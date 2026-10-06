package com.maito.b2b.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record B2BPartnerResponse(
        UUID id,
        UUID customerProfileId,
        String companyLegalName,
        String tradeName,
        String gstin,
        String pan,
        String fssaiLicenseNumber,
        String verificationStatus,
        BigDecimal creditLimit,
        BigDecimal usedCredit,
        BigDecimal availableCredit,
        Integer paymentTermsDays,
        Map<String, Object> billingAddress,
        Instant createdAt,
        Instant updatedAt
) {}
