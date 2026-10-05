package com.maito.fulfillment.internal.repository;

import com.maito.fulfillment.internal.domain.ShipmentCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentCheckpointRepository extends JpaRepository<ShipmentCheckpoint, UUID> {
    List<ShipmentCheckpoint> findByShipmentIdOrderByEventTimestampAsc(UUID shipmentId);
}
