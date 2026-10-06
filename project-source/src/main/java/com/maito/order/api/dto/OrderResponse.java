package com.maito.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    String orderNumber,
    UUID customerProfileId,
    String orderStatus,
    String currencyCode,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal taxAmount,
    BigDecimal shippingAmount,
    BigDecimal totalAmount,
    String couponCode,
    Map<String, Object> shippingAddressSnapshot,
    String paymentReference,
    String paymentStatus,
    Instant createdAt,
    List<OrderItemDto> items,
    BigDecimal coinsRedeemed
) {
    public OrderResponse(
        UUID id, String orderNumber, UUID customerProfileId, String orderStatus,
        String currencyCode, BigDecimal subtotalAmount, BigDecimal discountAmount,
        BigDecimal taxAmount, BigDecimal shippingAmount, BigDecimal totalAmount,
        String couponCode, Map<String, Object> shippingAddressSnapshot,
        String paymentReference, String paymentStatus, Instant createdAt,
        List<OrderItemDto> items
    ) {
        this(id, orderNumber, customerProfileId, orderStatus, currencyCode,
             subtotalAmount, discountAmount, taxAmount, shippingAmount, totalAmount,
             couponCode, shippingAddressSnapshot, paymentReference, paymentStatus,
             createdAt, items, BigDecimal.ZERO);
    }
}
