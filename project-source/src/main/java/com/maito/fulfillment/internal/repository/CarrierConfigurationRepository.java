package com.maito.fulfillment.internal.repository;

import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.internal.domain.CarrierConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarrierConfigurationRepository extends JpaRepository<CarrierConfiguration, UUID> {
    Optional<CarrierConfiguration> findByCarrierType(CarrierType carrierType);
}
