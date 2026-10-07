package com.maito.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Tenant-scoped user profile DTO")
public record TenantProfileDto(
    UUID id,
    UUID globalUserId,
    String firstName,
    String lastName,
    String role,
    List<String> permissions,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    String avatarUrl
) {
    public TenantProfileDto(
            UUID id,
            UUID globalUserId,
            String firstName,
            String lastName,
            String role,
            List<String> permissions,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this(id, globalUserId, firstName, lastName, role, permissions, active, createdAt, updatedAt, null);
    }
}
