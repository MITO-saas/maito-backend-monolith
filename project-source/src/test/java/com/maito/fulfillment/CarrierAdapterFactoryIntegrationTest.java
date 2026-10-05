package com.maito.fulfillment;

import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.internal.carrier.BlueDartCarrierAdapter;
import com.maito.fulfillment.internal.carrier.CarrierAdapter;
import com.maito.fulfillment.internal.carrier.CarrierAdapterFactory;
import com.maito.fulfillment.internal.carrier.DelhiveryCarrierAdapter;
import com.maito.fulfillment.internal.carrier.SelfDeliveryCarrierAdapter;
import com.maito.fulfillment.internal.carrier.ShipmentBookingRequest;
import com.maito.fulfillment.internal.carrier.ShipmentBookingResult;
import com.maito.fulfillment.internal.carrier.ShiprocketCarrierAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class CarrierAdapterFactoryIntegrationTest {

    @Autowired
    private CarrierAdapterFactory carrierAdapterFactory;

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
    @DisplayName("Assert SelfDeliveryCarrierAdapter generates 6-digit delivery verification OTP")
    void shouldGenerateDeliveryVerificationOtpForSelfFleet() {
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
        assertThat(result.trackingNumber()).startsWith("SELF-");
        assertThat(result.deliveryOtp()).isNotNull();
        assertThat(result.deliveryOtp()).matches("\\d{6}");
        assertThat(result.shippingLabelUrl()).contains("SHP-TEST-001");
        assertThat(result.initialCheckpointHub()).isEqualTo("PATNA_CENTRAL");
    }

    @Test
    @DisplayName("Assert 3PL carriers generate respective AWB numbers and label URLs")
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

        // Delhivery
        CarrierAdapter del = carrierAdapterFactory.getAdapter(CarrierType.DELHIVERY);
        ShipmentBookingResult delRes = del.bookShipment(req);
        assertThat(delRes.trackingNumber()).startsWith("DEL-");
        assertThat(delRes.shippingLabelUrl()).contains("track.delhivery.com");

        // BlueDart
        CarrierAdapter bd = carrierAdapterFactory.getAdapter(CarrierType.BLUEDART);
        ShipmentBookingResult bdRes = bd.bookShipment(req);
        assertThat(bdRes.trackingNumber()).startsWith("BD-");
        assertThat(bdRes.shippingLabelUrl()).contains("bluedart.com");

        // Shiprocket
        CarrierAdapter sr = carrierAdapterFactory.getAdapter(CarrierType.SHIPROCKET);
        ShipmentBookingResult srRes = sr.bookShipment(req);
        assertThat(srRes.trackingNumber()).startsWith("SR-");
        assertThat(srRes.shippingLabelUrl()).contains("shiprocket.co");
    }
}
