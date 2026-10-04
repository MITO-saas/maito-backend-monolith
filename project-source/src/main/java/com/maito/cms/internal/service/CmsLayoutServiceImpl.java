package com.maito.cms.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.cms.api.dto.CreatePageCommand;
import com.maito.cms.api.dto.PageLayoutResponse;
import com.maito.cms.api.dto.PublishPageCommand;
import com.maito.cms.api.dto.SectionWidgetDto;
import com.maito.cms.api.dto.ThemeTokensDto;
import com.maito.cms.api.dto.UpdateSectionCommand;
import com.maito.cms.api.service.CmsLayoutService;
import com.maito.cms.internal.domain.CmsPage;
import com.maito.cms.internal.domain.CmsSection;
import com.maito.cms.internal.domain.CmsTheme;
import com.maito.cms.internal.repository.CmsPageRepository;
import com.maito.cms.internal.repository.CmsSectionRepository;
import com.maito.cms.internal.repository.CmsThemeRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CmsLayoutServiceImpl implements CmsLayoutService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private final CmsPageRepository pageRepository;
    private final CmsSectionRepository sectionRepository;
    private final CmsThemeRepository themeRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CmsLayoutServiceImpl(
            CmsPageRepository pageRepository,
            CmsSectionRepository sectionRepository,
            CmsThemeRepository themeRepository,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.pageRepository = pageRepository;
        this.sectionRepository = sectionRepository;
        this.themeRepository = themeRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageLayoutResponse getPublishedLayout(String pageSlug, String locale) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        String safeSlug = pageSlug != null ? pageSlug.trim().toLowerCase() : "home";
        String safeLocale = locale != null && !locale.isBlank() ? locale.trim() : "default";
        String cacheKey = "tenant:" + tenantId + ":cms:" + safeSlug + ":" + safeLocale;

        // 1. Cache-Aside: Check Redis cache
        if (redisTemplate != null) {
            try {
                String cachedJson = redisTemplate.opsForValue().get(cacheKey);
                if (cachedJson != null && !cachedJson.isBlank()) {
                    log.debug("Cache HIT for CMS layout: [{}]", cacheKey);
                    return objectMapper.readValue(cachedJson, PageLayoutResponse.class);
                }
            } catch (Exception ex) {
                log.warn("Redis unavailable for cache read on [{}]. Proceeding directly to database fallback: {}",
                        cacheKey, ex.getMessage());
            }
        }

        // 2. Database Fallback: Query tenant isolated database
        log.debug("Cache MISS for CMS layout: [{}]. Fetching from tenant database...", cacheKey);
        CmsPage page = pageRepository.findByPageSlugAndIsPublishedTrue(safeSlug)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Published CMS page not found: " + safeSlug
                ));

        List<CmsSection> sections = sectionRepository.findByPageIdAndIsActiveTrueOrderByDisplayOrderAsc(page.getId());

        Instant now = Instant.now();
        List<SectionWidgetDto> visibleWidgets = sections.stream()
                .filter(section -> isSectionVisible(section, now))
                .map(section -> new SectionWidgetDto(
                        section.getId(),
                        section.getComponentType(),
                        section.getDisplayOrder(),
                        section.getContentPayload()
                ))
                .toList();

        ThemeTokensDto themeDto = themeRepository.findFirstByIsActiveTrue()
                .map(theme -> new ThemeTokensDto(theme.getThemeName(), theme.getBrandTokens()))
                .orElseGet(() -> new ThemeTokensDto("DEFAULT_THEME", getDefaultBrandTokens()));

        PageLayoutResponse response = new PageLayoutResponse(
                page.getPageSlug(),
                page.getTitle(),
                page.getSeoMetadata(),
                themeDto,
                visibleWidgets
        );

        // 3. Cache-Aside: Populate Redis cache with 15 min TTL
        if (redisTemplate != null) {
            try {
                String jsonPayload = objectMapper.writeValueAsString(response);
                redisTemplate.opsForValue().set(cacheKey, jsonPayload, CACHE_TTL);
                log.debug("Cached compiled CMS layout for key: [{}]", cacheKey);
            } catch (Exception ex) {
                log.warn("Failed to populate Redis cache for [{}]: {}", cacheKey, ex.getMessage());
            }
        }

        return response;
    }

    @Override
    @Transactional
    public CmsPage createOrUpdatePage(CreatePageCommand command) {
        String safeSlug = command.pageSlug().trim().toLowerCase();
        CmsPage page = pageRepository.findByPageSlug(safeSlug)
                .orElseGet(() -> CmsPage.builder().pageSlug(safeSlug).build());

        page.setTitle(command.title().trim());
        if (command.seoMetadata() != null) {
            page.setSeoMetadata(command.seoMetadata());
        }
        if (command.isPublished() != null) {
            page.setIsPublished(command.isPublished());
        }

        CmsPage saved = pageRepository.save(page);
        evictLayoutCache(TenantContextHolder.getTenantId(), safeSlug);
        return saved;
    }

    @Override
    @Transactional
    public CmsSection updateSection(UUID sectionId, UpdateSectionCommand command) {
        CmsSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "CMS Section not found: " + sectionId));

        section.setComponentType(command.componentType().trim());
        section.setDisplayOrder(command.displayOrder());
        if (command.isActive() != null) {
            section.setIsActive(command.isActive());
        }
        if (command.visibilityRules() != null) {
            section.setVisibilityRules(command.visibilityRules());
        }
        section.setContentPayload(command.contentPayload());

        CmsSection saved = sectionRepository.save(section);

        pageRepository.findById(saved.getPageId()).ifPresent(p ->
                evictLayoutCache(TenantContextHolder.getTenantId(), p.getPageSlug())
        );

        return saved;
    }

    @Override
    @Transactional
    public void publishPage(String pageSlug, PublishPageCommand command) {
        String safeSlug = pageSlug.trim().toLowerCase();
        CmsPage page = pageRepository.findByPageSlug(safeSlug)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "CMS Page not found: " + safeSlug));

        page.setIsPublished(command.isPublished());
        pageRepository.save(page);
        evictLayoutCache(TenantContextHolder.getTenantId(), safeSlug);
    }

    @Override
    public void evictLayoutCache(String tenantId, String pageSlug) {
        if (redisTemplate == null || tenantId == null || pageSlug == null) {
            return;
        }

        try {
            String pattern = "tenant:" + tenantId + ":cms:" + pageSlug.trim().toLowerCase() + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} CMS layout cache keys matching pattern [{}]", keys.size(), pattern);
            }
        } catch (Exception ex) {
            log.warn("Redis unavailable during cache eviction for tenant [{}] slug [{}]: {}",
                    tenantId, pageSlug, ex.getMessage());
        }
    }

    private boolean isSectionVisible(CmsSection section, Instant now) {
        Map<String, Object> rules = section.getVisibilityRules();
        if (rules == null || rules.isEmpty()) {
            return true;
        }

        try {
            if (rules.containsKey("startDate") && rules.get("startDate") != null) {
                Instant start = Instant.parse(String.valueOf(rules.get("startDate")));
                if (now.isBefore(start)) {
                    return false;
                }
            }
            if (rules.containsKey("endDate") && rules.get("endDate") != null) {
                Instant end = Instant.parse(String.valueOf(rules.get("endDate")));
                if (now.isAfter(end)) {
                    return false;
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing visibility dates for section [{}]: {}", section.getId(), e.getMessage());
        }

        return true;
    }

    private Map<String, Object> getDefaultBrandTokens() {
        Map<String, Object> tokens = new HashMap<>();
        tokens.put("primaryColor", "#D97706");
        tokens.put("fontHeadings", "Cabinet Grotesk");
        tokens.put("fontBody", "Inter");
        tokens.put("borderRadiusPx", 8);
        return tokens;
    }
}
