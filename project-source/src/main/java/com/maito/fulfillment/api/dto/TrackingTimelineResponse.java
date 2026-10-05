package com.maito.fulfillment.api.dto;

import java.time.Instant;
import java.util.List;

public record TrackingTimelineResponse(
        String orderNumber,
        String shipmentNumber,
        CarrierType carrierType,
        String carrierDisplayName,
        String trackingNumber,
        ShipmentStatus currentStatus,
        String currentProgressStep,
        List<String> progressStepperStages,
        String assignedRiderName,
        String assignedRiderPhone,
        String shippingLabelUrl,
        Instant dispatchedAt,
        Instant deliveredAt,
        List<CheckpointDto> checkpoints
) {
    public static final List<String> STANDARD_STEPPER_STAGES = List.of(
            "ORDER_PLACED",
            "PROCESSING",
            "DISPATCHED",
            "OUT_FOR_DELIVERY",
            "DELIVERED"
    );
}
