package com.maito.payment.api.service;

import com.maito.payment.api.dto.PaymentInitResponse;
import com.maito.payment.api.dto.WebhookProcessResult;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGatewayService {
    PaymentInitResponse initializePayment(UUID orderId, BigDecimal amount, String currency);
    PaymentInitResponse initializePayment(UUID orderId, BigDecimal amount, String currency, String gatewayProvider);
    boolean verifyRazorpaySignature(String orderId, String paymentId, String signature, String customSecret);
    boolean verifyStripeSignature(String payload, String signatureHeader, String customSecret);
    WebhookProcessResult processWebhook(String provider, String payload, String signatureHeader);
}
