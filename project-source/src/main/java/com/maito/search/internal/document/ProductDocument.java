package com.maito.search.internal.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "#{@tenantIndexResolver.resolveProductIndex()}")
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private UUID productId;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Keyword)
    private String slug;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "edge_ngram_analyzer", searchAnalyzer = "edge_ngram_search_analyzer"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword),
            @InnerField(suffix = "standard", type = FieldType.Text, analyzer = "standard")
        }
    )
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @MultiField(
        mainField = @Field(type = FieldType.Keyword),
        otherFields = {
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String brand;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private String categorySlug;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Double)
    private BigDecimal mrp;

    @Field(type = FieldType.Integer)
    private Integer discountPercent;

    @Field(type = FieldType.Integer)
    private Integer availableStock;

    @Field(type = FieldType.Boolean)
    private Boolean inStock;

    @Field(type = FieldType.Keyword)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Field(type = FieldType.Flattened)
    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();

    @Field(type = FieldType.Double)
    @Builder.Default
    private Double ratingAverage = 4.5;

    @Field(type = FieldType.Integer)
    @Builder.Default
    private Integer reviewCount = 0;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Keyword)
    private String primaryImage;
}
