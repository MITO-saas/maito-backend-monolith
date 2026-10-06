package com.maito.gateway.internal.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.shared.api.ApiResponse;
import com.maito.shared.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-precedence rate limiting filter acting as an in-memory DDoS and brute-force shield.
 * Uses a thread-safe Token Bucket algorithm partitioned by Client IP and Route Group.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    private static final List<String> BYPASS_PATTERNS = List.of(
            "/actuator/**",
            "/assets/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**",
            "/favicon.ico",
            "/error"
    );

    @Value("${maito.gateway.rate-limiting.enabled:true}")
    private boolean enabled;

    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public enum RouteGroup {
        AUTH(10, 5),
        CHECKOUT_ORDERS(15, 10),
        GENERAL(120, 60);

        private final long capacity;
        private final long refillPerMin;

        RouteGroup(long capacity, long refillPerMin) {
            this.capacity = capacity;
            this.refillPerMin = refillPerMin;
        }

        public long getCapacity() {
            return capacity;
        }

        public long getRefillPerMin() {
            return refillPerMin;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
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

        String clientIp = extractClientIp(request);
        String path = request.getRequestURI();
        RouteGroup routeGroup = resolveRouteGroup(path);

        String bucketKey = clientIp + ":" + routeGroup.name();
        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new TokenBucket(routeGroup.getCapacity(), routeGroup.getRefillPerMin()));

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for IP [{}] on route group [{}] path [{}]", clientIp, routeGroup.name(), path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader(HEADER_LIMIT, String.valueOf(bucket.getCapacity()));
            response.setHeader(HEADER_REMAINING, "0");
            response.setHeader(HEADER_RETRY_AFTER, "60");

            ApiResponse<Void> errorResponse = ApiResponse.fail(
                    ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    ErrorCode.RATE_LIMIT_EXCEEDED.getMessage()
            );
            objectMapper.writeValue(response.getOutputStream(), errorResponse);
            return;
        }

        response.setHeader(HEADER_LIMIT, String.valueOf(bucket.getCapacity()));
        response.setHeader(HEADER_REMAINING, String.valueOf(bucket.getRemainingTokens()));

        filterChain.doFilter(request, response);
    }

    public RouteGroup resolveRouteGroup(String path) {
        if (pathMatcher.match("/api/v1/auth/**", path)) {
            return RouteGroup.AUTH;
        }
        if (pathMatcher.match("/api/v1/checkout/**", path) ||
                pathMatcher.match("/api/v1/b2b/bulk-orders", path) ||
                pathMatcher.match("/api/v1/b2b/bulk-orders/**", path)) {
            return RouteGroup.CHECKOUT_ORDERS;
        }
        return RouteGroup.GENERAL;
    }

    public String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            int commaIdx = xForwardedFor.indexOf(',');
            return (commaIdx != -1 ? xForwardedFor.substring(0, commaIdx) : xForwardedFor).trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void clearBuckets() {
        buckets.clear();
    }

    public static class TokenBucket {
        private final double capacity;
        private final double refillTokensPerSecond;
        private double availableTokens;
        private long lastRefillNanos;

        public TokenBucket(double capacity, double refillTokensPerMinute) {
            this.capacity = capacity;
            this.refillTokensPerSecond = refillTokensPerMinute / 60.0;
            this.availableTokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (availableTokens >= 1.0) {
                availableTokens -= 1.0;
                return true;
            }
            return false;
        }

        public synchronized long getRemainingTokens() {
            refill();
            return (long) Math.max(0, Math.floor(availableTokens));
        }

        public long getCapacity() {
            return (long) capacity;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                availableTokens = Math.min(capacity, availableTokens + (elapsedSeconds * refillTokensPerSecond));
                lastRefillNanos = now;
            }
        }
    }
}