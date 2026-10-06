package com.maito.b2b.internal.repository;

import com.maito.b2b.internal.domain.B2BPriceTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2BPriceTierRepository extends JpaRepository<B2BPriceTier, UUID> {
    List<B2BPriceTier> findByVariantIdAndIsActiveTrueOrderByMinQuantityAsc(UUID variantId);
    List<B2BPriceTier> findByIsActiveTrueOrderByMinQuantityAsc();

    @Query("SELECT t FROM B2BPriceTier t WHERE t.variantId = :variantId AND t.isActive = true AND t.minQuantity <= :qty ORDER BY t.minQuantity DESC LIMIT 1")
    Optional<B2BPriceTier> findApplicableTier(@Param("variantId") UUID variantId, @Param("qty") int qty);

    Optional<B2BPriceTier> findByVariantIdAndMinQuantity(UUID variantId, int minQuantity);
}
