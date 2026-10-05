package com.maito.fulfillment;

import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.internal.calculator.VolumetricWeightCalculator;
import com.maito.fulfillment.internal.carrier.BlueDartCarrierAdapter;
import com.maito.fulfillment.internal.carrier.CarrierAdapter;
import com.maito.fulfillment.internal.carrier.CarrierAdapterFactory;
import com.maito.fulfillment.internal.carrier.DelhiveryCarrierAdapter;
import com.maito.fulfillment.internal.carrier.SelfDeliveryCarrierAdapter;
import com.maito.fulfillment.internal.carrier.ShipmentBookingRequest;
import com.maito.fulfillment.internal.carrier.ShipmentBookingResult;
import com.maito.fulfillment.internal.carrier.ShiprocketCarrierAdapter;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
class CarrierAdapterFactoryIntegrationTest {

    @Autowired
    private CarrierAdapterFactory carrierAdapterFactory;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(new TenantContext(
                "mito_crunch",
                "mitocrunch",
                "IN",
                "INR",
                "en_IN",
                "db_mitocrunch"
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert CarrierAdapterFactory resolves all pluggable carrier implementations")
    void shouldResolveAllCarrierAdapters() {
        CarrierAdapter selfAdapter = carrierAdapterFactory.getAdapter(CarrierType.SELF_FLEET);
        assertThat(selfAdapter).isInstanceOf(SelfDeliveryCarrierAdapter.class);

        CarrierAdapter delhiveryAdapter = carrierAdapterFactory.getAdapter(CarrierType.DELHIVERY);
        assertThat(delhiveryAdapter).isInstanceOf(DelhiveryCarrierAdapter.class);

        CarrierAdapter blueDartAdapter = carrierAdapterFactory.getAdapter(CarrierType.BLUEDART);
        assertThat(blueDartAdapter).isInstanceOf(BlueDartCarrierAdapter.class);

        CarrierAdapter shiprocketAdapter = carrierAdapterFactory.getAdapter(CarrierType.SHIPROCKET);
        assertThat(shiprocketAdapter).isInstanceOf(ShiprocketCarrierAdapter.class);
    }

    @Test
    @DisplayName("Assert requesting unsupported or unconfigured carrier fails fast with CARRIER_NOT_SUPPORTED")
    void shouldFailFastWhenCarrierNotSupported() {
        assertThatThrownBy(() -> carrierAdapterFactory.getAdapter(null))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.CARRIER_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("Assert SelfDeliveryCarrierAdapter generates SELF-{tenant}-{random} AWB and 4-digit OTP")
    void shouldGenerateDeliveryVerificationOtpAndAwbForSelfFleet() {
        CarrierAdapter adapter = carrierAdapterFactory.getAdapter(CarrierType.SELF_FLEET);

        ShipmentBookingRequest req = new ShipmentBookingRequest(
                UUID.randomUUID(),
                "MC-2026-TEST",
                "SHP-TEST-001",
                "Rahul Sharma",
                "9876543210",
                Map.of("city", "Patna"),
                500,
                500,
                Map.of("defaultHub", "PATNA_CENTRAL"),
                Map.of(),
                "Rajesh Kumar",
                "+91 99887 76655"
        );

        ShipmentBookingResult result = adapter.bookShipment(req);
        assertThat(result.trackingNumber()).matches("SELF-MITOCRUNCH-\\d{6}");
        assertThat(result.deliveryOtp()).isNotNull();
        assertThat(result.deliveryOtp()).matches("\\d{4}");
        assertThat(result.shippingLabelUrl()).contains("SHP-TEST-001");
        assertThat(result.initialCheckpointHub()).isEqualTo("PATNA_CENTRAL");
    }

    @Test
    @DisplayName("Assert 3PL carriers generate respective DLV-... and BLD-... AWBs and hub checkpoints")
    void shouldGenerateAwbsForThirdPartyCarriers() {
        ShipmentBookingRequest req = new ShipmentBookingRequest(
                UUID.randomUUID(),
                "MC-2026-3PL",
                "SHP-TEST-3PL",
                "Amit Verma",
                "9876500000",
                Map.of("city", "Bengaluru"),
                600,
                600,
                Map.of(),
                Map.of(),
                null,
                null
        );

        // Delhivery -> DLV-...
        CarrierAdapter del = carrierAdapterFactory.getAdapter(CarrierType.DELHIVERY);
        ShipmentBookingResult delRes = del.bookShipment(req);
        assertThat(delRes.trackingNumber()).startsWith("DLV-");
        assertThat(delRes.shippingLabelUrl()).contains("track.delhivery.com");
        assertThat(delRes.initialCheckpointHub()).isEqualTo("DELHIVERY_PATNA_DC");

        // BlueDart -> BLD-...
        CarrierAdapter bd = carrierAdapterFactory.getAdapter(CarrierType.BLUEDART);
        ShipmentBookingResult bdRes = bd.bookShipment(req);
        assertThat(bdRes.trackingNumber()).startsWith("BLD-");
        assertThat(bdRes.shippingLabelUrl()).contains("bluedart.com");
        assertThat(bdRes.initialCheckpointHub()).isEqualTo("BLUEDART_AVIATION_GATEWAY");

        // Shiprocket -> SR-...
        CarrierAdapter sr = carrierAdapterFactory.getAdapter(CarrierType.SHIPROCKET);
        ShipmentBookingResult srRes = sr.bookShipment(req);
        assertThat(srRes.trackingNumber()).startsWith("SR-");
        assertThat(srRes.shippingLabelUrl()).contains("shiprocket.co");
    }

    @Test
    @DisplayName("Assert VolumetricWeightCalculator correctly calculates (L*W*H)/5000 and max(dead, vol)")
    void shouldCalculateVolumetricAndBillableWeight() {
        // Dimensions: 50cm x 40cm x 30cm => (50 * 40 * 30) / 5000 = 60,000 / 5000 = 12.0 kg = 12,000 grams
        double volKg = VolumetricWeightCalculator.calculateVolumetricWeightKg(50.0, 40.0, 30.0);
        assertThat(volKg).isEqualTo(12.0);

        int volGrams = VolumetricWeightCalculator.calculateVolumetricWeightGrams(50.0, 40.0, 30.0);
        assertThat(volGrams).isEqualTo(12000);

        // Case 1: Dead weight (500g) < Volumetric weight (12,000g) => Billable = 12,000g
        int billable1 = VolumetricWeightCalculator.calculateBillableWeightGrams(500, 50.0, 40.0, 30.0);
        assertThat(billable1).isEqualTo(12000);

        // Case 2: Dead weight (15,000g) > Volumetric weight (12,000g) => Billable = 15,000g
        int billable2 = VolumetricWeightCalculator.calculateBillableWeightGrams(15000, 50.0, 40.0, 30.0);
        assertThat(billable2).isEqualTo(15000);
    }
}
