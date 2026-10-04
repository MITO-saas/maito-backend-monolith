package com.maito.tenant.repository;

import com.maito.tenant.domain.GlobalTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlobalTenantRepository extends JpaRepository<GlobalTenant, String> {
    Optional<GlobalTenant> findByTenantSlug(String tenantSlug);
    boolean existsByTenantId(String tenantId);
    boolean existsByTenantSlug(String tenantSlug);
}