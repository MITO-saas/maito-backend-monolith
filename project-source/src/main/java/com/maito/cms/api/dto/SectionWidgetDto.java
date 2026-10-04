package com.maito.cms.api.dto;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public record SectionWidgetDto(
    UUID sectionId,
    String componentType,
    int displayOrder,
    Map<String, Object> contentPayload
) implements Serializable {}
