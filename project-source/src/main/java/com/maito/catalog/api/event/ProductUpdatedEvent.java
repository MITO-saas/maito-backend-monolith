package com.maito.catalog.api.event;

import java.util.UUID;

public record ProductUpdatedEvent(
        UUID productId,
        String tenantId,
        String slug
) {
}
