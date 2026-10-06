package com.maito.b2b.api.service;

import com.maito.b2b.api.dto.B2BInvoiceResponse;
import com.maito.b2b.api.dto.B2BCreditLedgerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface B2BInvoiceService {
    B2BInvoiceResponse payInvoice(UUID invoiceId);
    B2BInvoiceResponse getInvoiceById(UUID invoiceId);
    B2BInvoiceResponse getInvoiceByOrderId(UUID orderId);
    Page<B2BInvoiceResponse> getCustomerInvoices(UUID customerProfileId, Pageable pageable);
    List<B2BCreditLedgerResponse> getPartnerCreditLedger(UUID customerProfileId);
}
