package com.maito.tenant.repository;

import com.maito.tenant.domain.GlobalUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlobalUserRepository extends JpaRepository<GlobalUser, UUID> {
    Optional<GlobalUser> findByPrimaryEmail(String primaryEmail);
    Optional<GlobalUser> findByPhoneE164(String phoneE164);
    boolean existsByPrimaryEmail(String primaryEmail);
    boolean existsByPhoneE164(String phoneE164);
}