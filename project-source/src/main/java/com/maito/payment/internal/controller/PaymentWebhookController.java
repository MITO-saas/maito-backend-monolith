package com.maito.payment.internal.controller;

import com.maito.payment.api.dto.PaymentInitCommand;
import com.maito.payment.api.dto.PaymentInitResponse;
import com.maito.payment.api.dto.WebhookProcessResult;
import com.maito.payment.api.service.PaymentGatewayService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Gateway & Webhooks", description = "Razorpay & Stripe payment initialization, cryptographic verification, and idempotent webhook settlements")
public class PaymentWebhookController {

    private final PaymentGatewayService paymentGatewayService;

    @PostMapping("/initialize")
    @Operation(summary = "Initialize gateway payment transaction for order (Razorpay or Stripe)")
    public ResponseEntity<ApiResponse<PaymentInitResponse>> initializePayment(@Valid @RequestBody PaymentInitCommand cmd) {
        PaymentInitResponse response = paymentGatewayService.initializePayment(
                cmd.orderId(),
                cmd.amount(),
                cmd.currency(),
                cmd.gatewayProvider()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/webhook/razorpay")
    @Operation(summary = "Inbound Razorpay webhook settlement callback")
    public ResponseEntity<ApiResponse<WebhookProcessResult>> handleRazorpayWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader,
            @RequestBody String payload
    ) {
        WebhookProcessResult result = paymentGatewayService.processWebhook("RAZORPAY", payload, signatureHeader);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/webhook/stripe")
    @Operation(summary = "Inbound Stripe webhook settlement callback")
    public ResponseEntity<ApiResponse<WebhookProcessResult>> handleStripeWebhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader,
            @RequestBody String payload
    ) {
        WebhookProcessResult result = paymentGatewayService.processWebhook("STRIPE", payload, signatureHeader);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/verify-signature")
    @Operation(summary = "Direct HMAC-SHA256 signature verification utility")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifySignature(
            @RequestParam String orderId,
            @RequestParam String paymentId,
            @RequestParam String signature,
            @RequestParam(required = false) String secret
    ) {
        boolean valid = paymentGatewayService.verifyRazorpaySignature(orderId, paymentId, signature, secret);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("valid", valid, "orderId", orderId, "paymentId", paymentId)));
    }

    @PostMapping("/verify-stripe-signature")
    @Operation(summary = "Stripe signature validation utility")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyStripeSignature(
            @RequestParam String signature,
            @RequestBody String payload,
            @RequestParam(required = false) String secret
    ) {
        boolean valid = paymentGatewayService.verifyStripeSignature(payload, signature, secret);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("valid", valid)));
    }
}
