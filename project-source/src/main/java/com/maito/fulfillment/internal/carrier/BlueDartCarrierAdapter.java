package com.maito.fulfillment.internal.carrier;

import com.maito.fulfillment.api.dto.CarrierType;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BlueDartCarrierAdapter implements CarrierAdapter {

    @Override
    public CarrierType getCarrierType() {
        return CarrierType.BLUEDART;
    }

    @Override
    public ShipmentBookingResult bookShipment(ShipmentBookingRequest request) {
        String waybill = "BD-" + Math.abs(request.shipmentNumber().hashCode()) + "99";
        String labelUrl = "https://api.bluedart.com/docs/waybill/" + waybill + ".pdf";
        return new ShipmentBookingResult(
                waybill,
                labelUrl,
                null,
                "BLUEDART_APEX_HUB",
                "Electronic Air Waybill generated. Scheduled for pickup."
        );
    }

    @Override
    public TrackingDetailsResult fetchTracking(String trackingNumber) {
        return new TrackingDetailsResult(
                trackingNumber,
                "IN_TRANSIT",
                "BLUEDART_APEX_HUB",
                "Shipment processed at air express gateway",
                Instant.now()
        );
    }

    @Override
    public boolean cancelShipment(String trackingNumber) {
        return true;
    }
}
