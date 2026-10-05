package com.maito.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateProductCommand(
    @NotBlank String slug,
    @NotBlank String name,
    @NotBlank String brand,
    String shortDescription,
    String description,
    UUID categoryId,
    String hsnCode,
    BigDecimal taxRatePercent,
    Map<String, Object> attributes,
    Boolean isPublished,
    List<CreateVariantCommand> variants
) {}
