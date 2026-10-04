package com.maito.tenant.provisioning;

import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.api.dto.ProvisionTenantRequest;
import com.maito.tenant.api.dto.TenantDetailsResponse;
import com.maito.tenant.api.dto.TenantProvisioningResult;
import com.maito.tenant.datasource.HikariPoolManager;
import com.maito.tenant.domain.GlobalTenant;
import com.maito.tenant.domain.GlobalTenantDomain;
import com.maito.tenant.repository.GlobalTenantDomainRepository;
import com.maito.tenant.repository.GlobalTenantRepository;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantRoutingResolver;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Automated zero-deploy tenant provisioning engine.
 * Programmatically provisions physical isolated PostgreSQL databases, applies tenant Liquibase
 * migrations, configures dedicated HikariCP pools, registers dynamic routing entries in < 10 seconds,
 * and handles transactional compensating rollbacks on failure.
 */
@Service
@Slf4j
public class TenantProvisioningService {

    private static final String TENANT_CHANGELOG = "db/changelog/tenant/tenant-base-schema.xml";

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/maito_db}")
    private String masterUrl;

    @Value("${spring.datasource.username:maito_user}")
    private String masterUsername;

    @Value("${spring.datasource.password:maito_pass}")
    private String masterPassword;

    @Value("${platform.security.isolated-db-users:false}")
    private boolean isolatedDbUsers;

    private final DataSource masterDataSource;
    private final GlobalTenantRepository tenantRepository;
    private final GlobalTenantDomainRepository domainRepository;
    private final HikariPoolManager poolManager;
    private final TenantRoutingResolver routingResolver;

    public TenantProvisioningService(
            DataSource masterDataSource,
            GlobalTenantRepository tenantRepository,
            GlobalTenantDomainRepository domainRepository,
            HikariPoolManager poolManager,
            TenantRoutingResolver routingResolver) {
        this.masterDataSource = masterDataSource;
        this.tenantRepository = tenantRepository;
        this.domainRepository = domainRepository;
        this.poolManager = poolManager;
        this.routingResolver = routingResolver;
    }

    @Transactional
    public TenantProvisioningResult provisionTenant(ProvisionTenantRequest request) {
        log.info("Starting automated zero-deploy provisioning for tenant: [{}]", request.tenantId());

        String tenantId = request.tenantId().trim().toLowerCase();
        String tenantSlug = request.tenantSlug().trim().toLowerCase();
        String primaryDomain = cleanDomain(request.primaryDomain());

        // 1. Uniqueness Validations (returns HTTP 409 Conflict via IDEMPOTENCY_CONFLICT)
        if (tenantRepository.existsByTenantId(tenantId)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "Tenant ID already exists: " + tenantId);
        }
        if (tenantRepository.existsByTenantSlug(tenantSlug)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "Tenant Slug already exists: " + tenantSlug);
        }
        if (domainRepository.existsByDomainName(primaryDomain)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "Domain already registered: " + primaryDomain);
        }

        String targetDbName = "db_" + tenantSlug.replaceAll("[^a-z0-9_]", "");
        boolean dbCreated = false;

        try {
            // 2. Physical Database Provisioning (returns true only if actually created)
            dbCreated = createPhysicalDatabaseIfNotExists(targetDbName);

            // Optional restricted DB user creation
            createRestrictedUserIfEnabled(targetDbName, tenantSlug);

            // 3. Automated Liquibase Schema Execution on Target DB
            String tenantJdbcUrl = buildTenantJdbcUrl(targetDbName);
            executeTenantLiquibase(tenantJdbcUrl);

            // 4. Register Tenant Metadata in Master Control Plane
            Map<String, Object> routingConfig = buildRoutingConfig(targetDbName, tenantJdbcUrl, request.initialConfig());
            Map<String, Object> regionalProfile = buildRegionalProfile(request);
            Map<String, Object> tierEntitlements = buildTierEntitlements(request);

            GlobalTenant tenant = GlobalTenant.builder()
                    .tenantId(tenantId)
                    .tenantSlug(tenantSlug)
                    .legalEntityName(request.legalName().trim())
                    .accountState("ACTIVE")
                    .routingConfig(routingConfig)
                    .regionalProfile(regionalProfile)
                    .tierEntitlements(tierEntitlements)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(0L)
                    .build();
            tenantRepository.save(tenant);

            GlobalTenantDomain domain = GlobalTenantDomain.builder()
                    .tenantId(tenantId)
                    .domainName(primaryDomain)
                    .isPrimary(true)
                    .sslStatus("ACTIVE")
                    .verificationToken("verified-auto-" + System.currentTimeMillis())
                    .createdAt(Instant.now())
                    .build();
            domainRepository.save(domain);

            // 5. Initialize Dedicated Hikari Pool in Routing DataSource
            poolManager.getOrCreateTenantPool(tenantId, tenantJdbcUrl, masterUsername, masterPassword);

            // 6. Pre-warm Redis routing cache
            TenantContext tenantContext = new TenantContext(
                    tenantId,
                    tenantSlug,
                    String.valueOf(regionalProfile.get("country")),
                    String.valueOf(regionalProfile.get("currency")),
                    String.valueOf(regionalProfile.get("locale")),
                    targetDbName
            );
            routingResolver.cacheTenantContext(tenantContext, primaryDomain);

            log.info("Tenant [{}] successfully provisioned with isolated database [{}] and pool registered.",
                    tenantId, targetDbName);

            return new TenantProvisioningResult(
                    tenantId,
                    tenantSlug,
                    primaryDomain,
                    targetDbName,
                    "ACTIVE",
                    Instant.now(),
                    "Tenant successfully provisioned with isolated database and live HikariCP pool."
            );
        } catch (BusinessException be) {
            if (ErrorCode.IDEMPOTENCY_CONFLICT.equals(be.getErrorCode()) || ErrorCode.BUSINESS_RULE_VIOLATION.equals(be.getErrorCode())) {
                throw be;
            }
            performCompensatingRollback(targetDbName, tenantId, primaryDomain, dbCreated);
            throw be;
        } catch (Exception ex) {
            log.error("Fatal error during tenant provisioning pipeline for [{}]. Initiating rollback...", tenantId, ex);
            performCompensatingRollback(targetDbName, tenantId, primaryDomain, dbCreated);
            throw new BusinessException(ErrorCode.PROVISIONING_FAILED,
                    "Tenant provisioning pipeline execution failed: " + ex.getMessage());
        }
    }

    @Transactional
    public void decommissionTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Invalid tenant identifier");
        }

        String searchId = tenantId.trim().toLowerCase();
        GlobalTenant tenant = tenantRepository.findById(tenantId.trim())
                .or(() -> tenantRepository.findByTenantSlug(searchId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant not found: " + tenantId));

        tenant.setAccountState("DECOMMISSIONED");
        tenant.setUpdatedAt(Instant.now());
        tenantRepository.save(tenant);

        // 1. Evict and close active HikariCP connection pool
        poolManager.closeAndEvictPool(tenant.getTenantId());

        // 2. Purge Redis routing cache for both ID, slug, and domains
        routingResolver.evictCache(tenant.getTenantId(), null);
        routingResolver.evictCache(tenant.getTenantSlug(), null);
        List<GlobalTenantDomain> domains = domainRepository.findByTenantId(tenant.getTenantId());
        for (GlobalTenantDomain d : domains) {
            routingResolver.evictCache(null, d.getDomainName());
        }

        log.warn("AUDIT LOG: Tenant [{}] (slug: {}) transitioned to DECOMMISSIONED. Active pool evicted and routing cache invalidated.",
                tenant.getTenantId(), tenant.getTenantSlug());
    }

    @Transactional(readOnly = true)
    public TenantDetailsResponse getTenantDetails(String tenantId) {
        GlobalTenant tenant = tenantRepository.findById(tenantId)
                .or(() -> tenantRepository.findByTenantSlug(tenantId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant not found: " + tenantId));

        List<GlobalTenantDomain> domains = domainRepository.findByTenantId(tenant.getTenantId());
        String primaryDomain = domains.stream()
                .filter(GlobalTenantDomain::getIsPrimary)
                .map(GlobalTenantDomain::getDomainName)
                .findFirst()
                .orElse(domains.isEmpty() ? "none" : domains.get(0).getDomainName());

        return new TenantDetailsResponse(
                tenant.getTenantId(),
                tenant.getTenantSlug(),
                tenant.getLegalEntityName(),
                tenant.getAccountState(),
                primaryDomain,
                tenant.getRoutingConfig(),
                tenant.getRegionalProfile(),
                tenant.getTierEntitlements(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }

    private void performCompensatingRollback(String dbName, String tenantId, String primaryDomain, boolean dbCreated) {
        log.warn("COMPENSATING ROLLBACK: Cleaning up provisioning resources for tenant [{}] db [{}]", tenantId, dbName);
        try {
            poolManager.closeAndEvictPool(tenantId);
        } catch (Exception e) {
            log.warn("Rollback pool eviction error for [{}]: {}", tenantId, e.getMessage());
        }

        try {
            routingResolver.evictCache(tenantId, primaryDomain);
        } catch (Exception e) {
            log.warn("Rollback cache eviction error for [{}]: {}", tenantId, e.getMessage());
        }

        if (dbCreated) {
            dropPhysicalDatabase(dbName);
        }
    }

    private void dropPhysicalDatabase(String dbName) {
        log.warn("COMPENSATING ACTION: Dropping physical database: [{}]", dbName);
        try (Connection conn = masterDataSource.getConnection()) {
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + dbName + "' AND pid <> pg_backend_pid()");
                stmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName);
                log.info("Successfully dropped database [{}] during compensating rollback.", dbName);
            }
        } catch (Exception e) {
            log.error("Failed to drop database [{}] during compensating rollback: {}", dbName, e.getMessage());
        }
    }

    private boolean createPhysicalDatabaseIfNotExists(String dbName) {
        log.info("Verifying physical database existence: [{}]", dbName);
        try (Connection conn = masterDataSource.getConnection()) {
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT 1 FROM pg_database WHERE datname = ?")) {
                checkStmt.setString(1, dbName);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        exists = true;
                    }
                }
            }

            if (!exists) {
                log.info("Executing native SQL to create physical database: [{}]", dbName);
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("CREATE DATABASE " + dbName + " OWNER " + masterUsername);
                }
                return true;
            } else {
                log.info("Database [{}] already exists; skipping creation.", dbName);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to verify/create database [{}]: {}", dbName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to provision physical database: " + e.getMessage());
        }
    }

    private void createRestrictedUserIfEnabled(String dbName, String tenantSlug) {
        if (!isolatedDbUsers) {
            log.debug("Restricted DB user creation skipped (isolated-db-users=false)");
            return;
        }
        String restrictedUser = "usr_" + tenantSlug.replaceAll("[^a-z0-9_]", "");
        String restrictedPass = "pwd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("Creating isolated restricted database user [{}] for [{}]", restrictedUser, dbName);
        try (Connection conn = masterDataSource.getConnection()) {
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DO $$\n" +
                        "BEGIN\n" +
                        "    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '" + restrictedUser + "') THEN\n" +
                        "        CREATE ROLE " + restrictedUser + " WITH LOGIN PASSWORD '" + restrictedPass + "' NOSUPERUSER NOCREATEDB NOCREATEROLE;\n" +
                        "    END IF;\n" +
                        "END\n" +
                        "$$;");
                stmt.execute("GRANT CONNECT ON DATABASE " + dbName + " TO " + restrictedUser);
                log.info("Successfully granted CONNECT on [{}] to [{}] with zero DDL privileges", dbName, restrictedUser);
            }
        } catch (Exception e) {
            log.error("Failed to configure isolated DB user [{}]: {}", restrictedUser, e.getMessage(), e);
            throw new BusinessException(ErrorCode.PROVISIONING_FAILED, "Failed to create restricted database user: " + e.getMessage());
        }
    }

    private void executeTenantLiquibase(String tenantJdbcUrl) {
        log.info("Applying tenant base Liquibase migration [{}] to [{}]", TENANT_CHANGELOG, tenantJdbcUrl);
        try (Connection tenantConn = DriverManager.getConnection(tenantJdbcUrl, masterUsername, masterPassword)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(tenantConn));

            try (Liquibase liquibase = new Liquibase(
                    TENANT_CHANGELOG,
                    new ClassLoaderResourceAccessor(),
                    database)) {
                liquibase.update(new Contexts());
                log.info("Successfully completed Liquibase migration on tenant database.");
            }
        } catch (Exception e) {
            log.error("Failed to run Liquibase migrations on [{}]: {}", tenantJdbcUrl, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to execute tenant database migrations: " + e.getMessage());
        }
    }

    private String buildTenantJdbcUrl(String targetDbName) {
        String base = masterUrl;
        int lastSlash = base.lastIndexOf('/');
        int queryIndex = base.indexOf('?');
        String hostPart = (lastSlash != -1) ? base.substring(0, lastSlash + 1) : "jdbc:postgresql://localhost:5432/";
        String queryPart = (queryIndex != -1) ? base.substring(queryIndex) : "";
        return hostPart + targetDbName + queryPart;
    }

    private Map<String, Object> buildRoutingConfig(String dbName, String jdbcUrl, Map<String, Object> overrides) {
        Map<String, Object> map = new HashMap<>();
        map.put("db_name", dbName);
        map.put("jdbc_url", jdbcUrl);
        map.put("max_pool_size", 20);
        map.put("min_idle", 2);
        map.put("idle_timeout_ms", 60000);
        map.put("connection_timeout_ms", 10000);
        if (overrides != null) {
            map.putAll(overrides);
        }
        return map;
    }

    private Map<String, Object> buildRegionalProfile(ProvisionTenantRequest request) {
        Map<String, Object> map = new HashMap<>();
        String country = (request.countryCode() != null && !request.countryCode().isBlank()) 
                ? request.countryCode().toUpperCase() : "IN";
        String currency = (request.currencyCode() != null && !request.currencyCode().isBlank()) 
                ? request.currencyCode().toUpperCase() : "INR";
        map.put("country", country);
        map.put("currency", currency);
        map.put("locale", country.equalsIgnoreCase("US") ? "en_US" : country.equalsIgnoreCase("AE") ? "ar_AE" : "en_IN");
        map.put("timezone", country.equalsIgnoreCase("US") ? "America/New_York" : country.equalsIgnoreCase("AE") ? "Asia/Dubai" : "Asia/Kolkata");
        return map;
    }

    private Map<String, Object> buildTierEntitlements(ProvisionTenantRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("tier", "ENTERPRISE");
        map.put("features", List.of("ISOLATED_DB", "CUSTOM_DOMAIN", "AUDIT_LEDGER", "REALTIME_STOCK"));
        map.put("max_admin_users", 50);
        return map;
    }

    private String cleanDomain(String domain) {
        String cleaned = domain.trim().toLowerCase();
        if (cleaned.contains(":")) {
            cleaned = cleaned.substring(0, cleaned.indexOf(":"));
        }
        return cleaned;
    }
}