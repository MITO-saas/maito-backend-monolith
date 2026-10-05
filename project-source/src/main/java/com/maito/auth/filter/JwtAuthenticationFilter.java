package com.maito.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.auth.security.UserPrincipal;
import com.maito.shared.api.ApiResponse;
import com.maito.tenant.routing.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            String tokenTenantId = tokenProvider.getTenantId(token);
            String activeTenantId = TenantContextHolder.getTenantId();

            // Multi-tenant token isolation check
            if (activeTenantId != null && tokenTenantId != null && !activeTenantId.equalsIgnoreCase(tokenTenantId)) {
                log.warn("Cross-tenant token replay detected! Token tenant: [{}], Active context: [{}]", tokenTenantId, activeTenantId);
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ApiResponse<Void> apiResponse = ApiResponse.fail("CROSS_TENANT_VIOLATION", "Token tenant [" + tokenTenantId + "] does not match active tenant [" + activeTenantId + "]");
                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                return;
            }

            UUID globalUserId = tokenProvider.getGlobalUserId(token);
            UUID profileId = tokenProvider.getProfileId(token);
            String role = tokenProvider.getRole(token);
            List<String> permissions = tokenProvider.getPermissions(token);

            UserPrincipal principal = new UserPrincipal(
                    globalUserId,
                    profileId,
                    null,
                    tokenTenantId,
                    role,
                    permissions
            );

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
