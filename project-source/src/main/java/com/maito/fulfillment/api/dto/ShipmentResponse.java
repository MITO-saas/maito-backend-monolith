package com.maito.fulfillment.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        String shipmentNumber,
        CarrierType carrierType,
        String trackingNumber,
        ShipmentStatus status,
        String assignedRiderName,
        String assignedRiderPhone,
        String deliveryOtp,
        int totalWeightGrams,
        int volumetricWeightGrams,
        String shippingLabelUrl,
        Instant dispatchedAt,
        Instant deliveredAt,
        Instant createdAt
) {}
