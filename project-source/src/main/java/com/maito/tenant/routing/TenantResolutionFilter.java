package com.maito.tenant.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.shared.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * High-precedence servlet filter extracting tenant identity from HTTP headers.
 * Priority: 1) X-Tenant-ID header, 2) Inbound Host header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class TenantResolutionFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";

    private static final List<String> BYPASS_PATTERNS = List.of(
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**",
            "/actuator/**",
            "/api/v1/health/**",
            "/api/v1/health",
            "/api/v1/help/**",
            "/api/v1/help",
            "/api/v1/internal/platform/**",
            "/error"
    );

    private final TenantRoutingResolver routingResolver;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TenantResolutionFilter(
            @Autowired(required = false) TenantRoutingResolver routingResolver,
            ObjectMapper objectMapper) {
        this.routingResolver = routingResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (routingResolver == null) {
            return true;
        }
        String path = request.getRequestURI();
        for (String pattern : BYPASS_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Optional<TenantContext> tenantContextOpt = resolveTenant(request);

        if (tenantContextOpt.isEmpty()) {
            writeErrorResponse(response, HttpStatus.NOT_FOUND, "TENANT_RESOLUTION_FAILED",
                    "Invalid or unresolvable tenant. Verify X-Tenant-ID header or Host mapping.");
            return;
        }

        TenantContext tenantContext = tenantContextOpt.get();
        TenantContextHolder.set(tenantContext);
        response.setHeader(TENANT_HEADER, tenantContext.tenantId());

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private Optional<TenantContext> resolveTenant(HttpServletRequest request) {
        // 1. Priority: Explicit X-Tenant-ID header
        String tenantHeader = request.getHeader(TENANT_HEADER);
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return routingResolver.resolveByTenantId(tenantHeader.trim());
        }

        // 2. Priority: Inbound HTTP Host header
        String hostHeader = request.getHeader("Host");
        if (hostHeader != null && !hostHeader.isBlank()) {
            return routingResolver.resolveByDomain(hostHeader.trim());
        }

        return Optional.empty();
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            HttpStatus status,
            String errorCode,
            String message) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> errorEnvelope = ApiResponse.fail(errorCode, message);
        objectMapper.writeValue(response.getOutputStream(), errorEnvelope);
    }
}