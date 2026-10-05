package com.maito.fulfillment.internal.domain;

import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.api.dto.ShipmentStatus;
import com.maito.shared.domain.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "shipment_number", nullable = false, unique = true, length = 64)
    private String shipmentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier_type", nullable = false, length = 64)
    private CarrierType carrierType;

    @Column(name = "tracking_number", nullable = false, length = 128)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private ShipmentStatus status = ShipmentStatus.MANIFESTED;

    @Column(name = "assigned_rider_name", length = 128)
    private String assignedRiderName;

    @Column(name = "assigned_rider_phone", length = 32)
    private String assignedRiderPhone;

    @Column(name = "delivery_otp", length = 8)
    private String deliveryOtp;

    @Column(name = "total_weight_grams", nullable = false)
    @Builder.Default
    private Integer totalWeightGrams = 500;

    @Column(name = "volumetric_weight_grams", nullable = false)
    @Builder.Default
    private Integer volumetricWeightGrams = 500;

    @Column(name = "shipping_label_url", length = 512)
    private String shippingLabelUrl;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
