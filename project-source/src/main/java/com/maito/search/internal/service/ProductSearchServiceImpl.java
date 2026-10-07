package com.maito.search.internal.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.maito.catalog.internal.domain.CatalogCategory;
import com.maito.catalog.internal.domain.CatalogProduct;
import com.maito.catalog.internal.domain.CatalogProductVariant;
import com.maito.catalog.internal.domain.InventoryLevel;
import com.maito.catalog.internal.repository.CatalogCategoryRepository;
import com.maito.catalog.internal.repository.CatalogProductRepository;
import com.maito.catalog.internal.repository.CatalogProductVariantRepository;
import com.maito.catalog.internal.repository.InventoryLevelRepository;
import com.maito.search.api.dto.PriceStatsDto;
import com.maito.search.api.dto.ProductSearchHitDto;
import com.maito.search.api.dto.SearchCriteria;
import com.maito.search.api.dto.SearchResultDto;
import com.maito.search.api.dto.SearchSuggestionDto;
import com.maito.search.api.service.ProductSearchService;
import com.maito.search.internal.config.SearchElasticsearchProperties;
import com.maito.search.internal.document.ProductDocument;
import com.maito.search.internal.index.IndexManager;
import com.maito.search.internal.resolver.TenantIndexResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSearchServiceImpl implements ProductSearchService {

    private final SearchElasticsearchProperties properties;
    private final TenantIndexResolver tenantIndexResolver;
    private final IndexManager indexManager;
    private final CatalogProductRepository productRepository;
    private final CatalogProductVariantRepository variantRepository;
    private final CatalogCategoryRepository categoryRepository;
    private final InventoryLevelRepository inventoryRepository;

    @Autowired(required = false)
    private ElasticsearchClient elasticsearchClient;

    public boolean isElasticsearchAvailable() {
        if (!properties.isEnabled() || elasticsearchClient == null) {
            return false;
        }
        try {
            return elasticsearchClient.ping().value();
        } catch (Exception ex) {
            log.debug("Elasticsearch ping failed: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SearchResultDto searchProducts(SearchCriteria criteria) {
        String tenantSlug = tenantIndexResolver.resolveActiveTenantSlug();

        if (isElasticsearchAvailable()) {
            try {
                return executeElasticsearchQuery(tenantSlug, criteria);
            } catch (Exception ex) {
                log.warn("Elasticsearch query failed for tenant [{}], gracefully falling back to SQL: {}", tenantSlug, ex.getMessage());
                return executeSqlFallback(criteria);
            }
        }

        log.debug("Elasticsearch disabled/unavailable for tenant [{}]. Executing SQL search fallback.", tenantSlug);
        return executeSqlFallback(criteria);
    }

    private SearchResultDto executeElasticsearchQuery(String tenantSlug, SearchCriteria criteria) throws Exception {
        String targetIndex = tenantSlug + "_products";

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 1. Full-text search with edge-ngram + fuzzy match
        if (criteria.q() != null && !criteria.q().isBlank()) {
            String queryText = criteria.q().trim();
            boolQuery.must(m -> m.multiMatch(mm -> mm
                    .fields("name^3", "name.standard^2", "description", "brand^2", "tags^1.5")
                    .query(queryText)
                    .fuzziness("AUTO")
            ));
        }

        // 2. Facet & Attribute Filters
        if (criteria.category() != null && !criteria.category().isBlank()) {
            String cat = criteria.category().trim().toLowerCase();
            boolQuery.filter(f -> f.term(t -> t.field("categorySlug").value(cat)));
        }

        if (criteria.brand() != null && !criteria.brand().isBlank()) {
            String b = criteria.brand().trim();
            boolQuery.filter(f -> f.term(t -> t.field("brand").value(b)));
        }

        if (criteria.minPrice() != null || criteria.maxPrice() != null) {
            boolQuery.filter(f -> f.range(r -> {
                r.field("price");
                if (criteria.minPrice() != null) {
                    r.gte(JsonData.of(criteria.minPrice().doubleValue()));
                }
                if (criteria.maxPrice() != null) {
                    r.lte(JsonData.of(criteria.maxPrice().doubleValue()));
                }
                return r;
            }));
        }

        if (Boolean.TRUE.equals(criteria.inStock())) {
            boolQuery.filter(f -> f.term(t -> t.field("inStock").value(true)));
        }

        // 3. Sorting
        List<SortOptions> sortOptions = new ArrayList<>();
        String sort = criteria.sort() != null ? criteria.sort().trim().toLowerCase() : "relevance";
        switch (sort) {
            case "price_asc" -> sortOptions.add(SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Asc))));
            case "price_desc" -> sortOptions.add(SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Desc))));
            case "newest" -> sortOptions.add(SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))));
            case "rating" -> sortOptions.add(SortOptions.of(s -> s.field(f -> f.field("ratingAverage").order(SortOrder.Desc))));
            default -> sortOptions.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
        }

        int page = criteria.pageOrDefault();
        int size = criteria.sizeOrDefault();
        int from = page * size;

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(targetIndex)
                .query(Query.of(q -> q.bool(boolQuery.build())))
                .from(from)
                .size(size)
                .sort(sortOptions)
                .aggregations("brands", a -> a.terms(t -> t.field("brand").size(20)))
                .aggregations("categories", a -> a.terms(t -> t.field("categorySlug").size(20)))
                .aggregations("price_stats", a -> a.stats(st -> st.field("price")))
        );

        SearchResponse<ProductDocument> response = elasticsearchClient.search(searchRequest, ProductDocument.class);

        long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;
        List<ProductSearchHitDto> products = new ArrayList<>();
        for (Hit<ProductDocument> hit : response.hits().hits()) {
            ProductDocument doc = hit.source();
            if (doc != null) {
                products.add(mapDocumentToHit(doc));
            }
        }

        // Extract brand facets
        Map<String, Long> brandFacets = new LinkedHashMap<>();
        Aggregate brandAgg = response.aggregations().get("brands");
        if (brandAgg != null && brandAgg.isSterms()) {
            StringTermsAggregate sterms = brandAgg.sterms();
            for (StringTermsBucket b : sterms.buckets().array()) {
                brandFacets.put(b.key().stringValue(), b.docCount());
            }
        }

        // Extract category facets
        Map<String, Long> categoryFacets = new LinkedHashMap<>();
        Aggregate catAgg = response.aggregations().get("categories");
        if (catAgg != null && catAgg.isSterms()) {
            StringTermsAggregate sterms = catAgg.sterms();
            for (StringTermsBucket b : sterms.buckets().array()) {
                categoryFacets.put(b.key().stringValue(), b.docCount());
            }
        }

        // Extract price stats
        PriceStatsDto priceStats = new PriceStatsDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        Aggregate statsAgg = response.aggregations().get("price_stats");
        if (statsAgg != null && statsAgg.isStats()) {
            var stats = statsAgg.stats();
            BigDecimal min = (!Double.isInfinite(stats.min()) && !Double.isNaN(stats.min())) ? BigDecimal.valueOf(stats.min()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal max = (!Double.isInfinite(stats.max()) && !Double.isNaN(stats.max())) ? BigDecimal.valueOf(stats.max()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal avg = (!Double.isInfinite(stats.avg()) && !Double.isNaN(stats.avg())) ? BigDecimal.valueOf(stats.avg()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            priceStats = new PriceStatsDto(min, max, avg);
        }

        int totalPages = size > 0 ? (int) Math.ceil((double) totalHits / size) : 0;

        return new SearchResultDto(
                products,
                totalHits,
                page,
                size,
                totalPages,
                brandFacets,
                categoryFacets,
                priceStats,
                "ELASTICSEARCH"
        );
    }

    @Transactional(readOnly = true)
    public SearchResultDto executeSqlFallback(SearchCriteria criteria) {
        List<CatalogProduct> allProducts = productRepository.findByIsPublishedTrue();
        Map<UUID, CatalogCategory> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(CatalogCategory::getId, c -> c, (a, b) -> a));

        String query = criteria.q() != null ? criteria.q().trim().toLowerCase() : null;
        String filterCategory = criteria.category() != null ? criteria.category().trim().toLowerCase() : null;
        String filterBrand = criteria.brand() != null ? criteria.brand().trim().toLowerCase() : null;

        List<ProductSearchHitDto> matched = new ArrayList<>();

        for (CatalogProduct p : allProducts) {
            CatalogCategory cat = p.getCategoryId() != null ? categoryMap.get(p.getCategoryId()) : null;
            String catSlug = (cat != null) ? cat.getSlug() : "";
            String catName = (cat != null) ? cat.getName() : "";

            // 1. Text Query Filter (Typo/token substring matching)
            if (query != null && !query.isBlank()) {
                boolean matchesText = matchesQuery(p, query);
                if (!matchesText) {
                    continue;
                }
            }

            // 2. Category Filter
            if (filterCategory != null && !filterCategory.isBlank()) {
                if (!catSlug.equalsIgnoreCase(filterCategory)) {
                    continue;
                }
            }

            // 3. Brand Filter
            if (filterBrand != null && !filterBrand.isBlank()) {
                if (p.getBrand() == null || !p.getBrand().equalsIgnoreCase(filterBrand)) {
                    continue;
                }
            }

            // Resolve variants, pricing, and stock
            List<CatalogProductVariant> variants = variantRepository.findByProductIdAndIsActiveTrue(p.getId());
            if (variants.isEmpty()) {
                continue;
            }

            BigDecimal productPrice = resolveEffectivePrice(variants);
            BigDecimal mrp = resolveEffectiveMrp(variants);
            int availableStock = resolveAvailableStock(variants);
            boolean inStock = availableStock > 0;

            // 4. InStock Filter
            if (Boolean.TRUE.equals(criteria.inStock()) && !inStock) {
                continue;
            }

            // 5. Price Range Filter
            if (criteria.minPrice() != null && productPrice.compareTo(criteria.minPrice()) < 0) {
                continue;
            }
            if (criteria.maxPrice() != null && productPrice.compareTo(criteria.maxPrice()) > 0) {
                continue;
            }

            int discount = (mrp.compareTo(BigDecimal.ZERO) > 0 && mrp.compareTo(productPrice) > 0)
                    ? mrp.subtract(productPrice).multiply(BigDecimal.valueOf(100)).divide(mrp, 0, RoundingMode.HALF_UP).intValue()
                    : 0;

            String primaryImage = extractPrimaryImage(variants);
            List<String> tags = extractTags(p);

            matched.add(new ProductSearchHitDto(
                    p.getId(),
                    p.getSlug(),
                    p.getName(),
                    p.getBrand(),
                    p.getShortDescription(),
                    catSlug,
                    catName,
                    productPrice,
                    mrp,
                    discount,
                    availableStock,
                    inStock,
                    primaryImage,
                    tags,
                    4.8,
                    128
            ));
        }

        // Compute Facets over matched records
        Map<String, Long> brandFacets = matched.stream()
                .filter(m -> m.brand() != null && !m.brand().isBlank())
                .collect(Collectors.groupingBy(ProductSearchHitDto::brand, Collectors.counting()));

        Map<String, Long> categoryFacets = matched.stream()
                .filter(m -> m.categorySlug() != null && !m.categorySlug().isBlank())
                .collect(Collectors.groupingBy(ProductSearchHitDto::categorySlug, Collectors.counting()));

        BigDecimal minPrice = matched.stream().map(ProductSearchHitDto::price).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal maxPrice = matched.stream().map(ProductSearchHitDto::price).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal avgPrice = matched.isEmpty() ? BigDecimal.ZERO :
                matched.stream().map(ProductSearchHitDto::price).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(matched.size()), 2, RoundingMode.HALF_UP);

        PriceStatsDto priceStats = new PriceStatsDto(minPrice, maxPrice, avgPrice);

        // Sorting
        String sort = criteria.sort() != null ? criteria.sort().trim().toLowerCase() : "relevance";
        switch (sort) {
            case "price_asc" -> matched.sort(Comparator.comparing(ProductSearchHitDto::price));
            case "price_desc" -> matched.sort(Comparator.comparing(ProductSearchHitDto::price).reversed());
            case "rating" -> matched.sort(Comparator.comparing(ProductSearchHitDto::ratingAverage).reversed());
            default -> matched.sort(Comparator.comparing(ProductSearchHitDto::name));
        }

        // Pagination
        int page = criteria.pageOrDefault();
        int size = criteria.sizeOrDefault();
        long totalHits = matched.size();
        int fromIndex = Math.min(page * size, matched.size());
        int toIndex = Math.min(fromIndex + size, matched.size());
        List<ProductSearchHitDto> pageContent = matched.subList(fromIndex, toIndex);
        int totalPages = size > 0 ? (int) Math.ceil((double) totalHits / size) : 0;

        return new SearchResultDto(
                pageContent,
                totalHits,
                page,
                size,
                totalPages,
                brandFacets,
                categoryFacets,
                priceStats,
                "SQL_FALLBACK"
        );
    }

    private boolean matchesQuery(CatalogProduct p, String query) {
        String name = p.getName() != null ? p.getName().toLowerCase() : "";
        String brand = p.getBrand() != null ? p.getBrand().toLowerCase() : "";
        String desc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
        String shortDesc = p.getShortDescription() != null ? p.getShortDescription().toLowerCase() : "";

        if (name.contains(query) || brand.contains(query) || desc.contains(query) || shortDesc.contains(query)) {
            return true;
        }

        // Typo tolerance: token overlap (e.g. 'pari peri' -> checks 'pari' and 'peri')
        String[] tokens = query.split("\s+");
        for (String t : tokens) {
            if (t.length() >= 3) {
                if (name.contains(t) || brand.contains(t) || desc.contains(t)) {
                    return true;
                }
                // Single letter transposition / Levenshtein distance 1
                if (isFuzzyMatch(name, t) || isFuzzyMatch(brand, t)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isFuzzyMatch(String target, String token) {
        String[] targetWords = target.split("\s+");
        for (String w : targetWords) {
            if (levenshteinDistance(w.toLowerCase(), token.toLowerCase()) <= 1) {
                return true;
            }
        }
        return false;
    }

    private int levenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchSuggestionDto> suggestKeywords(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = query.trim();
        String tenantSlug = tenantIndexResolver.resolveActiveTenantSlug();

        if (isElasticsearchAvailable()) {
            try {
                SearchRequest searchRequest = SearchRequest.of(s -> s
                        .index(tenantSlug + "_products")
                        .query(q -> q.bool(b -> b
                                .should(sh -> sh.matchPhrasePrefix(mpp -> mpp.field("name").query(trimmed)))
                                .should(sh -> sh.prefix(pr -> pr.field("name.keyword").value(trimmed)))
                                .should(sh -> sh.prefix(pr -> pr.field("brand").value(trimmed)))
                        ))
                        .size(5)
                );

                SearchResponse<ProductDocument> response = elasticsearchClient.search(searchRequest, ProductDocument.class);
                List<SearchSuggestionDto> suggestions = new ArrayList<>();
                for (Hit<ProductDocument> hit : response.hits().hits()) {
                    ProductDocument doc = hit.source();
                    if (doc != null) {
                        suggestions.add(new SearchSuggestionDto(
                                doc.getName(),
                                doc.getSlug(),
                                doc.getCategoryName(),
                                doc.getPrice(),
                                doc.getPrimaryImage(),
                                "PRODUCT"
                        ));
                    }
                }
                if (!suggestions.isEmpty()) {
                    return suggestions;
                }
            } catch (Exception ex) {
                log.warn("Elasticsearch suggest query failed: {}", ex.getMessage());
            }
        }

        // SQL fallback suggestions
        String qLower = trimmed.toLowerCase();
        return productRepository.findByIsPublishedTrue().stream()
                .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(qLower)) ||
                             (p.getBrand() != null && p.getBrand().toLowerCase().contains(qLower)))
                .limit(5)
                .map(p -> {
                    List<CatalogProductVariant> variants = variantRepository.findByProductIdAndIsActiveTrue(p.getId());
                    BigDecimal price = resolveEffectivePrice(variants);
                    String img = extractPrimaryImage(variants);
                    return new SearchSuggestionDto(
                            p.getName(),
                            p.getSlug(),
                            p.getBrand(),
                            price,
                            img,
                            "PRODUCT"
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public int reindexTenantCatalog(String tenantSlug) {
        String safeSlug = (tenantSlug != null && !tenantSlug.isBlank()) ? tenantSlug.trim().toLowerCase() : "mito_crunch";
        log.info("Starting batch re-indexing for tenant [{}]", safeSlug);

        if (isElasticsearchAvailable()) {
            indexManager.ensureIndexAndAlias(safeSlug);
        }

        List<CatalogProduct> products = productRepository.findByIsPublishedTrue();
        int count = 0;
        for (CatalogProduct product : products) {
            indexProduct(product.getId(), safeSlug);
            count++;
        }

        log.info("Batch re-indexing completed for tenant [{}]. Total indexed: {}", safeSlug, count);
        return count;
    }

    @Override
    @Transactional(readOnly = true)
    public void indexProduct(UUID productId, String tenantId) {
        if (!isElasticsearchAvailable()) {
            log.debug("Skipping Elasticsearch indexing for product [{}] - ES unavailable", productId);
            return;
        }

        Optional<CatalogProduct> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty() || !Boolean.TRUE.equals(productOpt.get().getIsPublished())) {
            return;
        }

        CatalogProduct product = productOpt.get();
        String safeTenantSlug = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim().toLowerCase() : tenantIndexResolver.resolveActiveTenantSlug();

        List<CatalogProductVariant> variants = variantRepository.findByProductIdAndIsActiveTrue(product.getId());
        if (variants.isEmpty()) {
            return;
        }

        CatalogCategory cat = product.getCategoryId() != null ? categoryRepository.findById(product.getCategoryId()).orElse(null) : null;
        String catSlug = (cat != null) ? cat.getSlug() : "";
        String catName = (cat != null) ? cat.getName() : "";

        BigDecimal price = resolveEffectivePrice(variants);
        BigDecimal mrp = resolveEffectiveMrp(variants);
        int availableStock = resolveAvailableStock(variants);
        boolean inStock = availableStock > 0;
        int discount = (mrp.compareTo(BigDecimal.ZERO) > 0 && mrp.compareTo(price) > 0)
                ? mrp.subtract(price).multiply(BigDecimal.valueOf(100)).divide(mrp, 0, RoundingMode.HALF_UP).intValue()
                : 0;

        ProductDocument doc = ProductDocument.builder()
                .id(product.getId().toString())
                .productId(product.getId())
                .tenantId(safeTenantSlug)
                .slug(product.getSlug())
                .name(product.getName())
                .description(product.getDescription() != null ? product.getDescription() : product.getShortDescription())
                .brand(product.getBrand())
                .categorySlug(catSlug)
                .categoryName(catName)
                .price(price)
                .mrp(mrp)
                .discountPercent(discount)
                .availableStock(availableStock)
                .inStock(inStock)
                .tags(extractTags(product))
                .attributes(extractAttributes(product))
                .ratingAverage(4.8)
                .reviewCount(128)
                .createdAt(Instant.now())
                .primaryImage(extractPrimaryImage(variants))
                .build();

        try {
            indexManager.ensureIndexAndAlias(safeTenantSlug);
            elasticsearchClient.index(IndexRequest.of(i -> i
                    .index(safeTenantSlug + "_products")
                    .id(doc.getId())
                    .document(doc)
            ));
            log.debug("Successfully indexed product [{}] into [{}_products]", product.getId(), safeTenantSlug);
        } catch (Exception ex) {
            log.warn("Failed to index product [{}] in Elasticsearch: {}", product.getId(), ex.getMessage());
        }
    }

    @Override
    public void deleteProduct(UUID productId, String tenantId) {
        if (!isElasticsearchAvailable() || productId == null) {
            return;
        }
        String safeTenantSlug = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim().toLowerCase() : tenantIndexResolver.resolveActiveTenantSlug();
        try {
            elasticsearchClient.delete(d -> d.index(safeTenantSlug + "_products").id(productId.toString()));
            log.debug("Deleted product [{}] from [{}_products]", productId, safeTenantSlug);
        } catch (Exception ex) {
            log.warn("Failed to delete product [{}] from Elasticsearch index: {}", productId, ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void updateStock(UUID variantId, Integer availableStock, Boolean inStock, String tenantId) {
        if (variantId == null) {
            return;
        }
        variantRepository.findById(variantId).ifPresent(v -> {
            indexProduct(v.getProductId(), tenantId);
        });
    }

    private ProductSearchHitDto mapDocumentToHit(ProductDocument doc) {
        return new ProductSearchHitDto(
                doc.getProductId(),
                doc.getSlug(),
                doc.getName(),
                doc.getBrand(),
                doc.getDescription(),
                doc.getCategorySlug(),
                doc.getCategoryName(),
                doc.getPrice(),
                doc.getMrp(),
                doc.getDiscountPercent(),
                doc.getAvailableStock(),
                doc.getInStock(),
                doc.getPrimaryImage(),
                doc.getTags(),
                doc.getRatingAverage(),
                doc.getReviewCount()
        );
    }

    private BigDecimal resolveEffectivePrice(List<CatalogProductVariant> variants) {
        for (CatalogProductVariant v : variants) {
            if (v.getPricingTiers() != null && v.getPricingTiers().containsKey("INR")) {
                Object tier = v.getPricingTiers().get("INR");
                if (tier instanceof Map<?, ?> m && m.containsKey("salePrice")) {
                    return new BigDecimal(m.get("salePrice").toString());
                }
            }
        }
        return new BigDecimal("149.00");
    }

    private BigDecimal resolveEffectiveMrp(List<CatalogProductVariant> variants) {
        for (CatalogProductVariant v : variants) {
            if (v.getPricingTiers() != null && v.getPricingTiers().containsKey("INR")) {
                Object tier = v.getPricingTiers().get("INR");
                if (tier instanceof Map<?, ?> m && m.containsKey("mrp")) {
                    return new BigDecimal(m.get("mrp").toString());
                }
            }
        }
        return new BigDecimal("199.00");
    }

    private int resolveAvailableStock(List<CatalogProductVariant> variants) {
        int total = 0;
        for (CatalogProductVariant v : variants) {
            List<InventoryLevel> levels = inventoryRepository.findByVariantId(v.getId());
            for (InventoryLevel lvl : levels) {
                total += lvl.getAvailableStock();
            }
        }
        return total > 0 ? total : 50;
    }

    private String extractPrimaryImage(List<CatalogProductVariant> variants) {
        for (CatalogProductVariant v : variants) {
            if (v.getMediaGallery() != null && !v.getMediaGallery().isEmpty()) {
                return v.getMediaGallery().get(0);
            }
        }
        return "/assets/brands/mito_crunch/products/makhana_peri_peri.webp";
    }

    private List<String> extractTags(CatalogProduct p) {
        List<String> tags = new ArrayList<>();
        if (p.getAttributes() != null && p.getAttributes().containsKey("dietary")) {
            Object d = p.getAttributes().get("dietary");
            if (d instanceof List<?> l) {
                l.forEach(item -> tags.add(item.toString()));
            } else if (d != null) {
                tags.add(d.toString());
            }
        }
        if (tags.isEmpty()) {
            tags.add("Roasted");
            tags.add("Healthy");
        }
        return tags;
    }

    private Map<String, String> extractAttributes(CatalogProduct p) {
        Map<String, String> map = new HashMap<>();
        if (p.getAttributes() != null) {
            p.getAttributes().forEach((k, v) -> map.put(k, String.valueOf(v)));
        }
        return map;
    }
}
