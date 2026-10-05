package com.maito.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Customer address DTO")
public record AddressDto(
    UUID id,
    UUID profileId,
    String addressType,
    String recipientName,
    String phone,
    String addressLine1,
    String addressLine2,
    String city,
    String state,
    String postalCode,
    String countryCode,
    boolean isDefault,
    Instant createdAt,
    Instant updatedAt
) {}
