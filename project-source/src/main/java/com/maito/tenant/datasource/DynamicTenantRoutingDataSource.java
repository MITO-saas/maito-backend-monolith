package com.maito.tenant.datasource;

import com.maito.tenant.routing.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring AbstractRoutingDataSource dynamically switching database connection pools per tenant.
 */
@Slf4j
public class DynamicTenantRoutingDataSource extends AbstractRoutingDataSource {

    private final Map<Object, Object> targetDataSourcesMap = new ConcurrentHashMap<>();

    public DynamicTenantRoutingDataSource(DataSource defaultMasterDataSource) {
        setDefaultTargetDataSource(defaultMasterDataSource);
        targetDataSourcesMap.put("master", defaultMasterDataSource);
        setTargetDataSources(targetDataSourcesMap);
        afterPropertiesSet();
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