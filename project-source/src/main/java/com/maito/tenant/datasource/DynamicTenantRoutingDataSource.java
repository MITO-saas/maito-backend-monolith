package com.maito.tenant.datasource;

import com.maito.tenant.routing.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Spring AbstractRoutingDataSource dynamically switching database connection pools per tenant.
 * Supports lazy pool recovery on application restart with zero active pools in memory.
 */
@Slf4j
public class DynamicTenantRoutingDataSource extends AbstractRoutingDataSource {

    private final Map<Object, Object> targetDataSourcesMap = new ConcurrentHashMap<>();
    private final DataSource defaultMasterDataSource;
    private Function<String, DataSource> lazyPoolProvider;

    public DynamicTenantRoutingDataSource(DataSource defaultMasterDataSource) {
        this.defaultMasterDataSource = defaultMasterDataSource;
        setDefaultTargetDataSource(defaultMasterDataSource);
        targetDataSourcesMap.put("master", defaultMasterDataSource);
        setTargetDataSources(targetDataSourcesMap);
        afterPropertiesSet();
    }

    public void setLazyPoolProvider(Function<String, DataSource> lazyPoolProvider) {
        this.lazyPoolProvider = lazyPoolProvider;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.isBlank() || "master".equalsIgnoreCase(tenantId)) {
            log.trace("Routing to default master control plane datasource.");
            return "master";
        }
        log.trace("Routing to tenant connection pool: [{}]", tenantId);
        return tenantId;
    }

    @Override
    protected DataSource determineTargetDataSource() {
        Object lookupKey = determineCurrentLookupKey();
        if (lookupKey == null || "master".equals(lookupKey)) {
            return defaultMasterDataSource;
        }

        String tenantId = (String) lookupKey;
        if (!hasTenantDataSource(tenantId) && lazyPoolProvider != null) {
            log.info("Tenant pool not in memory for [{}]. Triggering lazy pool recovery...", tenantId);
            DataSource recoveredDs = lazyPoolProvider.apply(tenantId);
            if (recoveredDs != null) {
                return recoveredDs;
            }
        }

        return super.determineTargetDataSource();
    }

    public synchronized void registerTenantDataSource(String tenantId, DataSource dataSource) {
        if (tenantId == null || dataSource == null) {
            return;
        }
        targetDataSourcesMap.put(tenantId, dataSource);
        setTargetDataSources(new HashMap<>(targetDataSourcesMap));
        afterPropertiesSet();
        log.info("Successfully registered dynamic DataSource for tenant [{}] in routing registry.", tenantId);
    }

    public synchronized void unregisterTenantDataSource(String tenantId) {
        if (tenantId == null) {
            return;
        }
        targetDataSourcesMap.remove(tenantId);
        setTargetDataSources(new HashMap<>(targetDataSourcesMap));
        afterPropertiesSet();
        log.info("Successfully unregistered DataSource for tenant [{}] from routing registry.", tenantId);
    }

    public boolean hasTenantDataSource(String tenantId) {
        return tenantId != null && targetDataSourcesMap.containsKey(tenantId);
    }

    public Map<Object, Object> getRegisteredDataSources() {
        return Collections.unmodifiableMap(targetDataSourcesMap);
    }
}