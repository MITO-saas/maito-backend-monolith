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
import com.maito.shared.idempotency.WebhookIdempotencyService;
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
    private final StripeSignatureVerifier stripeSignatureVerifier;
    private final WebhookIdempotencyService idempotencyService;

    @Value("${maito.payment.razorpay.key-id:rzp_test_mock_key_2026}")
    private String razorpayKeyId;

    @Value("${maito.payment.razorpay.secret:secret_mock_test_2026}")
    private String razorpaySecret;

    @Value("${maito.payment.razorpay.webhook-secret:rzp_webhook_secret_default_2026}")
    private String razorpayWebhookSecret;

    @Value("${maito.payment.stripe.publishable-key:pk_test_mock_key_2026}")
    private String stripePublishableKey;

    @Value("${maito.payment.stripe.secret-key:sk_test_mock_secret_2026}")
    private String stripeSecretKey;

    @Value("${maito.payment.stripe.webhook-secret:whsec_mock_test_stripe_2026}")
    private String stripeWebhookSecret;

    @Override
    @Transactional
    public PaymentInitResponse initializePayment(UUID orderId, BigDecimal amount, String currency) {
        return initializePayment(orderId, amount, currency, "RAZORPAY");
    }

    @Override
    @Transactional
    public PaymentInitResponse initializePayment(UUID orderId, BigDecimal amount, String currency, String gatewayProvider) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Order ID cannot be null");
        }

        String provider = (gatewayProvider != null && !gatewayProvider.isBlank())
                ? gatewayProvider.trim().toUpperCase() : "RAZORPAY";
        String currencyCode = (currency != null && !currency.isBlank()) ? currency : "INR";
        BigDecimal txAmount = amount != null ? amount : BigDecimal.ZERO;

        String gatewayOrderId;
        String clientKey;

        if ("STRIPE".equals(provider)) {
            gatewayOrderId = "pi_stripe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            clientKey = stripePublishableKey;
        } else {
            provider = "RAZORPAY";
            gatewayOrderId = "order_rzp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            clientKey = razorpayKeyId;
        }

        PaymentTransaction tx = PaymentTransaction.builder()
                .orderId(orderId)
                .gatewayProvider(provider)
                .gatewayOrderId(gatewayOrderId)
                .amount(txAmount)
                .currencyCode(currencyCode)
                .status("INITIATED")
                .rawResponse("{}")
                .build();

        PaymentTransaction saved = paymentTransactionRepository.save(tx);
        log.info("Initialized [{}] payment transaction [{}] for order [{}] with gateway order [{}]",
                provider, saved.getId(), orderId, gatewayOrderId);

        return new PaymentInitResponse(
                saved.getId(),
                orderId,
                provider,
                gatewayOrderId,
                txAmount,
                currencyCode,
                clientKey
        );
    }

    @Override
    public boolean verifyRazorpaySignature(String orderId, String paymentId, String signature, String customSecret) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        String effectiveSecret = (customSecret != null && !customSecret.isBlank()) ? customSecret : this.razorpaySecret;
        try {
            String payload = orderId + "|" + paymentId;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(effectiveSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hmacBytes = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = HexFormat.of().formatHex(hmacBytes);
            return MessageDigest.isEqual(calculatedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to verify Razorpay HMAC-SHA256 signature: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean verifyStripeSignature(String payload, String signatureHeader, String customSecret) {
        String effectiveSecret = (customSecret != null && !customSecret.isBlank()) ? customSecret : this.stripeWebhookSecret;
        return stripeSignatureVerifier.verifySignature(payload, signatureHeader, effectiveSecret);
    }

    @Override
    @Transactional
    public WebhookProcessResult processWebhook(String provider, String payload, String signatureHeader) {
        String normalizedProvider = (provider != null) ? provider.trim().toUpperCase() : "RAZORPAY";
        log.info("Processing inbound webhook from provider: [{}]", normalizedProvider);

        if ("STRIPE".equals(normalizedProvider)) {
            return processStripeWebhook(payload, signatureHeader);
        } else {
            return processRazorpayWebhook(payload, signatureHeader);
        }
    }

    private WebhookProcessResult processRazorpayWebhook(String payload, String signatureHeader) {
        // 1. HMAC signature verification
        if (signatureHeader != null && !signatureHeader.isBlank()) {
            boolean valid = verifyWebhookPayloadHmac(payload, signatureHeader, razorpayWebhookSecret);
            if (!valid) {
                log.warn("Invalid Razorpay webhook signature received: {}", signatureHeader);
                throw new BusinessException(ErrorCode.INVALID_PAYMENT_SIGNATURE, "Invalid payment gateway webhook signature");
            }
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.has("event") ? root.get("event").asText() : "payment.captured";
            String eventId = root.has("id") ? root.get("id").asText() : null;

            String gatewayOrderId = null;
            String gatewayPaymentId = null;

            // Extract payment entity details
            if (root.has("payload") && root.get("payload").has("payment")) {
                JsonNode paymentEntity = root.get("payload").get("payment").get("entity");
                gatewayPaymentId = paymentEntity.has("id") ? paymentEntity.get("id").asText() : null;
                gatewayOrderId = paymentEntity.has("order_id") ? paymentEntity.get("order_id").asText() : null;
            } else if (root.has("order_id") || root.has("gatewayOrderId")) {
                gatewayOrderId = root.has("order_id") ? root.get("order_id").asText() : root.get("gatewayOrderId").asText();
                gatewayPaymentId = root.has("payment_id") ? root.get("payment_id").asText() : "pay_mock_" + System.currentTimeMillis();
            }

            // Fallback unique eventId if not present
            if (eventId == null || eventId.isBlank()) {
                eventId = "rzp:" + event + ":" + (gatewayPaymentId != null ? gatewayPaymentId : gatewayOrderId);
            }

            // 2. Idempotency Check
            if (!idempotencyService.tryAcquire(eventId)) {
                log.info("Idempotent skip: Razorpay webhook event [{}] already processed", eventId);
                return new WebhookProcessResult(true, event, gatewayOrderId, gatewayPaymentId, "ALREADY_PROCESSED");
            }

            // 3. Process event transitions
            if (gatewayOrderId != null) {
                PaymentTransaction tx = paymentTransactionRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
                if (tx != null) {
                    tx.setGatewayPaymentId(gatewayPaymentId);
                    tx.setGatewaySignature(signatureHeader);
                    tx.setRawResponse(payload);

                    if ("payment.failed".equalsIgnoreCase(event)) {
                        tx.setStatus("FAILED");
                        paymentTransactionRepository.save(tx);
                        try {
                            orderService.confirmPayment(tx.getOrderId(), new PaymentCallbackCommand(gatewayPaymentId, "FAILED", signatureHeader));
                        } catch (Exception e) {
                            log.warn("Payment callback failure notice: {}", e.getMessage());
                        }
                        return new WebhookProcessResult(true, event, String.valueOf(tx.getOrderId()), gatewayPaymentId, "FAILED");
                    } else if ("refund.processed".equalsIgnoreCase(event)) {
                        tx.setStatus("REFUNDED");
                        paymentTransactionRepository.save(tx);
                        try {
                            orderService.updateOrderStatus(tx.getOrderId(), "REFUNDED");
                        } catch (Exception e) {
                            log.warn("Refund status update notice: {}", e.getMessage());
                        }
                        return new WebhookProcessResult(true, event, String.valueOf(tx.getOrderId()), gatewayPaymentId, "REFUNDED");
                    } else {
                        // Default to payment.captured / SUCCESS
                        tx.setStatus("SUCCESS");
                        paymentTransactionRepository.save(tx);
                        try {
                            orderService.confirmPayment(tx.getOrderId(), new PaymentCallbackCommand(gatewayPaymentId, "PAID", signatureHeader));
                        } catch (Exception e) {
                            log.warn("Order already paid or payment callback notice: {}", e.getMessage());
                        }
                        return new WebhookProcessResult(true, event, String.valueOf(tx.getOrderId()), gatewayPaymentId, "SUCCESS");
                    }
                }
            }

            return new WebhookProcessResult(true, event, gatewayOrderId, gatewayPaymentId, "PROCESSED_WITHOUT_MATCHING_TX");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Razorpay webhook processing error: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Failed to parse payment webhook payload: " + e.getMessage());
        }
    }

    private WebhookProcessResult processStripeWebhook(String payload, String signatureHeader) {
        // 1. Stripe cryptographic verification and replay defense
        if (signatureHeader != null && !signatureHeader.isBlank()) {
            boolean valid = stripeSignatureVerifier.verifySignature(payload, signatureHeader, stripeWebhookSecret);
            if (!valid) {
                log.warn("Invalid Stripe webhook signature received: {}", signatureHeader);
                throw new BusinessException(ErrorCode.INVALID_PAYMENT_SIGNATURE, "Invalid Stripe webhook signature");
            }
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.has("type") ? root.get("type").asText() : "payment_intent.succeeded";
            String eventId = root.has("id") ? root.get("id").asText() : "evt_stripe_" + UUID.randomUUID();

            String gatewayPaymentIntentId = null;
            String gatewayOrderId = null;

            JsonNode dataObject = root.path("data").path("object");
            if (!dataObject.isMissingNode()) {
                gatewayPaymentIntentId = dataObject.path("id").asText(null);
                if (dataObject.path("metadata").has("gatewayOrderId")) {
                    gatewayOrderId = dataObject.path("metadata").get("gatewayOrderId").asText();
                } else {
                    gatewayOrderId = gatewayPaymentIntentId;
                }
            }

            // 2. Idempotency Check
            if (!idempotencyService.tryAcquire(eventId)) {
                log.info("Idempotent skip: Stripe webhook event [{}] already processed", eventId);
                return new WebhookProcessResult(true, event, gatewayOrderId, gatewayPaymentIntentId, "ALREADY_PROCESSED");
            }

            // 3. Process Stripe Event Transitions
            if (gatewayOrderId != null) {
                PaymentTransaction tx = paymentTransactionRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
                if (tx == null && gatewayPaymentIntentId != null) {
                    tx = paymentTransactionRepository.findByGatewayOrderId(gatewayPaymentIntentId).orElse(null);
                }

                if (tx != null) {
                    tx.setGatewayPaymentId(gatewayPaymentIntentId);
                    tx.setGatewaySignature(signatureHeader);
                    tx.setRawResponse(payload);

                    if ("payment_intent.payment_failed".equalsIgnoreCase(event)) {
                        tx.setStatus("FAILED");
                        paymentTransactionRepository.save(tx);
                        try {
                            orderService.confirmPayment(tx.getOrderId(), new PaymentCallbackCommand(gatewayPaymentIntentId, "FAILED", signatureHeader));
                        } catch (Exception e) {
                            log.warn("Payment callback failure notice: {}", e.getMessage());
                        }
                        return new WebhookProcessResult(true, event, String.valueOf(tx.getOrderId()), gatewayPaymentIntentId, "FAILED");
                    } else if ("charge.refunded".equalsIgnoreCase(event)) {
                        tx.setStatus("REFUNDED");
                        paymentTransactionRepository.save(tx);
                        try {
                            orderService.updateOrderStatus(tx.getOrderId(), "REFUNDED");
                        } catch (Exception e) {
                            log.warn("Refund status update notice: {}", e.getMessage());
                        }
                        return new WebhookProcessResult(true, event, String.valueOf(tx.getOrderId()), gatewayPaymentIntentId, "REFUNDED");
                    } else {
                        // Default to payment_intent.succeeded
                        tx.setStatus("SUCCESS");
                        paymentTransactionRepository.save(tx);
                        try {
                            orderService.confirmPayment(tx.getOrderId(), new PaymentCallbackCommand(gatewayPaymentIntentId, "PAID", signatureHeader));
                        } catch (Exception e) {
                            log.warn("Order already paid or payment callback notice: {}", e.getMessage());
                        }
                        return new WebhookProcessResult(true, event, String.valueOf(tx.getOrderId()), gatewayPaymentIntentId, "SUCCESS");
                    }
                }
            }

            return new WebhookProcessResult(true, event, gatewayOrderId, gatewayPaymentIntentId, "PROCESSED_WITHOUT_MATCHING_TX");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Stripe webhook processing error: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Failed to parse Stripe webhook payload: " + e.getMessage());
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
