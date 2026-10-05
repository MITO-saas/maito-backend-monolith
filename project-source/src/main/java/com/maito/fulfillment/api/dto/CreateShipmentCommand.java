package com.maito.fulfillment.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateShipmentCommand(
        @NotNull(message = "Order ID is mandatory")
        UUID orderId,

        @NotNull(message = "Carrier type is mandatory")
        CarrierType carrierType,

        String assignedRiderName,
        String assignedRiderPhone,
        Integer totalWeightGrams,
        Integer volumetricWeightGrams,
        Double lengthCm,
        Double widthCm,
        Double heightCm
) {
    public CreateShipmentCommand(UUID orderId, CarrierType carrierType, String assignedRiderName, String assignedRiderPhone, Integer totalWeightGrams, Integer volumetricWeightGrams) {
        this(orderId, carrierType, assignedRiderName, assignedRiderPhone, totalWeightGrams, volumetricWeightGrams, null, null, null);
    }
}
