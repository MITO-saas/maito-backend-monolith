package com.maito.b2b.internal.repository;

import com.maito.b2b.internal.domain.B2BInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2BInvoiceRepository extends JpaRepository<B2BInvoice, UUID> {
    Optional<B2BInvoice> findByInvoiceNumber(String invoiceNumber);
    Optional<B2BInvoice> findByOrderId(UUID orderId);
    Page<B2BInvoice> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId, Pageable pageable);
    List<B2BInvoice> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId);
    Page<B2BInvoice> findByPaymentStatusOrderByCreatedAtDesc(String paymentStatus, Pageable pageable);
}
