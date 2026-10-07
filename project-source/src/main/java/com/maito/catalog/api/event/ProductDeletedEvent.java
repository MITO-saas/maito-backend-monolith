package com.maito.catalog.api.event;

import java.util.UUID;

public record ProductDeletedEvent(
        UUID productId,
        String tenantId,
        String slug
) {
}
