package com.maito.cms.internal.controller;

import com.maito.cms.api.dto.PageLayoutResponse;
import com.maito.cms.api.service.CmsLayoutService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cms")
@RequiredArgsConstructor
@Tag(name = "Storefront Headless CMS", description = "Server-Driven UI Layout Engine for Storefront Consumers")
public class CmsStorefrontController {

    private final CmsLayoutService cmsLayoutService;

    @GetMapping("/pages/{slug}")
    @Operation(summary = "Get Published Page Layout", description = "Fetches published SDUI layout component tree with brand theme tokens for the current tenant storefront.")
    public ResponseEntity<ApiResponse<PageLayoutResponse>> getPublishedLayout(
            @Parameter(description = "Page slug (e.g., home, about-us)")
            @PathVariable("slug") String slug,
            @Parameter(description = "Optional locale/language for localized content resolution")
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {

        PageLayoutResponse response = cmsLayoutService.getPublishedLayout(slug, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
