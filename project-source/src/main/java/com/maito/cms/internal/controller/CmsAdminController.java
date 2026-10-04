package com.maito.cms.internal.controller;

import com.maito.cms.api.dto.CreatePageCommand;
import com.maito.cms.api.dto.PublishPageCommand;
import com.maito.cms.api.dto.UpdateSectionCommand;
import com.maito.cms.api.service.CmsLayoutService;
import com.maito.cms.internal.domain.CmsPage;
import com.maito.cms.internal.domain.CmsSection;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/cms")
@RequiredArgsConstructor
@Tag(name = "Admin CMS", description = "Server-Driven UI Page & Component Management for Tenant Administrators")
public class CmsAdminController {

    private final CmsLayoutService cmsLayoutService;

    @PostMapping("/pages")
    @Operation(summary = "Create or Update CMS Page Layout")
    public ResponseEntity<ApiResponse<CmsPage>> createOrUpdatePage(
            @Valid @RequestBody CreatePageCommand command) {
        CmsPage page = cmsLayoutService.createOrUpdatePage(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(page));
    }

    @PutMapping("/sections/{sectionId}")
    @Operation(summary = "Hot-Update Section Component Payload")
    public ResponseEntity<ApiResponse<CmsSection>> updateSection(
            @PathVariable("sectionId") UUID sectionId,
            @Valid @RequestBody UpdateSectionCommand command) {
        CmsSection section = cmsLayoutService.updateSection(sectionId, command);
        return ResponseEntity.ok(ApiResponse.ok(section));
    }

    @PutMapping("/pages/{slug}/publish")
    @Operation(summary = "Publish or Unpublish CMS Page")
    public ResponseEntity<ApiResponse<Void>> publishPage(
            @PathVariable("slug") String slug,
            @Valid @RequestBody PublishPageCommand command) {
        cmsLayoutService.publishPage(slug, command);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
