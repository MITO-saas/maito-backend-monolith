package com.maito.cms.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpdateSectionCommand(
    @NotBlank(message = "Component type is required")
    String componentType,
    @NotNull(message = "Display order is required")
    Integer displayOrder,
    Boolean isActive,
    Map<String, Object> visibilityRules,
    @NotNull(message = "Content payload is required")
    Map<String, Object> contentPayload
) {}
