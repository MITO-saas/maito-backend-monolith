package com.maito.catalog.internal.repository;

import com.maito.catalog.internal.domain.CatalogCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CatalogCategoryRepository extends JpaRepository<CatalogCategory, UUID> {
    List<CatalogCategory> findByIsActiveTrueOrderByDisplayOrderAsc();
    Optional<CatalogCategory> findBySlug(String slug);
}
