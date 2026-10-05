package com.maito.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.cms.api.dto.CreatePageCommand;
import com.maito.cms.api.dto.PageLayoutResponse;
import com.maito.cms.api.dto.SectionWidgetDto;
import com.maito.cms.api.dto.ThemeTokensDto;
import com.maito.cms.api.dto.UpdateSectionCommand;
import com.maito.cms.api.service.CmsLayoutService;
import com.maito.cms.internal.controller.CmsAdminController;
import com.maito.cms.internal.controller.CmsStorefrontController;
import com.maito.cms.internal.domain.CmsPage;
import com.maito.cms.internal.domain.CmsSection;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CmsStorefrontController.class, CmsAdminController.class})
@AutoConfigureMockMvc(addFilters = false)
class CmsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CmsLayoutService cmsLayoutService;

    @Test
    @DisplayName("GET /api/v1/cms/pages/{slug} returns 200 OK with published SDUI layout")
    void shouldReturnPublishedPageLayout() throws Exception {
        ThemeTokensDto theme = new ThemeTokensDto("DEFAULT_THEME", Map.of("primaryColor", "#D97706"));
        SectionWidgetDto widget = new SectionWidgetDto(
                UUID.randomUUID(),
                "PROMO_STRIP",
                1,
                Map.of("text", "Free Delivery on orders above ₹499")
        );
        PageLayoutResponse mockLayout = new PageLayoutResponse(
                "home",
                "Mito Crunch Home",
                Map.of("metaTitle", "Mito Crunch"),
                theme,
                List.of(widget)
        );

        when(cmsLayoutService.getPublishedLayout(eq("home"), any())).thenReturn(mockLayout);

        mockMvc.perform(get("/api/v1/cms/pages/home")
                        .header("X-Tenant-ID", "mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pageSlug").value("home"))
                .andExpect(jsonPath("$.data.title").value("Mito Crunch Home"))
                .andExpect(jsonPath("$.data.theme.themeName").value("DEFAULT_THEME"))
                .andExpect(jsonPath("$.data.sections[0].componentType").value("PROMO_STRIP"))
                .andExpect(jsonPath("$.data.sections[0].contentPayload.text").value("Free Delivery on orders above ₹499"));
    }

    @Test
    @DisplayName("GET /api/v1/cms/pages/{slug} returns 404 NOT FOUND when page does not exist")
    void shouldReturn404WhenPageNotFound() throws Exception {
        when(cmsLayoutService.getPublishedLayout(eq("non_existent"), any()))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Published CMS page not found: non_existent"));

        mockMvc.perform(get("/api/v1/cms/pages/non_existent")
                        .header("X-Tenant-ID", "mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MAITO_4040"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/cms/pages creates CMS page and returns 201 Created")
    void shouldCreateCmsPage() throws Exception {
        CreatePageCommand command = new CreatePageCommand("about-us", "About Us", Map.of("keywords", "makhana"), true);
        CmsPage created = CmsPage.builder()
                .id(UUID.randomUUID())
                .pageSlug("about-us")
                .title("About Us")
                .isPublished(true)
                .build();

        when(cmsLayoutService.createOrUpdatePage(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/admin/cms/pages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pageSlug").value("about-us"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/cms/sections/{id} updates section payload and returns 200 OK")
    void shouldUpdateSectionPayload() throws Exception {
        UUID sectionId = UUID.randomUUID();
        UpdateSectionCommand command = new UpdateSectionCommand(
                "HERO_CAROUSEL",
                2,
                true,
                Map.of(),
                Map.of("slides", List.of())
        );

        CmsSection updated = CmsSection.builder()
                .id(sectionId)
                .componentType("HERO_CAROUSEL")
                .displayOrder(2)
                .isActive(true)
                .build();

        when(cmsLayoutService.updateSection(eq(sectionId), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/admin/cms/sections/" + sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.componentType").value("HERO_CAROUSEL"));
    }
}
