package com.maito.cms.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreatePageCommand(
    @NotBlank(message = "Page slug is required")
    String pageSlug,
    @NotBlank(message = "Page title is required")
    String title,
    Map<String, Object> seoMetadata,
    Boolean isPublished
) {}
