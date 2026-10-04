package com.maito.tenant.routing;

import java.io.Serializable;

/**
 * Cache representation of resolved tenant metadata stored in Redis.
 */
public record CachedTenantRouting(
    String tenantId,
    String tenantSlug,
    String region,
    String currency,
    String locale,
    String databaseName,
    String accountState
) implements Serializable {}