package com.maito.fulfillment.api.dto;

import java.util.Map;

public record ConfigureCarrierCommand(
        Boolean isEnabled,
        Map<String, Object> credentials,
        Map<String, Object> settings
) {}
