package com.maito.tenant.datasource;

import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DynamicRoutingIntegrationTest {

    private DataSource masterDataSource;
    private DataSource tenantADataSource;
    private DataSource tenantBDataSource;
    private DynamicTenantRoutingDataSource routingDataSource;

    @BeforeEach
    void setUp() throws Exception {
        masterDataSource = mock(DataSource.class);
        tenantADataSource = mock(DataSource.class);
        tenantBDataSource = mock(DataSource.class);

        Connection masterConn = mock(Connection.class);
        Connection connA = mock(Connection.class);
        Connection connB = mock(Connection.class);

        when(masterDataSource.getConnection()).thenReturn(masterConn);
        when(tenantADataSource.getConnection()).thenReturn(connA);
        when(tenantBDataSource.getConnection()).thenReturn(connB);

        routingDataSource = new DynamicTenantRoutingDataSource(masterDataSource);
        routingDataSource.registerTenantDataSource("tenant_a", tenantADataSource);
        routingDataSource.registerTenantDataSource("tenant_b", tenantBDataSource);

        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Routes to master DataSource when no tenant context is bound")
    void shouldRouteToMasterWhenNoTenantContext() throws Exception {
        Connection conn = routingDataSource.getConnection();
        assertThat(conn).isNotNull();
        verify(masterDataSource, times(1)).getConnection();
        verify(tenantADataSource, never()).getConnection();
        verify(tenantBDataSource, never()).getConnection();
    }

    @Test
    @DisplayName("Routes to Tenant A DataSource when tenant_a context is bound")
    void shouldRouteToTenantA() throws Exception {
        TenantContext contextA = new TenantContext("tenant_a", "tenanta", "IN", "INR", "en_IN", "db_tenanta");
        TenantContextHolder.set(contextA);

        Connection conn = routingDataSource.getConnection();
        assertThat(conn).isNotNull();
        verify(tenantADataSource, times(1)).getConnection();
        verify(masterDataSource, never()).getConnection();
        verify(tenantBDataSource, never()).getConnection();
    }

    @Test
    @DisplayName("Routes to Tenant B DataSource when tenant_b context is bound")
    void shouldRouteToTenantB() throws Exception {
        TenantContext contextB = new TenantContext("tenant_b", "tenantb", "IN", "INR", "en_IN", "db_tenantb");
        TenantContextHolder.set(contextB);

        Connection conn = routingDataSource.getConnection();
        assertThat(conn).isNotNull();
        verify(tenantBDataSource, times(1)).getConnection();
        verify(tenantADataSource, never()).getConnection();
        verify(masterDataSource, never()).getConnection();
    }

    @Test
    @DisplayName("Dynamic pool registration succeeds at runtime without application restart")
    void shouldRegisterNewTenantPoolAtRuntime() throws Exception {
        DataSource tenantCDataSource = mock(DataSource.class);
        Connection connC = mock(Connection.class);
        when(tenantCDataSource.getConnection()).thenReturn(connC);

        assertThat(routingDataSource.hasTenantDataSource("tenant_c")).isFalse();
        routingDataSource.registerTenantDataSource("tenant_c", tenantCDataSource);
        assertThat(routingDataSource.hasTenantDataSource("tenant_c")).isTrue();

        TenantContextHolder.set(new TenantContext("tenant_c", "tenantc", "IN", "INR", "en_IN", "db_tenantc"));
        Connection conn = routingDataSource.getConnection();
        assertThat(conn).isNotNull();
        verify(tenantCDataSource, times(1)).getConnection();
    }

    @Test
    @DisplayName("Dynamic pool unregistration removes DataSource from routing registry")
    void shouldUnregisterTenantDataSource() {
        assertThat(routingDataSource.hasTenantDataSource("tenant_a")).isTrue();
        routingDataSource.unregisterTenantDataSource("tenant_a");
        assertThat(routingDataSource.hasTenantDataSource("tenant_a")).isFalse();
    }

    @Test
    @DisplayName("Lazily recovers tenant pool on application restart when pool is not in memory")
    void shouldLazilyRecoverPoolWhenNotInRegistry() throws Exception {
        DataSource lazyDataSource = mock(DataSource.class);
        Connection lazyConn = mock(Connection.class);
        when(lazyDataSource.getConnection()).thenReturn(lazyConn);

        routingDataSource.setLazyPoolProvider(tenantId -> {
            if ("recovered_tenant".equals(tenantId)) {
                return lazyDataSource;
            }
            return null;
        });

        TenantContextHolder.set(new TenantContext("recovered_tenant", "recovered", "IN", "INR", "en_IN", "db_recovered"));
        Connection conn = routingDataSource.getConnection();
        assertThat(conn).isNotNull();
        verify(lazyDataSource, times(1)).getConnection();
        verify(masterDataSource, never()).getConnection();
    }
}