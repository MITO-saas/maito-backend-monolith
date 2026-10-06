package com.maito.b2b.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record B2BInvoiceResponse(
        UUID id,
        String invoiceNumber,
        UUID partnerId,
        UUID orderId,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String paymentStatus,
        String paymentTerms,
        Instant issuedAt,
        Instant dueAt,
        Instant paidAt,
        Map<String, Object> taxBreakdown,
        Instant createdAt
) {}
