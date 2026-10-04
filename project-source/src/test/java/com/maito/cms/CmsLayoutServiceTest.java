package com.maito.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.cms.api.dto.PageLayoutResponse;
import com.maito.cms.internal.domain.CmsPage;
import com.maito.cms.internal.domain.CmsSection;
import com.maito.cms.internal.domain.CmsTheme;
import com.maito.cms.internal.repository.CmsPageRepository;
import com.maito.cms.internal.repository.CmsSectionRepository;
import com.maito.cms.internal.repository.CmsThemeRepository;
import com.maito.cms.internal.service.CmsLayoutServiceImpl;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CmsLayoutServiceTest {

    @Mock
    private CmsPageRepository pageRepository;

    @Mock
    private CmsSectionRepository sectionRepository;

    @Mock
    private CmsThemeRepository themeRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private CmsLayoutServiceImpl layoutService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        layoutService = new CmsLayoutServiceImpl(
                pageRepository,
                sectionRepository,
                themeRepository,
                redisTemplate,
                objectMapper
        );
        TenantContextHolder.set(new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Successfully constructs PageLayoutResponse from DB on cache miss")
    void shouldConstructPageLayoutOnCacheMiss() {
        UUID pageId = UUID.randomUUID();
        CmsPage page = CmsPage.builder()
                .id(pageId)
                .pageSlug("home")
                .title("Mito Crunch Home")
                .seoMetadata(Map.of("metaTitle", "Mito Crunch"))
                .isPublished(true)
                .build();

        CmsSection section = CmsSection.builder()
                .id(UUID.randomUUID())
                .pageId(pageId)
                .componentType("PROMO_STRIP")
                .displayOrder(1)
                .isActive(true)
                .visibilityRules(Map.of())
                .contentPayload(Map.of("text", "Free Shipping"))
                .build();

        CmsTheme theme = CmsTheme.builder()
                .id(UUID.randomUUID())
                .themeName("DEFAULT_THEME")
                .brandTokens(Map.of("primaryColor", "#D97706"))
                .isActive(true)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // Cache miss
        when(pageRepository.findByPageSlugAndIsPublishedTrue("home")).thenReturn(Optional.of(page));
        when(sectionRepository.findByPageIdAndIsActiveTrueOrderByDisplayOrderAsc(pageId)).thenReturn(List.of(section));
        when(themeRepository.findFirstByIsActiveTrue()).thenReturn(Optional.of(theme));

        PageLayoutResponse response = layoutService.getPublishedLayout("home", "en_IN");

        assertThat(response).isNotNull();
        assertThat(response.pageSlug()).isEqualTo("home");
        assertThat(response.title()).isEqualTo("Mito Crunch Home");
        assertThat(response.theme().themeName()).isEqualTo("DEFAULT_THEME");
        assertThat(response.sections()).hasSize(1);
        assertThat(response.sections().get(0).componentType()).isEqualTo("PROMO_STRIP");
    }

    @Test
    @DisplayName("Filters out sections with expired visibilityRules endDate")
    void shouldFilterExpiredSections() {
        UUID pageId = UUID.randomUUID();
        CmsPage page = CmsPage.builder()
                .id(pageId)
                .pageSlug("home")
                .title("Home")
                .isPublished(true)
                .build();

        Instant pastDate = Instant.now().minus(2, ChronoUnit.DAYS);
        CmsSection expiredSection = CmsSection.builder()
                .id(UUID.randomUUID())
                .pageId(pageId)
                .componentType("PROMO_STRIP")
                .displayOrder(1)
                .isActive(true)
                .visibilityRules(Map.of("endDate", pastDate.toString()))
                .contentPayload(Map.of("text", "Expired Promo"))
                .build();

        CmsSection activeSection = CmsSection.builder()
                .id(UUID.randomUUID())
                .pageId(pageId)
                .componentType("HERO_CAROUSEL")
                .displayOrder(2)
                .isActive(true)
                .visibilityRules(Map.of())
                .contentPayload(Map.of("slides", List.of()))
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(pageRepository.findByPageSlugAndIsPublishedTrue("home")).thenReturn(Optional.of(page));
        when(sectionRepository.findByPageIdAndIsActiveTrueOrderByDisplayOrderAsc(pageId)).thenReturn(List.of(expiredSection, activeSection));
        when(themeRepository.findFirstByIsActiveTrue()).thenReturn(Optional.empty());

        PageLayoutResponse response = layoutService.getPublishedLayout("home", null);

        assertThat(response.sections()).hasSize(1);
        assertThat(response.sections().get(0).componentType()).isEqualTo("HERO_CAROUSEL");
    }

    @Test
    @DisplayName("Gracefully falls back to database when Redis throws RedisConnectionFailureException")
    void shouldFallbackGracefullyWhenRedisFails() {
        UUID pageId = UUID.randomUUID();
        CmsPage page = CmsPage.builder()
                .id(pageId)
                .pageSlug("home")
                .title("Home")
                .isPublished(true)
                .build();

        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("Connection refused to Redis:6379"));
        when(pageRepository.findByPageSlugAndIsPublishedTrue("home")).thenReturn(Optional.of(page));
        when(sectionRepository.findByPageIdAndIsActiveTrueOrderByDisplayOrderAsc(pageId)).thenReturn(List.of());
        when(themeRepository.findFirstByIsActiveTrue()).thenReturn(Optional.empty());

        PageLayoutResponse response = layoutService.getPublishedLayout("home", null);

        assertThat(response).isNotNull();
        assertThat(response.pageSlug()).isEqualTo("home");
        verify(pageRepository, times(1)).findByPageSlugAndIsPublishedTrue("home");
    }

    @Test
    @DisplayName("Throws RESOURCE_NOT_FOUND (HTTP 404) when page does not exist in DB")
    void shouldThrowWhenPageNotFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(pageRepository.findByPageSlugAndIsPublishedTrue("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> layoutService.getPublishedLayout("unknown", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }
}
