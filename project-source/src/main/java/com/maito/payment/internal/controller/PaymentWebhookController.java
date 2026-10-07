package com.maito.payment.internal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.payment.api.dto.PaymentInitCommand;
import com.maito.payment.api.dto.PaymentInitResponse;
import com.maito.payment.api.dto.WebhookProcessResult;
import com.maito.payment.api.service.PaymentGatewayService;
import com.maito.shared.api.ApiResponse;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Gateway & Webhooks", description = "Razorpay & Stripe payment initialization, cryptographic verification, and idempotent webhook settlements")
public class PaymentWebhookController {

    private final PaymentGatewayService paymentGatewayService;
    private final ObjectMapper objectMapper;

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

    @PostMapping("/webhook/{tenantSlug}/razorpay")
    @Operation(summary = "Inbound Razorpay webhook settlement callback for specific tenant")
    public ResponseEntity<ApiResponse<WebhookProcessResult>> handleTenantRazorpayWebhook(
            @PathVariable String tenantSlug,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader,
            @RequestBody String payload
    ) {
        log.info("Inbound Razorpay webhook received for tenant [{}]", tenantSlug);
        return TenantContextHolder.withTenant(
                new TenantContext(tenantSlug, tenantSlug, "IN", "INR", "en_IN", ("mito_crunch".equalsIgnoreCase(tenantSlug.trim()) ? "db_mitocrunch" : ("vijiya_solar".equalsIgnoreCase(tenantSlug.trim()) ? "db_vijiyasolar" : "db_" + tenantSlug.trim().toLowerCase().replaceAll("[^a-z0-9_]", "")))),
                () -> {
                    WebhookProcessResult result = paymentGatewayService.processWebhook("RAZORPAY", payload, signatureHeader);
                    return ResponseEntity.ok(ApiResponse.ok(result));
                }
        );
    }

    @PostMapping("/webhook/{tenantSlug}/stripe")
    @Operation(summary = "Inbound Stripe webhook settlement callback for specific tenant")
    public ResponseEntity<ApiResponse<WebhookProcessResult>> handleTenantStripeWebhook(
            @PathVariable String tenantSlug,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader,
            @RequestBody String payload
    ) {
        log.info("Inbound Stripe webhook received for tenant [{}]", tenantSlug);
        return TenantContextHolder.withTenant(
                new TenantContext(tenantSlug, tenantSlug, "IN", "INR", "en_IN", ("mito_crunch".equalsIgnoreCase(tenantSlug.trim()) ? "db_mitocrunch" : ("vijiya_solar".equalsIgnoreCase(tenantSlug.trim()) ? "db_vijiyasolar" : "db_" + tenantSlug.trim().toLowerCase().replaceAll("[^a-z0-9_]", "")))),
                () -> {
                    WebhookProcessResult result = paymentGatewayService.processWebhook("STRIPE", payload, signatureHeader);
                    return ResponseEntity.ok(ApiResponse.ok(result));
                }
        );
    }

    @PostMapping("/webhook/razorpay")
    @Operation(summary = "Legacy inbound Razorpay webhook settlement callback")
    public ResponseEntity<ApiResponse<WebhookProcessResult>> handleRazorpayWebhook(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader,
            @RequestBody String payload
    ) {
        boolean boundHere = false;
        if (TenantContextHolder.getTenantId() == null) {
            String resolvedTenant = (tenantHeader != null && !tenantHeader.isBlank())
                    ? tenantHeader.trim()
                    : extractTenantFromPayload(payload);
            if (resolvedTenant != null) {
                TenantContextHolder.setTenantId(resolvedTenant);
                boundHere = true;
            }
        }
        try {
            WebhookProcessResult result = paymentGatewayService.processWebhook("RAZORPAY", payload, signatureHeader);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } finally {
            if (boundHere) {
                TenantContextHolder.clear();
            }
        }
    }

    @PostMapping("/webhook/stripe")
    @Operation(summary = "Legacy inbound Stripe webhook settlement callback")
    public ResponseEntity<ApiResponse<WebhookProcessResult>> handleStripeWebhook(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader,
            @RequestBody String payload
    ) {
        boolean boundHere = false;
        if (TenantContextHolder.getTenantId() == null) {
            String resolvedTenant = (tenantHeader != null && !tenantHeader.isBlank())
                    ? tenantHeader.trim()
                    : extractTenantFromPayload(payload);
            if (resolvedTenant != null) {
                TenantContextHolder.setTenantId(resolvedTenant);
                boundHere = true;
            }
        }
        try {
            WebhookProcessResult result = paymentGatewayService.processWebhook("STRIPE", payload, signatureHeader);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } finally {
            if (boundHere) {
                TenantContextHolder.clear();
            }
        }
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

    private String extractTenantFromPayload(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            // Check notes / metadata
            JsonNode notes = root.path("payload").path("payment").path("entity").path("notes");
            if (!notes.isMissingNode()) {
                if (notes.has("tenantId")) return notes.get("tenantId").asText();
                if (notes.has("tenant_id")) return notes.get("tenant_id").asText();
                if (notes.has("tenantSlug")) return notes.get("tenantSlug").asText();
            }
            JsonNode metadata = root.path("data").path("object").path("metadata");
            if (!metadata.isMissingNode()) {
                if (metadata.has("tenantId")) return metadata.get("tenantId").asText();
                if (metadata.has("tenant_id")) return metadata.get("tenant_id").asText();
                if (metadata.has("tenantSlug")) return metadata.get("tenantSlug").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
