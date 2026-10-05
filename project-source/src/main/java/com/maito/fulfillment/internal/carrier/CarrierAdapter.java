package com.maito.fulfillment.internal.carrier;

import com.maito.fulfillment.api.dto.CarrierType;

public interface CarrierAdapter {
    CarrierType getCarrierType();
    ShipmentBookingResult bookShipment(ShipmentBookingRequest request);
    TrackingDetailsResult fetchTracking(String trackingNumber);
    boolean cancelShipment(String trackingNumber);
}
