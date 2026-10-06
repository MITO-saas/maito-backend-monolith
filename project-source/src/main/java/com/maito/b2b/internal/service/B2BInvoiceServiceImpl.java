package com.maito.b2b.internal.service;

import com.maito.b2b.api.dto.B2BCreditLedgerResponse;
import com.maito.b2b.api.dto.B2BInvoiceResponse;
import com.maito.b2b.api.service.B2BInvoiceService;
import com.maito.b2b.internal.domain.B2BCreditLedger;
import com.maito.b2b.internal.domain.B2BInvoice;
import com.maito.b2b.internal.domain.B2BPartner;
import com.maito.b2b.internal.repository.B2BCreditLedgerRepository;
import com.maito.b2b.internal.repository.B2BInvoiceRepository;
import com.maito.b2b.internal.repository.B2BPartnerRepository;
import com.maito.order.internal.repository.OrderRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class B2BInvoiceServiceImpl implements B2BInvoiceService {

    private final B2BInvoiceRepository invoiceRepository;
    private final B2BPartnerRepository partnerRepository;
    private final B2BCreditLedgerRepository creditLedgerRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public B2BInvoiceResponse payInvoice(UUID invoiceId) {
        B2BInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.B2B_INVOICE_NOT_FOUND, "B2B invoice not found: " + invoiceId));

        if ("PAID".equalsIgnoreCase(invoice.getPaymentStatus())) {
            throw new BusinessException(ErrorCode.B2B_INVOICE_ALREADY_PAID, "Invoice is already settled: " + invoice.getInvoiceNumber());
        }

        invoice.setPaymentStatus("PAID");
        invoice.setPaidAt(Instant.now());
        B2BInvoice saved = invoiceRepository.save(invoice);

        // Restore credit if Net credit terms
        B2BPartner partner = partnerRepository.findById(invoice.getPartnerId()).orElse(null);
        if (partner != null && ("NET_30".equalsIgnoreCase(invoice.getPaymentTerms()) || "NET_60".equalsIgnoreCase(invoice.getPaymentTerms()))) {
            BigDecimal newUsed = partner.getUsedCredit().subtract(invoice.getTotalAmount()).max(BigDecimal.ZERO);
            partner.setUsedCredit(newUsed);
            partnerRepository.save(partner);

            B2BCreditLedger release = B2BCreditLedger.builder()
                    .partnerId(partner.getId())
                    .entryType("CREDIT_RELEASE")
                    .amount(invoice.getTotalAmount())
                    .usedCreditAfter(newUsed)
                    .referenceId(invoice.getInvoiceNumber())
                    .notes("Credit line restored upon settlement of invoice: " + invoice.getInvoiceNumber())
                    .build();
            creditLedgerRepository.save(release);
            log.info("Restored credit of [{}] for partner [{}]. New used credit: [{}]",
                    invoice.getTotalAmount(), partner.getId(), newUsed);
        }

        // Transition Order payment status
        orderRepository.findById(invoice.getOrderId()).ifPresent(order -> {
            order.setPaymentStatus("PAID");
            orderRepository.save(order);
        });

        log.info("Settled B2B invoice [{}] for order [{}]", saved.getInvoiceNumber(), saved.getOrderId());
        return toInvoiceResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public B2BInvoiceResponse getInvoiceById(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .map(this::toInvoiceResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.B2B_INVOICE_NOT_FOUND, "B2B invoice not found: " + invoiceId));
    }

    @Override
    @Transactional(readOnly = true)
    public B2BInvoiceResponse getInvoiceByOrderId(UUID orderId) {
        return invoiceRepository.findByOrderId(orderId)
                .map(this::toInvoiceResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.B2B_INVOICE_NOT_FOUND, "B2B invoice not found for order: " + orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<B2BInvoiceResponse> getCustomerInvoices(UUID customerProfileId, Pageable pageable) {
        return partnerRepository.findByCustomerProfileId(customerProfileId)
                .map(p -> invoiceRepository.findByPartnerIdOrderByCreatedAtDesc(p.getId(), pageable).map(this::toInvoiceResponse))
                .orElse(Page.empty());
    }

    @Override
    @Transactional(readOnly = true)
    public List<B2BCreditLedgerResponse> getPartnerCreditLedger(UUID customerProfileId) {
        return partnerRepository.findByCustomerProfileId(customerProfileId)
                .map(p -> creditLedgerRepository.findByPartnerIdOrderByCreatedAtDesc(p.getId()).stream().map(this::toLedgerResponse).toList())
                .orElse(List.of());
    }

    private B2BInvoiceResponse toInvoiceResponse(B2BInvoice inv) {
        return new B2BInvoiceResponse(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getPartnerId(),
                inv.getOrderId(),
                inv.getSubtotalAmount(),
                inv.getTaxAmount(),
                inv.getTotalAmount(),
                inv.getPaymentStatus(),
                inv.getPaymentTerms(),
                inv.getIssuedAt(),
                inv.getDueAt(),
                inv.getPaidAt(),
                inv.getTaxBreakdown(),
                inv.getCreatedAt()
        );
    }

    private B2BCreditLedgerResponse toLedgerResponse(B2BCreditLedger l) {
        return new B2BCreditLedgerResponse(
                l.getId(),
                l.getPartnerId(),
                l.getEntryType(),
                l.getAmount(),
                l.getUsedCreditAfter(),
                l.getReferenceId(),
                l.getNotes(),
                l.getCreatedAt()
        );
    }
}
