package com.maito.user.internal.repository;

import com.maito.user.internal.domain.TenantUserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantUserProfileRepository extends JpaRepository<TenantUserProfile, UUID> {

    Optional<TenantUserProfile> findByGlobalUserId(UUID globalUserId);
}
