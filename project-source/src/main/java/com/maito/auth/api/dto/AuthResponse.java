package com.maito.auth.api.dto;

import com.maito.user.api.dto.TenantProfileDto;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response payload containing JWT tokens and active profile")
public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresInSeconds,
    TenantProfileDto profile,
    String redirectTarget
) {}
