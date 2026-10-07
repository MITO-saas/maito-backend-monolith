package com.maito.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Master identity user details DTO")
public record GlobalUserDto(
    UUID id,
    String email,
    String phoneNumber,
    String accountStatus,
    int failedLoginAttempts,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt,
    String authProvider,
    String providerSubjectId,
    String avatarUrl
) {
    public GlobalUserDto(UUID id, String email, String phoneNumber, String accountStatus, int failedLoginAttempts, Instant lastLoginAt, Instant createdAt, Instant updatedAt) {
        this(id, email, phoneNumber, accountStatus, failedLoginAttempts, lastLoginAt, createdAt, updatedAt, "LOCAL", null, null);
    }
}
