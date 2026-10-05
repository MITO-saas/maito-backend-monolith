package com.maito.auth.api.dto;

public record TokenRefreshResponse(
    String accessToken,
    long expiresInSeconds
) {}
