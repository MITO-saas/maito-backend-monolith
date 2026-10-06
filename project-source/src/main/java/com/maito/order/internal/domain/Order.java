package com.maito.order.internal.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 64)
    private String orderNumber;

    @Column(name = "customer_profile_id", nullable = false)
    private UUID customerProfileId;

    @Column(name = "order_status", nullable = false, length = 32)
    @Builder.Default
    private String orderStatus = "PENDING_PAYMENT";

    @Column(name = "currency_code", nullable = false, length = 8)
    @Builder.Default
    private String currencyCode = "INR";

    @Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "shipping_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> shippingAddressSnapshot;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column(name = "payment_status", nullable = false, length = 32)
    @Builder.Default
    private String paymentStatus = "UNPAID";

    @Column(name = "coins_redeemed", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal coinsRedeemed = BigDecimal.ZERO;

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
