package com.maito.cart.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
    UUID id,
    String guestCartId,
    UUID customerProfileId,
    String currencyCode,
    String appliedCouponCode,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal shippingAmount,
    BigDecimal totalAmount,
    List<CartItemDto> items
) {}
