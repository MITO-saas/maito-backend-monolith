package com.maito.fulfillment.internal.carrier;

import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.tenant.routing.TenantContextHolder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;

@Component
public class SelfDeliveryCarrierAdapter implements CarrierAdapter {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public CarrierType getCarrierType() {
        return CarrierType.SELF_FLEET;
    }

    @Override
    public ShipmentBookingResult bookShipment(ShipmentBookingRequest request) {
        // Generate secure 4-digit numeric delivery verification OTP
        String otp = String.format("%04d", secureRandom.nextInt(10_000));
        String tenantSlug = TenantContextHolder.getTenantId() != null
                ? TenantContextHolder.getTenantId().replace("_", "").replace("-", "").toUpperCase()
                : "TENANT";
        String trackingNumber = "SELF-" + tenantSlug + "-" + (100000 + secureRandom.nextInt(900000));
        String labelUrl = "/api/v1/fulfillment/labels/self/" + request.shipmentNumber() + ".pdf";
        String hub = (request.carrierSettings() != null && request.carrierSettings().get("defaultHub") != null)
                ? String.valueOf(request.carrierSettings().get("defaultHub"))
                : "PATNA_CENTRAL_HUB";

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
        return new TrackingDetailsResult(
                trackingNumber,
                "IN_TRANSIT",
                "PATNA_CENTRAL_HUB",
                "Assigned to local delivery associate",
                Instant.now()
        );
    }

    @Override
    public boolean cancelShipment(String trackingNumber) {
        return true;
    }
}
