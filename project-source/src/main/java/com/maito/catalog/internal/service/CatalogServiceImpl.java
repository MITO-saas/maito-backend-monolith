package com.maito.catalog.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.catalog.api.dto.CategoryDto;
import com.maito.catalog.api.dto.CreateProductCommand;
import com.maito.catalog.api.dto.CreateVariantCommand;
import com.maito.catalog.api.dto.PriceTierDto;
import com.maito.catalog.api.dto.ProductDetailResponse;
import com.maito.catalog.api.dto.ProductSummaryDto;
import com.maito.catalog.api.dto.UpdateProductCommand;
import com.maito.catalog.api.dto.VariantDto;
import com.maito.catalog.api.service.CatalogService;
import com.maito.catalog.internal.domain.CatalogCategory;
import com.maito.catalog.internal.domain.CatalogProduct;
import com.maito.catalog.internal.domain.CatalogProductVariant;
import com.maito.catalog.internal.domain.InventoryLevel;
import com.maito.catalog.internal.repository.CatalogCategoryRepository;
import com.maito.catalog.internal.repository.CatalogProductRepository;
import com.maito.catalog.internal.repository.CatalogProductVariantRepository;
import com.maito.catalog.internal.repository.InventoryLevelRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CatalogServiceImpl implements CatalogService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final CatalogCategoryRepository categoryRepository;
    private final CatalogProductRepository productRepository;
    private final CatalogProductVariantRepository variantRepository;
    private final InventoryLevelRepository inventoryRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CatalogServiceImpl(
            CatalogCategoryRepository categoryRepository,
            CatalogProductRepository productRepository,
            CatalogProductVariantRepository variantRepository,
            InventoryLevelRepository inventoryRepository,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.inventoryRepository = inventoryRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> getCategoryTree() {
        List<CatalogCategory> allCategories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        Map<UUID, List<CatalogCategory>> childrenByParent = allCategories.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(CatalogCategory::getParentId));

        return allCategories.stream()
                .filter(c -> c.getParentId() == null)
                .map(root -> buildCategoryNode(root, childrenByParent))
                .toList();
    }

    private CategoryDto buildCategoryNode(CatalogCategory category, Map<UUID, List<CatalogCategory>> childrenByParent) {
        List<CatalogCategory> children = childrenByParent.getOrDefault(category.getId(), List.of());
        List<CategoryDto> childDtos = children.stream()
                .sorted(Comparator.comparing(CatalogCategory::getDisplayOrder))
                .map(child -> buildCategoryNode(child, childrenByParent))
                .toList();

        return new CategoryDto(
                category.getId(),
                category.getSlug(),
                category.getName(),
                category.getDescription(),
                category.getParentId(),
                category.getMaterializedPath(),
                category.getDisplayOrder(),
                category.getIsActive(),
                childDtos
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryDto> searchProducts(UUID categoryId, String categorySlug, String currency,
                                                  BigDecimal minPrice, BigDecimal maxPrice, String dietary,
                                                  int page, int size) {
        UUID effectiveCategoryId = categoryId;
        if (effectiveCategoryId == null && categorySlug != null && !categorySlug.isBlank()) {
            effectiveCategoryId = categoryRepository.findBySlug(categorySlug.trim().toLowerCase())
                    .map(CatalogCategory::getId)
                    .orElse(null);
        }

        List<CatalogProduct> products;
        if (effectiveCategoryId != null) {
            products = productRepository.findByCategoryIdAndIsPublishedTrue(effectiveCategoryId);
        } else {
            products = productRepository.findByIsPublishedTrue();
        }

        String targetCurrency = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "INR";
        Map<UUID, String> categorySlugMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(CatalogCategory::getId, CatalogCategory::getSlug, (a, b) -> a));

        List<ProductSummaryDto> summaries = new ArrayList<>();

        for (CatalogProduct p : products) {
            // Check dietary attribute filter if specified
            if (dietary != null && !dietary.isBlank()) {
                if (!matchesDietary(p.getAttributes(), dietary.trim())) {
                    continue;
                }
            }

            List<CatalogProductVariant> variants = variantRepository.findByProductIdAndIsActiveTrue(p.getId());
            if (variants.isEmpty()) {
                continue;
            }

            PriceTierDto priceRange = calculatePriceRange(variants, targetCurrency);

            // Price filtering
            if (minPrice != null && priceRange.salePrice() != null && priceRange.salePrice().compareTo(minPrice) < 0) {
                continue;
            }
            if (maxPrice != null && priceRange.salePrice() != null && priceRange.salePrice().compareTo(maxPrice) > 0) {
                continue;
            }

            String primaryImage = extractPrimaryImage(variants);
            List<String> dietaryTags = extractDietaryTags(p.getAttributes());
            String catSlug = p.getCategoryId() != null ? categorySlugMap.getOrDefault(p.getCategoryId(), "") : "";

            summaries.add(new ProductSummaryDto(
                    p.getId(),
                    p.getSlug(),
                    p.getName(),
                    p.getBrand(),
                    p.getShortDescription(),
                    p.getCategoryId(),
                    catSlug,
                    priceRange,
                    primaryImage,
                    dietaryTags,
                    p.getIsPublished()
            ));
        }

        // Apply pagination
        int fromIndex = Math.min(page * size, summaries.size());
        int toIndex = Math.min(fromIndex + size, summaries.size());
        return summaries.subList(fromIndex, toIndex);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlug(String slug, String currencyCode) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        String safeSlug = slug != null ? slug.trim().toLowerCase() : "";
        String safeCurrency = (currencyCode != null && !currencyCode.isBlank()) ? currencyCode.trim().toUpperCase() : "INR";
        String cacheKey = "tenant:" + tenantId + ":catalog:product:" + safeSlug + ":" + safeCurrency;

        // 1. Redis Cache Read
        if (redisTemplate != null) {
            try {
                String cachedJson = redisTemplate.opsForValue().get(cacheKey);
                if (cachedJson != null && !cachedJson.isBlank()) {
                    log.debug("Cache HIT for product detail: [{}]", cacheKey);
                    return objectMapper.readValue(cachedJson, ProductDetailResponse.class);
                }
            } catch (Exception ex) {
                log.warn("Redis read failed for [{}]. Proceeding directly to database fallback: {}", cacheKey, ex.getMessage());
            }
        }

        // 2. Database Fallback
        log.debug("Cache MISS for product detail: [{}]. Fetching from database...", cacheKey);
        CatalogProduct product = productRepository.findBySlugAndIsPublishedTrue(safeSlug)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found: " + safeSlug));

        String categorySlug = "";
        if (product.getCategoryId() != null) {
            categorySlug = categoryRepository.findById(product.getCategoryId())
                    .map(CatalogCategory::getSlug)
                    .orElse("");
        }

        List<CatalogProductVariant> variants = variantRepository.findByProductIdAndIsActiveTrue(product.getId());
        List<VariantDto> variantDtos = new ArrayList<>();

        for (CatalogProductVariant v : variants) {
            List<InventoryLevel> inventoryList = inventoryRepository.findByVariantId(v.getId());
            int availableStock = inventoryList.stream().mapToInt(InventoryLevel::getAvailableStock).sum();
            int reorderThreshold = inventoryList.stream().mapToInt(InventoryLevel::getReorderThreshold).max().orElse(10);

            String stockStatus;
            if (availableStock <= 0) {
                stockStatus = "OUT_OF_STOCK";
            } else if (availableStock <= reorderThreshold) {
                stockStatus = "LOW_STOCK";
            } else {
                stockStatus = "IN_STOCK";
            }

            Map<String, PriceTierDto> parsedPricingTiers = parsePricingTiers(v.getPricingTiers());
            PriceTierDto activePrice = parsedPricingTiers.getOrDefault(safeCurrency,
                    parsedPricingTiers.getOrDefault("INR", new PriceTierDto(BigDecimal.ZERO, BigDecimal.ZERO)));

            variantDtos.add(new VariantDto(
                    v.getId(),
                    v.getProductId(),
                    v.getSku(),
                    v.getBarcode(),
                    v.getWeightGrams(),
                    v.getVariantAttributes(),
                    parsedPricingTiers,
                    activePrice,
                    v.getMediaGallery(),
                    v.getIsActive(),
                    availableStock,
                    stockStatus
            ));
        }

        ProductDetailResponse response = new ProductDetailResponse(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getBrand(),
                product.getShortDescription(),
                product.getDescription(),
                product.getCategoryId(),
                categorySlug,
                product.getHsnCode(),
                product.getTaxRatePercent(),
                product.getAttributes(),
                product.getIsPublished(),
                safeCurrency,
                variantDtos
        );

        // 3. Redis Cache Write (TTL: 10 min)
        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(response);
                redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
                log.debug("Populated Redis cache for product detail: [{}]", cacheKey);
            } catch (Exception ex) {
                log.warn("Redis write failed for [{}]: {}", cacheKey, ex.getMessage());
            }
        }

        return response;
    }

    @Override
    @Transactional
    public ProductDetailResponse createProduct(CreateProductCommand command) {
        String safeSlug = command.slug().trim().toLowerCase();
        if (productRepository.findBySlug(safeSlug).isPresent()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Product slug already exists: " + safeSlug);
        }

        CatalogProduct product = CatalogProduct.builder()
                .slug(safeSlug)
                .name(command.name().trim())
                .brand(command.brand().trim())
                .shortDescription(command.shortDescription())
                .description(command.description())
                .categoryId(command.categoryId())
                .hsnCode(command.hsnCode() != null ? command.hsnCode() : "19041090")
                .taxRatePercent(command.taxRatePercent() != null ? command.taxRatePercent() : new BigDecimal("5.00"))
                .attributes(command.attributes() != null ? command.attributes() : Map.of())
                .isPublished(command.isPublished() != null ? command.isPublished() : true)
                .build();

        CatalogProduct savedProduct = productRepository.save(product);

        if (command.variants() != null) {
            for (CreateVariantCommand vc : command.variants()) {
                CatalogProductVariant variant = CatalogProductVariant.builder()
                        .productId(savedProduct.getId())
                        .sku(vc.sku().trim().toUpperCase())
                        .barcode(vc.barcode())
                        .weightGrams(vc.weightGrams() != null ? vc.weightGrams() : 100)
                        .variantAttributes(vc.variantAttributes() != null ? vc.variantAttributes() : Map.of())
                        .pricingTiers(serializePricingTiers(vc.pricingTiers()))
                        .mediaGallery(vc.mediaGallery() != null ? vc.mediaGallery() : List.of())
                        .isActive(vc.isActive() != null ? vc.isActive() : true)
                        .build();

                CatalogProductVariant savedVariant = variantRepository.save(variant);

                int stock = vc.initialStock() != null ? vc.initialStock() : 0;
                String wh = (vc.warehouseCode() != null && !vc.warehouseCode().isBlank()) ? vc.warehouseCode() : "DEFAULT_WH";

                InventoryLevel level = InventoryLevel.builder()
                        .variantId(savedVariant.getId())
                        .warehouseCode(wh)
                        .availableStock(stock)
                        .reservedStock(0)
                        .reorderThreshold(10)
                        .build();

                inventoryRepository.save(level);
            }
        }

        evictProductCache(TenantContextHolder.getTenantId(), safeSlug);
        return getProductBySlug(safeSlug, "INR");
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(UUID productId, UpdateProductCommand command) {
        CatalogProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found: " + productId));

        product.setName(command.name().trim());
        product.setBrand(command.brand().trim());
        product.setShortDescription(command.shortDescription());
        product.setDescription(command.description());
        if (command.categoryId() != null) {
            product.setCategoryId(command.categoryId());
        }
        if (command.hsnCode() != null) {
            product.setHsnCode(command.hsnCode());
        }
        if (command.taxRatePercent() != null) {
            product.setTaxRatePercent(command.taxRatePercent());
        }
        if (command.attributes() != null) {
            product.setAttributes(command.attributes());
        }
        if (command.isPublished() != null) {
            product.setIsPublished(command.isPublished());
        }

        CatalogProduct saved = productRepository.save(product);
        evictProductCache(TenantContextHolder.getTenantId(), saved.getSlug());
        return getProductBySlug(saved.getSlug(), "INR");
    }

    @Override
    public void evictProductCache(String tenantId, String slug) {
        if (redisTemplate == null || tenantId == null || slug == null) {
            return;
        }

        try {
            String pattern = "tenant:" + tenantId + ":catalog:product:" + slug.trim().toLowerCase() + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} catalog product cache keys matching pattern [{}]", keys.size(), pattern);
            }
        } catch (Exception ex) {
            log.warn("Redis unavailable during product cache eviction for tenant [{}] slug [{}]: {}",
                    tenantId, slug, ex.getMessage());
        }
    }

    private boolean matchesDietary(Map<String, Object> attributes, String dietary) {
        if (attributes == null || !attributes.containsKey("dietary")) {
            return false;
        }
        Object dietaryObj = attributes.get("dietary");
        if (dietaryObj instanceof List<?> list) {
            return list.stream()
                    .anyMatch(item -> String.valueOf(item).equalsIgnoreCase(dietary));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractDietaryTags(Map<String, Object> attributes) {
        if (attributes != null && attributes.containsKey("dietary")) {
            Object obj = attributes.get("dietary");
            if (obj instanceof List<?> l) {
                return l.stream().map(String::valueOf).toList();
            }
        }
        return List.of();
    }

    private String extractPrimaryImage(List<CatalogProductVariant> variants) {
        for (CatalogProductVariant v : variants) {
            if (v.getMediaGallery() != null && !v.getMediaGallery().isEmpty()) {
                return v.getMediaGallery().get(0);
            }
        }
        return "";
    }

    private PriceTierDto calculatePriceRange(List<CatalogProductVariant> variants, String currency) {
        BigDecimal minSalePrice = null;
        BigDecimal correspondingMrp = null;

        for (CatalogProductVariant v : variants) {
            Map<String, PriceTierDto> tiers = parsePricingTiers(v.getPricingTiers());
            PriceTierDto tier = tiers.getOrDefault(currency, tiers.get("INR"));
            if (tier != null && tier.salePrice() != null) {
                if (minSalePrice == null || tier.salePrice().compareTo(minSalePrice) < 0) {
                    minSalePrice = tier.salePrice();
                    correspondingMrp = tier.mrp();
                }
            }
        }

        return new PriceTierDto(
                correspondingMrp != null ? correspondingMrp : BigDecimal.ZERO,
                minSalePrice != null ? minSalePrice : BigDecimal.ZERO
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, PriceTierDto> parsePricingTiers(Map<String, Object> raw) {
        Map<String, PriceTierDto> result = new HashMap<>();
        if (raw == null) {
            return result;
        }

        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String cur = entry.getKey();
            Object val = entry.getValue();
            if (val instanceof Map<?, ?> m) {
                BigDecimal mrp = parseBigDecimal(m.get("mrp"));
                BigDecimal salePrice = parseBigDecimal(m.get("salePrice"));
                result.put(cur, new PriceTierDto(mrp, salePrice));
            } else if (val instanceof PriceTierDto pt) {
                result.put(cur, pt);
            }
        }
        return result;
    }

    private Map<String, Object> serializePricingTiers(Map<String, PriceTierDto> tiers) {
        Map<String, Object> result = new HashMap<>();
        if (tiers == null) {
            return result;
        }
        for (Map.Entry<String, PriceTierDto> e : tiers.entrySet()) {
            Map<String, Object> inner = new HashMap<>();
            inner.put("mrp", e.getValue().mrp());
            inner.put("salePrice", e.getValue().salePrice());
            result.put(e.getKey(), inner);
        }
        return result;
    }

    private BigDecimal parseBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        try {
            return new BigDecimal(String.valueOf(obj));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
