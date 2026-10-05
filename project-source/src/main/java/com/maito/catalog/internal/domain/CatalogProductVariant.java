package com.maito.catalog.internal.domain;

import com.maito.shared.domain.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "catalog_product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogProductVariant extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sku", length = 64, nullable = false, unique = true)
    private String sku;

    @Column(name = "barcode", length = 64)
    private String barcode;

    @Column(name = "weight_grams", nullable = false)
    @Builder.Default
    private Integer weightGrams = 100;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variant_attributes", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> variantAttributes = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_tiers", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> pricingTiers = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_gallery", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> mediaGallery = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
