package com.maito.order.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CreateOrderCommand(
    @NotNull Map<String, Object> shippingAddress,
    String couponCode
) {}
