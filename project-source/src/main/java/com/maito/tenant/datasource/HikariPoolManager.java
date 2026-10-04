package com.maito.tenant.datasource;

import com.maito.tenant.api.dto.TenantPoolMetrics;
import com.maito.tenant.domain.GlobalTenant;
import com.maito.tenant.repository.GlobalTenantRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise manager for dynamically provisioning, monitoring, and cycling tenant HikariCP pools.
 * Supports self-healing lazy pool recovery on application restart with zero active pools in memory.
 */
@Component
@Slf4j
public class HikariPoolManager {

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/maito_db}")
    private String masterUrl;

    @Value("${spring.datasource.username:maito_user}")
    private String masterUsername;

    @Value("${spring.datasource.password:maito_pass}")
    private String masterPassword;

    private final ConcurrentHashMap<String, HikariDataSource> poolRegistry = new ConcurrentHashMap<>();
    private final DynamicTenantRoutingDataSource routingDataSource;
    private final GlobalTenantRepository tenantRepository;

    public HikariPoolManager(
            DynamicTenantRoutingDataSource routingDataSource,
            @Autowired(required = false) GlobalTenantRepository tenantRepository) {
        this.routingDataSource = routingDataSource;
        this.tenantRepository = tenantRepository;
    }

    @PostConstruct
    public void registerLazyPoolProvider() {
        if (routingDataSource != null) {
            routingDataSource.setLazyPoolProvider(this::ensureTenantPool);
            log.info("Registered HikariPoolManager lazy pool recovery provider on DynamicTenantRoutingDataSource.");
        }
    }

    public synchronized DataSource ensureTenantPool(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || "master".equalsIgnoreCase(tenantId)) {
            return null;
        }

        if (hasPool(tenantId)) {
            return poolRegistry.get(tenantId);
        }

        if (tenantRepository == null) {
            log.debug("GlobalTenantRepository not available; lazy pool recovery skipped for [{}]", tenantId);
            return null;
        }

        try {
            Optional<GlobalTenant> tenantOpt = tenantRepository.findById(tenantId)
                    .or(() -> tenantRepository.findByTenantSlug(tenantId));

            if (tenantOpt.isPresent()) {
                GlobalTenant tenant = tenantOpt.get();
                if (!"ACTIVE".equalsIgnoreCase(tenant.getAccountState())) {
                    log.warn("Cannot lazily recover pool for non-active tenant: [{}] (state: {})", tenantId, tenant.getAccountState());
                    return null;
                }

                Map<String, Object> routing = tenant.getRoutingConfig();
                String dbName = (routing != null && routing.get("db_name") != null)
                        ? String.valueOf(routing.get("db_name"))
                        : "db_" + tenant.getTenantSlug();

                String jdbcUrl = (routing != null && routing.get("jdbc_url") != null)
                        ? String.valueOf(routing.get("jdbc_url"))
                        : buildTenantJdbcUrl(dbName);

                log.info("LAZY POOL RECOVERY: Rebuilding isolated HikariCP pool for tenant [{}] (db: {}) from master metadata.",
                        tenantId, dbName);

                return getOrCreateTenantPool(tenant.getTenantId(), jdbcUrl, masterUsername, masterPassword);
            }
        } catch (Exception e) {
            log.error("Failed during lazy pool recovery for tenant [{}]: {}", tenantId, e.getMessage());
        }

        return null;
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

    public synchronized boolean closeAndEvictPool(String tenantId) {
        if (tenantId == null) {
            return false;
        }
        HikariDataSource pool = poolRegistry.remove(tenantId);
        routingDataSource.unregisterTenantDataSource(tenantId);
        if (pool != null) {
            try {
                if (!pool.isClosed()) {
                    pool.close();
                    log.info("Closed and evicted HikariCP connection pool for tenant: [{}]", tenantId);
                }
                return true;
            } catch (Exception e) {
                log.error("Error closing connection pool for tenant [{}]: {}", tenantId, e.getMessage());
            }
        }
        return false;
    }

    public List<TenantPoolMetrics> getPoolTelemetry() {
        List<TenantPoolMetrics> telemetryList = new ArrayList<>();
        poolRegistry.forEach((tenantId, pool) -> {
            if (pool != null && !pool.isClosed()) {
                HikariPoolMXBean mxBean = pool.getHikariPoolMXBean();
                int active = mxBean != null ? mxBean.getActiveConnections() : 0;
                int idle = mxBean != null ? mxBean.getIdleConnections() : 0;
                int total = mxBean != null ? mxBean.getTotalConnections() : 0;
                int awaiting = mxBean != null ? mxBean.getThreadsAwaitingConnection() : 0;
                telemetryList.add(new TenantPoolMetrics(tenantId, active, idle, total, awaiting, "ACTIVE"));
            } else {
                telemetryList.add(new TenantPoolMetrics(tenantId, 0, 0, 0, 0, "CLOSED"));
            }
        });
        return Collections.unmodifiableList(telemetryList);
    }

    public boolean hasPool(String tenantId) {
        return poolRegistry.containsKey(tenantId) && !poolRegistry.get(tenantId).isClosed();
    }

    public Map<String, HikariDataSource> getActivePools() {
        return Collections.unmodifiableMap(poolRegistry);
    }

    private String buildTenantJdbcUrl(String targetDbName) {
        String base = masterUrl;
        int lastSlash = base.lastIndexOf('/');
        int queryIndex = base.indexOf('?');
        String hostPart = (lastSlash != -1) ? base.substring(0, lastSlash + 1) : "jdbc:postgresql://localhost:5432/";
        String queryPart = (queryIndex != -1) ? base.substring(queryIndex) : "";
        return hostPart + targetDbName + queryPart;
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