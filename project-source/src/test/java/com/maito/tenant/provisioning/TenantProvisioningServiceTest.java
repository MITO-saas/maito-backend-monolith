package com.maito.tenant.provisioning;

import com.maito.shared.exception.BusinessException;
import com.maito.tenant.api.dto.ProvisionTenantRequest;
import com.maito.tenant.api.dto.TenantDetailsResponse;
import com.maito.tenant.datasource.HikariPoolManager;
import com.maito.tenant.domain.GlobalTenant;
import com.maito.tenant.domain.GlobalTenantDomain;
import com.maito.tenant.repository.GlobalTenantDomainRepository;
import com.maito.tenant.repository.GlobalTenantRepository;
import com.maito.tenant.routing.TenantRoutingResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

    @Mock
    private DataSource masterDataSource;

    @Mock
    private GlobalTenantRepository tenantRepository;

    @Mock
    private GlobalTenantDomainRepository domainRepository;

    @Mock
    private HikariPoolManager poolManager;

    @Mock
    private TenantRoutingResolver routingResolver;

    private TenantProvisioningService provisioningService;

    @BeforeEach
    void setUp() {
        provisioningService = new TenantProvisioningService(
                masterDataSource,
                tenantRepository,
                domainRepository,
                poolManager,
                routingResolver
        );
        ReflectionTestUtils.setField(provisioningService, "masterUrl", "jdbc:postgresql://localhost:5432/maito_db");
        ReflectionTestUtils.setField(provisioningService, "masterUsername", "maito_user");
        ReflectionTestUtils.setField(provisioningService, "masterPassword", "maito_pass");
    }

    @Test
    @DisplayName("Throws BusinessException when tenant ID already exists")
    void shouldThrowWhenTenantIdExists() {
        ProvisionTenantRequest request = new ProvisionTenantRequest(
                "mito_crunch", "mitocrunch", "Mito Crunch Ltd", "store.mitocrunch.com", "IN", "INR", Map.of()
        );

        when(tenantRepository.existsByTenantId("mito_crunch")).thenReturn(true);

        assertThatThrownBy(() -> provisioningService.provisionTenant(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tenant ID already exists");

        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws BusinessException when tenant slug already exists")
    void shouldThrowWhenTenantSlugExists() {
        ProvisionTenantRequest request = new ProvisionTenantRequest(
                "mito_crunch", "mitocrunch", "Mito Crunch Ltd", "store.mitocrunch.com", "IN", "INR", Map.of()
        );

        when(tenantRepository.existsByTenantId("mito_crunch")).thenReturn(false);
        when(tenantRepository.existsByTenantSlug("mitocrunch")).thenReturn(true);

        assertThatThrownBy(() -> provisioningService.provisionTenant(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tenant Slug already exists");
    }

    @Test
    @DisplayName("Throws BusinessException when primary domain is already registered")
    void shouldThrowWhenDomainExists() {
        ProvisionTenantRequest request = new ProvisionTenantRequest(
                "mito_crunch", "mitocrunch", "Mito Crunch Ltd", "store.mitocrunch.com", "IN", "INR", Map.of()
        );

        when(tenantRepository.existsByTenantId("mito_crunch")).thenReturn(false);
        when(tenantRepository.existsByTenantSlug("mitocrunch")).thenReturn(false);
        when(domainRepository.existsByDomainName("store.mitocrunch.com")).thenReturn(true);

        assertThatThrownBy(() -> provisioningService.provisionTenant(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Domain already registered");
    }

    @Test
    @DisplayName("Retrieves tenant details successfully with primary domain")
    void shouldRetrieveTenantDetails() {
        GlobalTenant tenant = GlobalTenant.builder()
                .tenantId("mito_crunch")
                .tenantSlug("mitocrunch")
                .legalEntityName("Mito Crunch Ltd")
                .accountState("ACTIVE")
                .routingConfig(Map.of("db_name", "db_mitocrunch"))
                .regionalProfile(Map.of("country", "IN"))
                .tierEntitlements(Map.of("tier", "ENTERPRISE"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        GlobalTenantDomain domain = GlobalTenantDomain.builder()
                .tenantId("mito_crunch")
                .domainName("store.mitocrunch.com")
                .isPrimary(true)
                .build();

        when(tenantRepository.findById("mito_crunch")).thenReturn(Optional.of(tenant));
        when(domainRepository.findByTenantId("mito_crunch")).thenReturn(List.of(domain));

        TenantDetailsResponse details = provisioningService.getTenantDetails("mito_crunch");

        assertThat(details.tenantId()).isEqualTo("mito_crunch");
        assertThat(details.tenantSlug()).isEqualTo("mitocrunch");
        assertThat(details.primaryDomain()).isEqualTo("store.mitocrunch.com");
        assertThat(details.accountState()).isEqualTo("ACTIVE");
    }
}