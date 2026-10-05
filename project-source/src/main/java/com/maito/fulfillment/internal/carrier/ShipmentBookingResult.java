package com.maito.fulfillment.internal.carrier;

public record ShipmentBookingResult(
        String trackingNumber,
        String shippingLabelUrl,
        String deliveryOtp,
        String initialCheckpointHub,
        String initialCheckpointDesc
) {}
