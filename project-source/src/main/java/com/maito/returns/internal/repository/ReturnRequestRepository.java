package com.maito.returns.internal.repository;

import com.maito.returns.internal.domain.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID>, JpaSpecificationExecutor<ReturnRequest> {
    Optional<ReturnRequest> findByReturnNumber(String returnNumber);
    Page<ReturnRequest> findByCustomerProfileIdOrderByCreatedAtDesc(UUID customerProfileId, Pageable pageable);
    Page<ReturnRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}