package com.maito.payment.internal.controller;

import com.maito.payment.api.dto.PaymentProvider;
import com.maito.payment.api.dto.TenantPaymentConfigDto;
import com.maito.payment.api.dto.UpsertPaymentConfigCommand;
import com.maito.payment.internal.domain.TenantPaymentConfig;
import com.maito.payment.internal.repository.TenantPaymentConfigRepository;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/payments/configs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Payment Gateways", description = "Tenant-isolated Razorpay & Stripe credential management")
public class TenantPaymentConfigController {

    private final TenantPaymentConfigRepository paymentConfigRepository;

    @GetMapping
    @Operation(summary = "Get masked gateway credentials for current tenant")
    @PreAuthorize("hasAnyRole('ROLE_TENANT_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<TenantPaymentConfigDto>>> getPaymentConfigs() {
        List<TenantPaymentConfigDto> configs = paymentConfigRepository.findAll().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(configs));
    }

    @PutMapping
    @Transactional
    @Operation(summary = "Upsert tenant payment gateway credentials")
    @PreAuthorize("hasAnyRole('ROLE_TENANT_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TenantPaymentConfigDto>> upsertPaymentConfig(@Valid @RequestBody UpsertPaymentConfigCommand cmd) {
        TenantPaymentConfig config = paymentConfigRepository.findByProvider(cmd.provider())
                .orElseGet(() -> TenantPaymentConfig.builder()
                        .provider(cmd.provider())
                        .build());

        if (cmd.isEnabled() != null) config.setIsEnabled(cmd.isEnabled());
        if (cmd.isTestMode() != null) config.setIsTestMode(cmd.isTestMode());
        config.setKeyId(cmd.keyId());
        config.setSecretKey(cmd.secretKey());
        config.setWebhookSecret(cmd.webhookSecret());
        if (cmd.merchantAccountId() != null) config.setMerchantAccountId(cmd.merchantAccountId());

        TenantPaymentConfig saved = paymentConfigRepository.save(config);
        log.info("Persisted tenant payment configuration for provider [{}]", saved.getProvider());
        return ResponseEntity.ok(ApiResponse.ok(toDto(saved)));
    }

    private TenantPaymentConfigDto toDto(TenantPaymentConfig config) {
        return new TenantPaymentConfigDto(
                config.getId(),
                config.getProvider(),
                Boolean.TRUE.equals(config.getIsEnabled()),
                Boolean.TRUE.equals(config.getIsTestMode()),
                config.getKeyId(),
                mask(config.getSecretKey()),
                mask(config.getWebhookSecret()),
                config.getMerchantAccountId(),
                config.getUpdatedAt()
        );
    }

    private String mask(String secret) {
        if (secret == null || secret.length() <= 8) return "********";
        return secret.substring(0, 4) + "..." + secret.substring(secret.length() - 4);
    }
}
