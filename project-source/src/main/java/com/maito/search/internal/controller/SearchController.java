package com.maito.search.internal.controller;

import com.maito.search.api.dto.SearchCriteria;
import com.maito.search.api.dto.SearchResultDto;
import com.maito.search.api.dto.SearchSuggestionDto;
import com.maito.search.api.service.ProductSearchService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Storefront Search", description = "Elasticsearch-powered product search, faceted aggregations and typeahead suggestions")
public class SearchController {

    private final ProductSearchService searchService;

    @GetMapping("/products")
    @Operation(summary = "Search products with edge-ngram matching, typo tolerance, price filters, and faceted aggregation")
    public ResponseEntity<ApiResponse<SearchResultDto>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(required = false) String dietary,
            @RequestParam(required = false, defaultValue = "INR") String currency,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        SearchCriteria criteria = new SearchCriteria(
                q, category, brand, minPrice, maxPrice, inStock, sort, dietary, currency, page, size
        );
        SearchResultDto result = searchService.searchProducts(criteria);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/suggest")
    @Operation(summary = "Get instant typeahead suggestions for search-as-you-type autocomplete")
    public ResponseEntity<ApiResponse<List<SearchSuggestionDto>>> suggest(
            @RequestParam String q
    ) {
        List<SearchSuggestionDto> suggestions = searchService.suggestKeywords(q);
        return ResponseEntity.ok(ApiResponse.ok(suggestions));
    }
}
