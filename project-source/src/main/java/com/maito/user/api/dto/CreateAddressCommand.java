package com.maito.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateAddressCommand(
    String addressType,
    @NotBlank String recipientName,
    @NotBlank String phone,
    @NotBlank String addressLine1,
    String addressLine2,
    @NotBlank String city,
    @NotBlank String state,
    @NotBlank String postalCode,
    String countryCode,
    Boolean isDefault
) {}
