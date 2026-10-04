package com.maito.tenant.repository;

import com.maito.tenant.domain.GlobalTenantDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlobalTenantDomainRepository extends JpaRepository<GlobalTenantDomain, UUID> {
    Optional<GlobalTenantDomain> findByDomainName(String domainName);
    boolean existsByDomainName(String domainName);
    List<GlobalTenantDomain> findByTenantId(String tenantId);
}