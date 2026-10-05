package com.maito.notification.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationLogDto(
        UUID id,
        String recipient,
        String channel,
        String templateCode,
        String status,
        Map<String, Object> payloadSnapshot,
        Instant sentAt,
        Instant createdAt
) {}
