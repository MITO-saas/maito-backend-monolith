package com.maito.observability.config;

import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enterprise Micrometer and OpenTelemetry distributed tracing configuration.
 * Automatically injects multi-tenant tags and application metadata into all metric observations.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservationFilter tenantObservationFilter() {
        return context -> {
            TenantContext ctx = TenantContextHolder.get();
            if (ctx != null && ctx.tenantId() != null && !ctx.tenantId().isBlank()) {
                context.addLowCardinalityKeyValue(KeyValue.of("tenant", ctx.tenantId()));
            } else {
                boolean alreadyHasTenant = false;
                for (KeyValue kv : context.getLowCardinalityKeyValues()) {
                    if ("tenant".equals(kv.getKey())) {
                        alreadyHasTenant = true;
                        break;
                    }
                }
                if (!alreadyHasTenant) {
                    context.addLowCardinalityKeyValue(KeyValue.of("tenant", "system"));
                }
            }
            return context;
        };
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer() {
        return registry -> registry.config().commonTags("service", "maito-backend-monolith");
    }
}
