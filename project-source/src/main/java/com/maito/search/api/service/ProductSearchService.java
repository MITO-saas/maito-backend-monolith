package com.maito.search.api.service;

import com.maito.search.api.dto.SearchCriteria;
import com.maito.search.api.dto.SearchResultDto;
import com.maito.search.api.dto.SearchSuggestionDto;

import java.util.List;
import java.util.UUID;

public interface ProductSearchService {
    SearchResultDto searchProducts(SearchCriteria criteria);
    List<SearchSuggestionDto> suggestKeywords(String query);
    int reindexTenantCatalog(String tenantSlug);
    void indexProduct(UUID productId, String tenantId);
    void deleteProduct(UUID productId, String tenantId);
    void updateStock(UUID variantId, Integer availableStock, Boolean inStock, String tenantId);
}
