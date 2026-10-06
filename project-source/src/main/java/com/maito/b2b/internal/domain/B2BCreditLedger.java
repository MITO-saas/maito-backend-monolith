package com.maito.b2b.internal.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "b2b_credit_ledgers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BCreditLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "entry_type", nullable = false, length = 32)
    private String entryType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "used_credit_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal usedCreditAfter;

    @Column(name = "reference_id", nullable = false, length = 128)
    private String referenceId;

    @Column(name = "notes", nullable = false, length = 255)
    private String notes;

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
