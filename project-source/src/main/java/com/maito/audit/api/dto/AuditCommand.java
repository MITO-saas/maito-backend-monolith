package com.maito.audit.api.dto;

import java.util.Map;
import java.util.UUID;

public record AuditCommand(
        UUID actorId,
        String actorEmail,
        String actorRole,
        String actionType,
        String entityType,
        String entityId,
        String ipAddress,
        Map<String, Object> detailsBefore,
        Map<String, Object> detailsAfter
) {}
