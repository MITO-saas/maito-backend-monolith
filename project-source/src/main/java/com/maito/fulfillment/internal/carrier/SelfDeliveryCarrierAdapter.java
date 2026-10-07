package com.maito.fulfillment.internal.carrier;

import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.store.api.service.StoreService;
import com.maito.tenant.routing.TenantContextHolder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;

@Component
public class SelfDeliveryCarrierAdapter implements CarrierAdapter {

    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectProvider<StoreService> storeServiceProvider;

    public SelfDeliveryCarrierAdapter(ObjectProvider<StoreService> storeServiceProvider) {
        this.storeServiceProvider = storeServiceProvider;
    }

    @Override
    public CarrierType getCarrierType() {
        return CarrierType.SELF_FLEET;
    }

    @Override
    public ShipmentBookingResult bookShipment(ShipmentBookingRequest request) {
        String otp = String.format("%04d", secureRandom.nextInt(10_000));
        String tenantSlug = TenantContextHolder.getTenantId() != null
                ? TenantContextHolder.getTenantId().replace("_", "").replace("-", "").toUpperCase()
                : "TENANT";
        String trackingNumber = "SELF-" + tenantSlug + "-" + (100000 + secureRandom.nextInt(900000));
        String labelUrl = "/api/v1/fulfillment/labels/self/" + request.shipmentNumber() + ".pdf";

        // Dynamic hub resolution:
        // 1. Read pickupLocation from request or carrierSettings
        // 2. Fall back to tenant StoreSettings defaultWarehouseCode
        // 3. Default to "HUB-" + tenantId.toUpperCase()
        String hub = null;
        if (request.pickupLocation() != null && !request.pickupLocation().isBlank()) {
            hub = request.pickupLocation().trim();
        } else if (request.carrierSettings() != null && request.carrierSettings().get("pickupLocation") != null) {
            hub = request.carrierSettings().get("pickupLocation").toString().trim();
        } else if (request.carrierSettings() != null && request.carrierSettings().get("defaultHub") != null) {
            hub = request.carrierSettings().get("defaultHub").toString().trim();
        }

        if (hub == null || hub.isBlank()) {
            try {
                if (storeServiceProvider != null && storeServiceProvider.getIfAvailable() != null) {
                    var settings = storeServiceProvider.getIfAvailable().getStoreSettings();
                    if (settings != null && settings.commercialSettings() != null && settings.commercialSettings().get("defaultWarehouseCode") != null) {
                        hub = settings.commercialSettings().get("defaultWarehouseCode").toString().trim();
                    }
                }
            } catch (Exception ignored) {}
        }

        if (hub == null || hub.isBlank()) {
            String tenantId = TenantContextHolder.getTenantId();
            hub = (tenantId != null && !tenantId.isBlank()) ? "HUB-" + tenantId.toUpperCase() : "PATNA_CENTRAL_HUB";
        }

        return new ShipmentBookingResult(
                trackingNumber,
                labelUrl,
                otp,
                hub,
                "Shipment allocated to local fleet. Verification OTP generated."
        );
    }

    @Override
    public TrackingDetailsResult fetchTracking(String trackingNumber) {
        String tenantId = TenantContextHolder.getTenantId();
        String hub = (tenantId != null && !tenantId.isBlank()) ? "HUB-" + tenantId.toUpperCase() : "PATNA_CENTRAL_HUB";
        return new TrackingDetailsResult(
                trackingNumber,
                "IN_TRANSIT",
                hub,
                "Assigned to local delivery associate",
                Instant.now()
        );
    }

    @Override
    public boolean cancelShipment(String trackingNumber) {
        return true;
    }
}
