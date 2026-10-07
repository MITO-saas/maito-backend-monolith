package com.maito.catalog.api.event;

import java.util.UUID;

public record ProductCreatedEvent(
        UUID productId,
        String tenantId,
        String slug
) {
}
