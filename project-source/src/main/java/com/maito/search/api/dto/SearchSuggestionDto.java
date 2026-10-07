package com.maito.search.api.dto;

import java.math.BigDecimal;

public record SearchSuggestionDto(
        String text,
        String slug,
        String category,
        BigDecimal price,
        String primaryImage,
        String type
) {
}
