package com.maito.fulfillment.api.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateShipmentStatusCommand(
        @NotNull ShipmentStatus newStatus,
        String locationHub,
        String statusDescription,
        String deliveryOtp
) {}
