package com.maito.catalog.internal.repository;

import com.maito.catalog.internal.domain.CatalogProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CatalogProductVariantRepository extends JpaRepository<CatalogProductVariant, UUID> {
    List<CatalogProductVariant> findByProductIdAndIsActiveTrue(UUID productId);
    List<CatalogProductVariant> findByProductId(UUID productId);
    Optional<CatalogProductVariant> findBySku(String sku);
}
