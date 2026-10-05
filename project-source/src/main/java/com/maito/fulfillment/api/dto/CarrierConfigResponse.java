package com.maito.fulfillment.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CarrierConfigResponse(
        UUID id,
        CarrierType carrierType,
        String displayName,
        boolean isEnabled,
        Map<String, Object> settings,
        Instant updatedAt
) {}
