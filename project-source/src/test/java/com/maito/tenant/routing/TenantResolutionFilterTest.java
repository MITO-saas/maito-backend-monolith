package com.maito.tenant.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    @Mock
    private TenantRoutingResolver routingResolver;

    @Mock
    private FilterChain filterChain;

    private TenantResolutionFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        filter = new TenantResolutionFilter(routingResolver, objectMapper);
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Filter resolves tenant via X-Tenant-ID header and binds to ThreadLocal")
    void shouldResolveTenantFromHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader(TenantResolutionFilter.TENANT_HEADER, "mito_crunch");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext expectedContext = new TenantContext(
                "mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch"
        );
        when(routingResolver.resolveByTenantId("mito_crunch")).thenReturn(Optional.of(expectedContext));

        doAnswer(invocation -> {
            assertThat(TenantContextHolder.get()).isNotNull();
            assertThat(TenantContextHolder.getTenantId()).isEqualTo("mito_crunch");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(response.getHeader(TenantResolutionFilter.TENANT_HEADER)).isEqualTo("mito_crunch");
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("Filter resolves tenant via Host header when X-Tenant-ID is missing")
    void shouldResolveTenantFromHostHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/catalog/items");
        request.addHeader("Host", "store.mitocrunch.com:8080");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext expectedContext = new TenantContext(
                "mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch"
        );
        when(routingResolver.resolveByDomain("store.mitocrunch.com:8080")).thenReturn(Optional.of(expectedContext));

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(response.getHeader(TenantResolutionFilter.TENANT_HEADER)).isEqualTo("mito_crunch");
    }

    @Test
    @DisplayName("Filter rejects request when tenant cannot be resolved")
    void shouldRejectWhenTenantNotFound() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader(TenantResolutionFilter.TENANT_HEADER, "unknown_tenant");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(routingResolver.resolveByTenantId("unknown_tenant")).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("TENANT_RESOLUTION_FAILED");
    }
}