package com.maito.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maito.gateway.internal.filter.RateLimitingFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    private RateLimitingFilter rateLimitingFilter;
    private ObjectMapper objectMapper;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        rateLimitingFilter = new RateLimitingFilter(objectMapper);
        rateLimitingFilter.setEnabled(true);
        rateLimitingFilter.clearBuckets();
    }

    @Test
    @DisplayName("Allows requests within burst limit and sets rate limit headers")
    void shouldAllowRequestWithinRateLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(response.getHeader(RateLimitingFilter.HEADER_LIMIT)).isEqualTo("10");
        assertThat(response.getHeader(RateLimitingFilter.HEADER_REMAINING)).isEqualTo("9");
    }

    @Test
    @DisplayName("Rejects requests exceeding burst limit with HTTP 429 and Retry-After header")
    void shouldRejectWhenBurstLimitExceeded() throws Exception {
        String clientIp = "10.0.0.2";

        // Consume all 10 tokens for AUTH route
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr(clientIp);
            MockHttpServletResponse res = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(req, res, filterChain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        verify(filterChain, times(10)).doFilter(any(), any());

        // 11th request must be rejected with HTTP 429
        MockHttpServletRequest exceededReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        exceededReq.setRemoteAddr(clientIp);
        MockHttpServletResponse exceededRes = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(exceededReq, exceededRes, filterChain);

        // filterChain was NOT called for 11th request
        verify(filterChain, times(10)).doFilter(any(), any());
        assertThat(exceededRes.getStatus()).isEqualTo(429);
        assertThat(exceededRes.getHeader(RateLimitingFilter.HEADER_LIMIT)).isEqualTo("10");
        assertThat(exceededRes.getHeader(RateLimitingFilter.HEADER_REMAINING)).isEqualTo("0");
        assertThat(exceededRes.getHeader(RateLimitingFilter.HEADER_RETRY_AFTER)).isEqualTo("60");

        String content = exceededRes.getContentAsString();
        assertThat(content).contains("GATEWAY_4290");
        assertThat(content).contains("Too many requests");
    }

    @Test
    @DisplayName("Isolates rate limit buckets across different client IPs")
    void shouldIsolateBucketsByClientIp() throws Exception {
        // Exhaust IP 1
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("192.168.1.1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(req, res, filterChain);
        }

        // IP 2 should still have full quota
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req2.setRemoteAddr("192.168.1.2");
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(req2, res2, filterChain);

        assertThat(res2.getStatus()).isEqualTo(200);
        assertThat(res2.getHeader(RateLimitingFilter.HEADER_REMAINING)).isEqualTo("9");
    }

    @Test
    @DisplayName("Isolates rate limit quotas across different route groups for same IP")
    void shouldIsolateBucketsByRouteGroup() throws Exception {
        String clientIp = "10.0.0.3";

        // Exhaust AUTH route (10 tokens)
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr(clientIp);
            MockHttpServletResponse res = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(req, res, filterChain);
        }

        // CHECKOUT route for same IP should have its own separate 15-token quota
        MockHttpServletRequest checkoutReq = new MockHttpServletRequest("POST", "/api/v1/checkout/create-order");
        checkoutReq.setRemoteAddr(clientIp);
        MockHttpServletResponse checkoutRes = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(checkoutReq, checkoutRes, filterChain);

        assertThat(checkoutRes.getStatus()).isEqualTo(200);
        assertThat(checkoutRes.getHeader(RateLimitingFilter.HEADER_LIMIT)).isEqualTo("15");
        assertThat(checkoutRes.getHeader(RateLimitingFilter.HEADER_REMAINING)).isEqualTo("14");
    }

    @Test
    @DisplayName("Extracts real client IP from X-Forwarded-For header")
    void shouldExtractClientIpFromHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");

        String ip = rateLimitingFilter.extractClientIp(req);
        assertThat(ip).isEqualTo("203.0.113.195");
    }
}