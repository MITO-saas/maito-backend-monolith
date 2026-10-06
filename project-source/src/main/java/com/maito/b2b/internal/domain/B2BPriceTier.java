package com.maito.b2b.internal.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "b2b_price_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BPriceTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "min_quantity", nullable = false)
    private Integer minQuantity;

    @Column(name = "wholesale_unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal wholesaleUnitPrice;

    @Column(name = "currency_code", nullable = false, length = 8)
    @Builder.Default
    private String currencyCode = "INR";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
