package com.maito.catalog.api.dto;

import java.util.List;
import java.util.UUID;

public record CategoryDto(
    UUID id,
    String slug,
    String name,
    String description,
    UUID parentId,
    String materializedPath,
    Integer displayOrder,
    Boolean isActive,
    List<CategoryDto> children
) {}
