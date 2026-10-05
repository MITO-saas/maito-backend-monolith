package com.maito.fulfillment.internal.carrier;

import com.maito.fulfillment.api.dto.CarrierType;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ShiprocketCarrierAdapter implements CarrierAdapter {

    @Override
    public CarrierType getCarrierType() {
        return CarrierType.SHIPROCKET;
    }

    @Override
    public ShipmentBookingResult bookShipment(ShipmentBookingRequest request) {
        String awb = "SR-" + Math.abs(request.shipmentNumber().hashCode()) + "-AGG";
        String labelUrl = "https://shiprocket.co/tracking/labels/" + awb + ".pdf";
        return new ShipmentBookingResult(
                awb,
                labelUrl,
                null,
                "SHIPROCKET_SMART_HUB",
                "Aggregator routing completed. Best-rate carrier assigned."
        );
    }

    @Override
    public TrackingDetailsResult fetchTracking(String trackingNumber) {
        return new TrackingDetailsResult(
                trackingNumber,
                "IN_TRANSIT",
                "SHIPROCKET_SMART_HUB",
                "In transit via Shiprocket partner fleet",
                Instant.now()
        );
    }

    @Override
    public boolean cancelShipment(String trackingNumber) {
        return true;
    }
}
