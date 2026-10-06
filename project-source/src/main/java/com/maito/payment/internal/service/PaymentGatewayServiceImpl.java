package com.maito.payment.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.order.api.dto.PaymentCallbackCommand;
import com.maito.order.api.service.OrderService;
import com.maito.payment.api.dto.PaymentInitResponse;
import com.maito.payment.api.dto.WebhookProcessResult;
import com.maito.payment.api.service.PaymentGatewayService;
import com.maito.payment.internal.domain.PaymentTransaction;
import com.maito.payment.internal.repository.PaymentTransactionRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayServiceImpl implements PaymentGatewayService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Value("${maito.payment.razorpay.key-id:rzp_test_mock_key_2026}")
    private String keyId;

    @Value("${maito.payment.razorpay.secret:secret_mock_test_2026}")
    private String secret;

    @Value("${maito.payment.razorpay.webhook-secret:rzp_webhook_secret_default_2026}")
    private String webhookSecret;

    @Override
    @Transactional
    public PaymentInitResponse initializePayment(UUID orderId, BigDecimal amount, String currency) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Order ID cannot be null");
        }
        String gatewayOrderId = "order_rzp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String currencyCode = (currency != null && !currency.isBlank()) ? currency : "INR";
        BigDecimal txAmount = amount != null ? amount : BigDecimal.ZERO;

        PaymentTransaction tx = PaymentTransaction.builder()
                .orderId(orderId)
                .gatewayProvider("RAZORPAY")
                .gatewayOrderId(gatewayOrderId)
                .amount(txAmount)
                .currencyCode(currencyCode)
                .status("INITIATED")
                .rawResponse("{}")
                .build();

        PaymentTransaction saved = paymentTransactionRepository.save(tx);
        log.info("Initialized payment transaction [{}] for order [{}] with gateway order [{}]", saved.getId(), orderId, gatewayOrderId);

        return new PaymentInitResponse(
                saved.getId(),
                orderId,
                "RAZORPAY",
                gatewayOrderId,
                txAmount,
                currencyCode,
                keyId
        );
    }

    @Override
    public boolean verifyRazorpaySignature(String orderId, String paymentId, String signature, String customSecret) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        String effectiveSecret = (customSecret != null && !customSecret.isBlank()) ? customSecret : this.secret;
        try {
            String payload = orderId + "|" + paymentId;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(effectiveSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hmacBytes = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = HexFormat.of().formatHex(hmacBytes);
            return MessageDigest.isEqual(calculatedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to verify HMAC-SHA256 signature: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional
    public WebhookProcessResult processWebhook(String provider, String payload, String signatureHeader) {
        log.info("Processing inbound webhook from provider: {}", provider);

        // 1. HMAC signature verification
        if (signatureHeader != null && !signatureHeader.isBlank()) {
            boolean valid = verifyWebhookPayloadHmac(payload, signatureHeader, webhookSecret);
            if (!valid) {
                log.warn("Invalid webhook signature received: {}", signatureHeader);
                throw new BusinessException(ErrorCode.INVALID_PAYMENT_SIGNATURE, "Invalid payment gateway webhook signature");
            }
        }

        // 2. Parse payload
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.has("event") ? root.get("event").asText() : "payment.captured";
            String gatewayOrderId = null;
            String gatewayPaymentId = null;
            String status = "SUCCESS";

            // Support standard Razorpay webhook structure
            if (root.has("payload") && root.get("payload").has("payment")) {
                JsonNode paymentEntity = root.get("payload").get("payment").get("entity");
                gatewayPaymentId = paymentEntity.has("id") ? paymentEntity.get("id").asText() : null;
                gatewayOrderId = paymentEntity.has("order_id") ? paymentEntity.get("order_id").asText() : null;
            } else if (root.has("order_id") || root.has("gatewayOrderId")) {
                gatewayOrderId = root.has("order_id") ? root.get("order_id").asText() : root.get("gatewayOrderId").asText();
                gatewayPaymentId = root.has("payment_id") ? root.get("payment_id").asText() : "pay_mock_" + System.currentTimeMillis();
            }

            if (gatewayOrderId != null) {
                PaymentTransaction tx = paymentTransactionRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
                if (tx != null) {
                    tx.setStatus(status);
                    tx.setGatewayPaymentId(gatewayPaymentId);
                    tx.setGatewaySignature(signatureHeader);
                    tx.setRawResponse(payload);
                    paymentTransactionRepository.save(tx);

                    // Confirm Order Settlement
                    try {
                        orderService.confirmPayment(tx.getOrderId(), new PaymentCallbackCommand(gatewayPaymentId, "PAID", signatureHeader));
                    } catch (Exception e) {
                        log.warn("Order already paid or payment callback notice: {}", e.getMessage());
                    }

                    return new WebhookProcessResult(true, event, String.valueOf(tx.getOrderId()), gatewayPaymentId, status);
                }
            }

            return new WebhookProcessResult(true, event, gatewayOrderId, gatewayPaymentId, "PROCESSED_WITHOUT_MATCHING_TX");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Failed to parse payment webhook payload: " + e.getMessage());
        }
    }

    private boolean verifyWebhookPayloadHmac(String payload, String signature, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hmacBytes = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculated = HexFormat.of().formatHex(hmacBytes);
            return MessageDigest.isEqual(calculated.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
