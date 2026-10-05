package com.maito.catalog.internal.repository;

import com.maito.catalog.internal.domain.CatalogProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CatalogProductRepository extends JpaRepository<CatalogProduct, UUID> {
    Optional<CatalogProduct> findBySlug(String slug);
    Optional<CatalogProduct> findBySlugAndIsPublishedTrue(String slug);
    List<CatalogProduct> findByIsPublishedTrue();
    List<CatalogProduct> findByCategoryIdAndIsPublishedTrue(UUID categoryId);
}
