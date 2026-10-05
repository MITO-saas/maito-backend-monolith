package com.maito.user.internal.repository;

import com.maito.user.internal.domain.TenantUserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantUserAddressRepository extends JpaRepository<TenantUserAddress, UUID> {

    List<TenantUserAddress> findByProfileIdOrderByCreatedAtDesc(UUID profileId);

    List<TenantUserAddress> findByProfileIdAndIsDefaultTrue(UUID profileId);

    Optional<TenantUserAddress> findByIdAndProfileId(UUID id, UUID profileId);
}
