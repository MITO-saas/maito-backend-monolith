package com.maito.observability;

import com.maito.gateway.internal.filter.RateLimitingFilter;
import com.maito.observability.service.BusinessMetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private BusinessMetricsService businessMetricsService;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Test
    @DisplayName("Actuator /actuator/prometheus endpoint produces standard OpenMetrics/Prometheus format")
    void shouldProduceOpenMetricsFormat() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .isNotEmpty()
                .contains("# HELP")
                .contains("# TYPE")
                .contains("jvm_memory_used_bytes");
    }

    @Test
    @DisplayName("API requests increment http_server_requests_seconds with resolved tenant tag")
    void shouldIncrementHttpServerRequestsWithTenantTag() throws Exception {
        // Issue request with tenant header
        mockMvc.perform(get("/api/v1/store/settings")
                        .header("X-Tenant-ID", "mitocrunch"))
                .andExpect(status().isOk());

        // Verify Micrometer registry has recorded observation with tenant tag
        Search search = meterRegistry.find("http.server.requests").tag("tenant", "mito_crunch");
        assertThat(search.timer()).isNotNull();
        assertThat(search.timer().count()).isGreaterThanOrEqualTo(1);

        // Also verify Prometheus scrape text output reflects tenant metric
        MvcResult prometheusResult = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String body = prometheusResult.getResponse().getContentAsString();
        assertThat(body).contains("tenant=\"mito_crunch\"");
    }

    @Test
    @DisplayName("Exceeding rate limit increments maito_rate_limit_rejections_total metric")
    void shouldIncrementRateLimitRejectionsMetric() throws Exception {
        rateLimitingFilter.setEnabled(true);
        rateLimitingFilter.clearBuckets();

        String clientIp = "192.0.2.99";

        // RouteGroup.AUTH capacity is 10. Exhaust all 10 tokens:
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", clientIp)
                            .header("X-Tenant-ID", "mitocrunch")
                            .contentType("application/json")
                            .content("{\"email\":\"invalid@test.com\",\"password\":\"dummy\"}"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 200, 400));
        }

        // 11th request must trigger rate limiter (HTTP 429)
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", clientIp)
                        .header("X-Tenant-ID", "mitocrunch")
                        .contentType("application/json")
                        .content("{\"email\":\"invalid@test.com\",\"password\":\"dummy\"}"))
                .andExpect(status().isTooManyRequests());

        // Verify maito_rate_limit_rejections_total metric is incremented
        Counter rejectionCounter = meterRegistry.find("maito_rate_limit_rejections_total")
                .tag("route_group", "AUTH")
                .counter();

        assertThat(rejectionCounter).isNotNull();
        assertThat(rejectionCounter.count()).isGreaterThanOrEqualTo(1.0);

        // Verify scraped Prometheus output contains the metric
        MvcResult prometheusResult = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String body = prometheusResult.getResponse().getContentAsString();
        assertThat(body).contains("maito_rate_limit_rejections_total");
    }

    @Test
    @DisplayName("Business metrics: Order creation and GMV revenue counters are registered and operational")
    void shouldRecordBusinessMetrics() throws Exception {
        businessMetricsService.recordOrderCreated("mito_crunch", "CARD", "CREATED", new BigDecimal("1250.00"));
        businessMetricsService.setB2bCreditUsed("mito_crunch", 50000.0);

        Counter orderCounter = meterRegistry.find("maito_orders_created_total")
                .tag("tenant", "mito_crunch")
                .counter();
        assertThat(orderCounter).isNotNull();
        assertThat(orderCounter.count()).isGreaterThanOrEqualTo(1.0);

        Counter revenueCounter = meterRegistry.find("maito_gmv_revenue_total")
                .tag("tenant", "mito_crunch")
                .counter();
        assertThat(revenueCounter).isNotNull();
        assertThat(revenueCounter.count()).isGreaterThanOrEqualTo(1250.0);

        assertThat(businessMetricsService.getB2bCreditUsed("mito_crunch")).isEqualTo(50000.0);
    }
}
