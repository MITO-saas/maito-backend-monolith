package com.maito.fulfillment.internal.carrier;

import com.maito.fulfillment.api.dto.CarrierType;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DelhiveryCarrierAdapter implements CarrierAdapter {

    @Override
    public CarrierType getCarrierType() {
        return CarrierType.DELHIVERY;
    }

    @Override
    public ShipmentBookingResult bookShipment(ShipmentBookingRequest request) {
        String awb = "DEL-" + Math.abs(request.shipmentNumber().hashCode()) + "-IN";
        String labelUrl = "https://track.delhivery.com/labels/" + awb + ".pdf";
        return new ShipmentBookingResult(
                awb,
                labelUrl,
                null,
                "DELHIVERY_PATNA_DC",
                "Manifest generated. Delhivery AWB allocated."
        );
    }

    @Override
    public TrackingDetailsResult fetchTracking(String trackingNumber) {
        return new TrackingDetailsResult(
                trackingNumber,
                "IN_TRANSIT",
                "DELHIVERY_PATNA_DC",
                "Package scanned at origin distribution center",
                Instant.now()
        );
    }

    @Override
    public boolean cancelShipment(String trackingNumber) {
        return true;
    }
}
