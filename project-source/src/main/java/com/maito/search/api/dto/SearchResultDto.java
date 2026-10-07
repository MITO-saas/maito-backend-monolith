package com.maito.search.api.dto;

import java.util.List;
import java.util.Map;

public record SearchResultDto(
        List<ProductSearchHitDto> products,
        long totalHits,
        int page,
        int size,
        int totalPages,
        Map<String, Long> brandFacets,
        Map<String, Long> categoryFacets,
        PriceStatsDto priceStats,
        String searchEngine
) {
}
