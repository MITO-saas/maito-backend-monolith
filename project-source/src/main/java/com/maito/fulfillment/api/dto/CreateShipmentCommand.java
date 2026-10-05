package com.maito.fulfillment.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateShipmentCommand(
        @NotNull UUID orderId,
        @NotNull CarrierType carrierType,
        String assignedRiderName,
        String assignedRiderPhone,
        Integer totalWeightGrams,
        Integer volumetricWeightGrams
) {}
