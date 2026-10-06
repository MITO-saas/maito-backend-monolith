package com.maito.b2b.internal.repository;

import com.maito.b2b.internal.domain.B2BPartner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2BPartnerRepository extends JpaRepository<B2BPartner, UUID> {
    Optional<B2BPartner> findByCustomerProfileId(UUID customerProfileId);
    Optional<B2BPartner> findByGstin(String gstin);
    Page<B2BPartner> findByVerificationStatus(String verificationStatus, Pageable pageable);
    boolean existsByCustomerProfileId(UUID customerProfileId);
    boolean existsByGstin(String gstin);
}
