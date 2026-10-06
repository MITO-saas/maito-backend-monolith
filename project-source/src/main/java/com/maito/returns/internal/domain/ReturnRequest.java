package com.maito.returns.internal.domain;

import com.maito.shared.domain.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequest extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "return_number", nullable = false, unique = true, length = 64)
    private String returnNumber;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_profile_id", nullable = false)
    private UUID customerProfileId;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "REQUESTED";

    @Column(name = "reason_category", nullable = false, length = 64)
    private String reasonCategory;

    @Column(name = "customer_notes", columnDefinition = "TEXT")
    private String customerNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proof_media_urls", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> proofMediaUrls = new ArrayList<>();

    @Column(name = "qc_notes", columnDefinition = "TEXT")
    private String qcNotes;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "refund_mode", length = 32)
    @Builder.Default
    private String refundMode = "WALLET";

    @Column(name = "settled_at")
    private Instant settledAt;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ReturnItem> items = new ArrayList<>();

    public void addItem(ReturnItem item) {
        items.add(item);
        item.setReturnRequest(this);
    }
}