package com.maito.fulfillment.api.service;

import com.maito.fulfillment.api.dto.CarrierConfigResponse;
import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.api.dto.ConfigureCarrierCommand;
import com.maito.fulfillment.api.dto.CreateShipmentCommand;
import com.maito.fulfillment.api.dto.ShipmentResponse;
import com.maito.fulfillment.api.dto.TrackingTimelineResponse;
import com.maito.fulfillment.api.dto.UpdateShipmentStatusCommand;

import java.util.List;
import java.util.UUID;

public interface FulfillmentService {
    ShipmentResponse createShipment(CreateShipmentCommand cmd);
    ShipmentResponse dispatchShipment(UUID shipmentId);
    TrackingTimelineResponse getTrackingByOrderNumber(String orderNumber);
    TrackingTimelineResponse getTrackingByTrackingNumber(String trackingNumber);
    ShipmentResponse getShipmentById(UUID shipmentId);
    ShipmentResponse updateShipmentStatus(UUID shipmentId, UpdateShipmentStatusCommand cmd);
    ShipmentResponse cancelShipment(UUID shipmentId);
    CarrierConfigResponse configureCarrier(CarrierType carrierType, ConfigureCarrierCommand cmd);
    List<CarrierConfigResponse> getCarrierConfigurations();
}
