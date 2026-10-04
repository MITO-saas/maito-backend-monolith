package com.maito.cms.api.dto;

import jakarta.validation.constraints.NotNull;

public record PublishPageCommand(
    @NotNull(message = "Published status is required")
    Boolean isPublished
) {}
