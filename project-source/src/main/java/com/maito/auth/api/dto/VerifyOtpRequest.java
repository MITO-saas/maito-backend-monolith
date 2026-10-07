package com.maito.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload for verifying phone OTP and issuing customer session")
public record VerifyOtpRequest(
    @NotBlank(message = "Phone number is required")
    @Schema(description = "Mobile phone number with country code", example = "+919876543210")
    String phone,

    @NotBlank(message = "OTP code is required")
    @Schema(description = "6-digit OTP code", example = "123456")
    String code,

    @Schema(description = "Optional guest cart ID to automatically merge upon login")
    String guestCartId
) {}
