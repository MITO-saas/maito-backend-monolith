package com.maito.audit.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        UUID actorId,
        String actorEmail,
        String actorRole,
        String actionType,
        String entityType,
        String entityId,
        String ipAddress,
        Map<String, Object> detailsBefore,
        Map<String, Object> detailsAfter,
        Instant createdAt
) {}
