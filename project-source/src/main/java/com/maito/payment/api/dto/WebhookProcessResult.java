package com.maito.payment.api.dto;

public record WebhookProcessResult(
    boolean processed,
    String event,
    String orderId,
    String paymentId,
    String status
) {}
