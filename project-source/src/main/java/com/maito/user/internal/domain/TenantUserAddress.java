package com.maito.user.internal.domain;

import com.maito.shared.domain.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tenant_user_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUserAddress extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "address_type", nullable = false, length = 32)
    @Builder.Default
    private String addressType = "SHIPPING";

    @Column(name = "recipient_name", nullable = false, length = 128)
    private String recipientName;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 128)
    private String city;

    @Column(name = "state", nullable = false, length = 128)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 32)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 8)
    @Builder.Default
    private String countryCode = "IN";

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;
}
