package com.maito.b2b.internal.repository;

import com.maito.b2b.internal.domain.B2BCreditLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface B2BCreditLedgerRepository extends JpaRepository<B2BCreditLedger, UUID> {
    Page<B2BCreditLedger> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId, Pageable pageable);
    List<B2BCreditLedger> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId);
}
