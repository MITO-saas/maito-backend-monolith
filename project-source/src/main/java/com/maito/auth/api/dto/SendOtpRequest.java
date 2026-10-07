package com.maito.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload for requesting a 6-digit phone OTP")
public record SendOtpRequest(
    @NotBlank(message = "Phone number is required")
    @Schema(description = "Mobile phone number with country code", example = "+919876543210")
    String phone
) {}
