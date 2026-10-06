package com.maito.payment.internal.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "gateway_provider", nullable = false, length = 32)
    @Builder.Default
    private String gatewayProvider = "RAZORPAY";

    @Column(name = "gateway_order_id", nullable = false, length = 128)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 128)
    private String gatewayPaymentId;

    @Column(name = "gateway_signature", length = 256)
    private String gatewaySignature;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 8)
    @Builder.Default
    private String currencyCode = "INR";

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "INITIATED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    @Builder.Default
    private String rawResponse = "{}";

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