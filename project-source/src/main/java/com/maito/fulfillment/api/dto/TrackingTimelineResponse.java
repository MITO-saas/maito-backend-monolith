package com.maito.fulfillment.api.dto;

import java.time.Instant;
import java.util.List;

public record TrackingTimelineResponse(
        String orderNumber,
        String shipmentNumber,
        CarrierType carrierType,
        String trackingNumber,
        ShipmentStatus currentStatus,
        String assignedRiderName,
        String assignedRiderPhone,
        String shippingLabelUrl,
        Instant dispatchedAt,
        Instant deliveredAt,
        List<CheckpointDto> checkpoints
) {}
