package com.maito.cart.internal.domain;

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

import java.util.UUID;

@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "guest_cart_id", length = 128)
    private String guestCartId;

    @Column(name = "customer_profile_id")
    private UUID customerProfileId;

    @Column(name = "currency_code", length = 8, nullable = false)
    @Builder.Default
    private String currencyCode = "INR";

    @Column(name = "applied_coupon_code", length = 64)
    private String appliedCouponCode;
}
