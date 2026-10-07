package com.maito.payment.internal.domain;

import com.maito.payment.api.dto.PaymentProvider;
import com.maito.shared.domain.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tenant_payment_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPaymentConfig extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 32, nullable = false, unique = true)
    private PaymentProvider provider;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "is_test_mode", nullable = false)
    @Builder.Default
    private Boolean isTestMode = true;

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(name = "secret_key", length = 512, nullable = false)
    private String secretKey;

    @Column(name = "webhook_secret", length = 512, nullable = false)
    private String webhookSecret;

    @Column(name = "merchant_account_id")
    private String merchantAccountId;
}
