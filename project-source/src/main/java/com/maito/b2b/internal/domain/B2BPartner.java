package com.maito.b2b.internal.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "b2b_partners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_profile_id", nullable = false, unique = true)
    private UUID customerProfileId;

    @Column(name = "company_legal_name", nullable = false)
    private String companyLegalName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "gstin", nullable = false, unique = true, length = 15)
    private String gstin;

    @Column(name = "pan", nullable = false, length = 10)
    private String pan;

    @Column(name = "fssai_license_number", length = 32)
    private String fssaiLicenseNumber;

    @Column(name = "verification_status", nullable = false, length = 32)
    @Builder.Default
    private String verificationStatus = "PENDING_VERIFICATION";

    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "used_credit", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal usedCredit = BigDecimal.ZERO;

    @Column(name = "payment_terms_days", nullable = false)
    @Builder.Default
    private Integer paymentTermsDays = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billing_address", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> billingAddress = new HashMap<>();

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

    public BigDecimal getAvailableCredit() {
        if (creditLimit == null) return BigDecimal.ZERO;
        BigDecimal used = usedCredit != null ? usedCredit : BigDecimal.ZERO;
        return creditLimit.subtract(used).max(BigDecimal.ZERO);
    }
}
