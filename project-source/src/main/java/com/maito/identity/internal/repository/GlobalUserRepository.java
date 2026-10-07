package com.maito.identity.internal.repository;

import com.maito.identity.internal.domain.GlobalUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlobalUserRepository extends JpaRepository<GlobalUser, UUID> {

    Optional<GlobalUser> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<GlobalUser> findByAuthProviderAndProviderSubjectId(String authProvider, String providerSubjectId);

    Optional<GlobalUser> findByPhoneNumber(String phoneNumber);
}
