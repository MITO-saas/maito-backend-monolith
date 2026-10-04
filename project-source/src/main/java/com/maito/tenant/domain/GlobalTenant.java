package com.maito.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Master Control Plane entity representing a provisioned enterprise tenant.
 * Backed by PostgreSQL 16 JSONB columns for dynamic 20-year adaptability without schema drift.
 */
@Entity
@Table(name = "global_tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalTenant {

    @Id
    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "tenant_slug", length = 64, nullable = false, unique = true)
    private String tenantSlug;

    @Column(name = "legal_entity_name", length = 255, nullable = false)
    private String legalEntityName;

    @Column(name = "account_state", length = 32, nullable = false)
    @Builder.Default
    private String accountState = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "routing_config", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> routingConfig = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regional_profile", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> regionalProfile = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tier_entitlements", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> tierEntitlements = new HashMap<>();

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