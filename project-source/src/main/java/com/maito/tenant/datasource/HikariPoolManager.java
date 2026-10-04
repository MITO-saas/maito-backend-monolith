package com.maito.tenant.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise manager for dynamically provisioning and cycling tenant HikariCP pools.
 */
@Component
@Slf4j
public class HikariPoolManager {

    private final ConcurrentHashMap<String, HikariDataSource> poolRegistry = new ConcurrentHashMap<>();
    private final DynamicTenantRoutingDataSource routingDataSource;

    public HikariPoolManager(DynamicTenantRoutingDataSource routingDataSource) {
        this.routingDataSource = routingDataSource;
    }

    public synchronized HikariDataSource getOrCreateTenantPool(
            String tenantId,
            String jdbcUrl,
            String username,
            String password) {

        if (poolRegistry.containsKey(tenantId)) {
            HikariDataSource existing = poolRegistry.get(tenantId);
            if (!existing.isClosed()) {
                return existing;
            }
        }

        log.info("Creating dedicated Hikari connection pool for tenant: [{}], URL: [{}]", tenantId, jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setPoolName("HikariPool-Tenant-" + tenantId);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");

        // Strict enterprise production bounds mandated for multi-tenancy
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(20);
        config.setIdleTimeout(60000); // 60 seconds
        config.setConnectionTimeout(10000); // 10 seconds
        config.setMaxLifetime(1800000); // 30 minutes
        config.setAutoCommit(true);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        HikariDataSource tenantDataSource = new HikariDataSource(config);

        poolRegistry.put(tenantId, tenantDataSource);
        routingDataSource.registerTenantDataSource(tenantId, tenantDataSource);

        return tenantDataSource;
    }

    public boolean hasPool(String tenantId) {
        return poolRegistry.containsKey(tenantId) && !poolRegistry.get(tenantId).isClosed();
    }

    public Map<String, HikariDataSource> getActivePools() {
        return Collections.unmodifiableMap(poolRegistry);
    }

    @PreDestroy
    public void shutdownAllPools() {
        log.info("Gracefully shutting down [{}] tenant connection pools...", poolRegistry.size());
        poolRegistry.forEach((tenantId, ds) -> {
            try {
                if (!ds.isClosed()) {
                    ds.close();
                    log.info("Closed Hikari pool for tenant: [{}]", tenantId);
                }
            } catch (Exception e) {
                log.error("Error shutting down pool for tenant [{}]: {}", tenantId, e.getMessage());
            }
        });
        poolRegistry.clear();
    }
}