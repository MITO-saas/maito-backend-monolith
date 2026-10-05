package com.maito.fulfillment.internal.domain;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipment_checkpoints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentCheckpoint extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "checkpoint_status", nullable = false, length = 32)
    private String checkpointStatus;

    @Column(name = "location_hub", nullable = false, length = 128)
    private String locationHub;

    @Column(name = "status_description", nullable = false, length = 255)
    private String statusDescription;

    @Column(name = "event_timestamp", nullable = false)
    @Builder.Default
    private Instant eventTimestamp = Instant.now();
}
