package com.maito.audit.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditFilter(
        String actionType,
        String entityType,
        UUID actorId,
        Instant fromDate,
        Instant toDate
) {}
