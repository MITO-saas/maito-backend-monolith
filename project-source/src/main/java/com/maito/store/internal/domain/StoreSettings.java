package com.maito.store.internal.domain;

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
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSettings extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(name = "support_email", nullable = false)
    private String supportEmail;

    @Column(name = "support_phone")
    private String supportPhone;

    @Column(name = "base_currency", length = 8, nullable = false)
    @Builder.Default
    private String baseCurrency = "INR";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_currencies", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> supportedCurrencies = new ArrayList<>(List.of("INR", "USD"));

    @Column(name = "timezone", length = 64, nullable = false)
    @Builder.Default
    private String timezone = "Asia/Kolkata";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "commercial_settings", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> commercialSettings = new HashMap<>();
}
