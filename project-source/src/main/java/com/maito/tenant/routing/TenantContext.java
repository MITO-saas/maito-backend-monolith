package com.maito.tenant.routing;

import java.io.Serializable;

/**
 * Immutable thread-bound tenant routing context.
 */
public record TenantContext(
    String tenantId,
    String tenantSlug,
    String region,
    String currency,
    String locale,
    String databaseName
) implements Serializable {

    public static final String MASTER_TENANT_ID = "master";

    public static TenantContext master() {
        return new TenantContext(
            MASTER_TENANT_ID,
            "master",
            "IN",
            "INR",
            "en_IN",
            "maito_db"
        );
    }

    public boolean isMaster() {
        return MASTER_TENANT_ID.equalsIgnoreCase(tenantId);
    }
}