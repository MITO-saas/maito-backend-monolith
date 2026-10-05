package com.maito.auth.api.dto;

import java.util.List;
import java.util.UUID;

public record UserMeResponse(
    UUID globalUserId,
    UUID profileId,
    String email,
    String tenantId,
    String role,
    List<String> permissions
) {}
