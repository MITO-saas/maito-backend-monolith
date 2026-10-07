package com.maito.search.api.dto;

import java.math.BigDecimal;

public record SearchCriteria(
        String q,
        String category,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStock,
        String sort,
        String dietary,
        String currency,
        Integer page,
        Integer size
) {
    public int pageOrDefault() {
        return (page != null && page >= 0) ? page : 0;
    }

    public int sizeOrDefault() {
        return (size != null && size > 0) ? size : 20;
    }
}
