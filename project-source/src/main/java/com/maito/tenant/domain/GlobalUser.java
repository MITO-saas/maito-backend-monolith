package com.maito.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global single-identity entity for cross-tenant SSO and authentication routing.
 */
@Entity
@Table(name = "global_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "global_user_id", nullable = false)
    private UUID globalUserId;

    @Column(name = "phone_e164", length = 32, unique = true)
    private String phoneE164;

    @Column(name = "primary_email", length = 255, unique = true)
    private String primaryEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "credential_vault", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> credentialVault = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "security_metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> securityMetadata = new HashMap<>();

    @Column(name = "account_status", length = 32, nullable = false)
    @Builder.Default
    private String accountStatus = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}