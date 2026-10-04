package com.maito.tenant.routing;

import java.util.Optional;

/**
 * Result of tenant resolution lifecycle at the filter and routing layer.
 * Distinguishes between ACTIVE, SUSPENDED, and NOT_FOUND states.
 */
public record TenantResolutionResult(
    Status status,
    TenantContext context,
    String tenantId,
    String message
) {
    public enum Status {
        ACTIVE,
        SUSPENDED,
        NOT_FOUND
    }

    public static TenantResolutionResult active(TenantContext context) {
        return new TenantResolutionResult(Status.ACTIVE, context, context.tenantId(), null);
    }

    public static TenantResolutionResult suspended(String tenantId, String message) {
        return new TenantResolutionResult(Status.SUSPENDED, null, tenantId, message);
    }

    public static TenantResolutionResult notFound(String identifier) {
        return new TenantResolutionResult(Status.NOT_FOUND, null, identifier, "Tenant not found");
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isSuspended() {
        return status == Status.SUSPENDED;
    }

    public boolean isNotFound() {
        return status == Status.NOT_FOUND;
    }

    public Optional<TenantContext> toOptional() {
        return Optional.ofNullable(context);
    }
}