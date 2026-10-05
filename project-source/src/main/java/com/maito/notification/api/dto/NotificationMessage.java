package com.maito.notification.api.dto;

import java.util.Map;

public record NotificationMessage(
        String recipient,
        NotificationChannelType channel,
        String templateCode,
        String subject,
        String content,
        Map<String, Object> metadata
) {}
