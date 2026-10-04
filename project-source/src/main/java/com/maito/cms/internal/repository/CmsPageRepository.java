package com.maito.cms.internal.repository;

import com.maito.cms.internal.domain.CmsPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CmsPageRepository extends JpaRepository<CmsPage, UUID> {
    Optional<CmsPage> findByPageSlugAndIsPublishedTrue(String pageSlug);
    Optional<CmsPage> findByPageSlug(String pageSlug);
}
