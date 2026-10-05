package com.maito.fulfillment.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CheckpointDto(
        UUID id,
        String checkpointStatus,
        String locationHub,
        String statusDescription,
        Instant eventTimestamp
) {}
