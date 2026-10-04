package com.maito.cms.internal.repository;

import com.maito.cms.internal.domain.CmsTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CmsThemeRepository extends JpaRepository<CmsTheme, UUID> {
    Optional<CmsTheme> findFirstByIsActiveTrue();
}
