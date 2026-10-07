package com.maito.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload for 1-click social sign-in (Google / Facebook)")
public record SocialLoginRequest(
    @NotBlank(message = "Social provider is required")
    @Schema(description = "Auth provider: GOOGLE or FACEBOOK", example = "GOOGLE")
    String provider,

    @NotBlank(message = "ID token is required")
    @Schema(description = "Google / Meta ID Token / OAuth2 credential JWT", example = "mock-google-token-customer@gmail.com")
    String idToken,

    @Schema(description = "Optional guest cart ID to automatically merge upon login")
    String guestCartId
) {}
