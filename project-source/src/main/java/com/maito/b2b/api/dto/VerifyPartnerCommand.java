package com.maito.b2b.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record VerifyPartnerCommand(
        @NotBlank(message = "Verification status is required (VERIFIED / REJECTED / SUSPENDED)")
        String verificationStatus,

        @DecimalMin(value = "0.0", message = "Credit limit cannot be negative")
        BigDecimal creditLimit,

        Integer paymentTermsDays,

        String notes
) {}
