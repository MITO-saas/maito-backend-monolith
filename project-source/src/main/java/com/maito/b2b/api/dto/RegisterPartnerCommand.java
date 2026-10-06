package com.maito.b2b.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public record RegisterPartnerCommand(
        @NotBlank(message = "Company legal name is required")
        String companyLegalName,

        String tradeName,

        @NotBlank(message = "GSTIN is required")
        @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$", message = "Invalid GSTIN format")
        String gstin,

        String pan,

        String fssaiLicenseNumber,

        Map<String, Object> billingAddress
) {}
