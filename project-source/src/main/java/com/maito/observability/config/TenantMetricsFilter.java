package com.maito.observability.config;

import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ServerHttpObservationFilter;

import java.io.IOException;

/**
 * High-precedence WebFilter attaching dynamically resolved tenant_id to the
 * Micrometer Observation/Timer context for every incoming HTTP request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30) // Executes inside TenantResolutionFilter (HIGHEST_PRECEDENCE + 20)
@Slf4j
public class TenantMetricsFilter extends OncePerRequestFilter {

    private final ObservationRegistry observationRegistry;

    public TenantMetricsFilter(@Autowired(required = false) ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Executed before TenantResolutionFilter cleans up TenantContextHolder
            tagObservationWithTenant(request);
        }
    }

    private void tagObservationWithTenant(HttpServletRequest request) {
        try {
            String tenant = resolveTenantTag(request);
            ServerHttpObservationFilter.findObservationContext(request)
                    .ifPresent(ctx -> ctx.addLowCardinalityKeyValue(KeyValue.of("tenant", tenant)));

            if (observationRegistry != null && !observationRegistry.isNoop()) {
                Observation current = observationRegistry.getCurrentObservation();
                if (current != null) {
                    current.lowCardinalityKeyValue(KeyValue.of("tenant", tenant));
                }
            }
        } catch (Throwable t) {
            log.trace("Suppressed error tagging observation with tenant: {}", t.getMessage());
        }
    }

    public static String resolveTenantTag(HttpServletRequest request) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx != null && ctx.tenantId() != null && !ctx.tenantId().isBlank()) {
            return ctx.tenantId();
        }
        if (request != null) {
            String header = request.getHeader("X-Tenant-ID");
            if (header != null && !header.isBlank()) {
                if ("mitocrunch".equalsIgnoreCase(header.trim())) return "mito_crunch";
                return header.trim().toLowerCase();
            }
        }
        return "system";
    }
}
