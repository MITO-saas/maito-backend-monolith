package com.maito.tenant.telemetry;

import com.maito.tenant.api.dto.TenantPoolMetrics;
import com.maito.tenant.datasource.HikariPoolManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Boot Actuator endpoint exposed at /actuator/tenants.
 * Exposes live connection pool metrics for observability platforms (Prometheus, Datadog).
 */
@Component
@Endpoint(id = "tenants")
public class TenantPoolActuatorEndpoint {

    private final HikariPoolManager poolManager;

    public TenantPoolActuatorEndpoint(HikariPoolManager poolManager) {
        this.poolManager = poolManager;
    }

    @ReadOperation
    public List<TenantPoolMetrics> tenantPoolMetrics() {
        return poolManager.getPoolTelemetry();
    }
}