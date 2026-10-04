package com.maito.cms.internal.repository;

import com.maito.cms.internal.domain.CmsSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CmsSectionRepository extends JpaRepository<CmsSection, UUID> {
    List<CmsSection> findByPageIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID pageId);
    List<CmsSection> findByPageIdOrderByDisplayOrderAsc(UUID pageId);
}
