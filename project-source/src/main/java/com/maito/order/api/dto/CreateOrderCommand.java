package com.maito.order.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

public record CreateOrderCommand(
    @NotNull Map<String, Object> shippingAddress,
    String couponCode,
    BigDecimal coinsToRedeem,
    String warehouseCode
) {
    public CreateOrderCommand(Map<String, Object> shippingAddress, String couponCode) {
        this(shippingAddress, couponCode, BigDecimal.ZERO, null);
    }

    public CreateOrderCommand(Map<String, Object> shippingAddress, String couponCode, BigDecimal coinsToRedeem) {
        this(shippingAddress, couponCode, coinsToRedeem, null);
    }
}
