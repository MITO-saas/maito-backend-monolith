package com.maito.store.internal.controller;

import com.maito.shared.api.ApiResponse;
import com.maito.store.api.dto.StoreSettingsDto;
import com.maito.store.api.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store")
@RequiredArgsConstructor
@Tag(name = "Storefront Store", description = "Public store configurations and brand settings")
public class StorefrontStoreController {

    private final StoreService storeService;

    @GetMapping("/settings")
    @Operation(summary = "Get active store settings and supported currencies")
    public ResponseEntity<ApiResponse<StoreSettingsDto>> getStoreSettings() {
        StoreSettingsDto dto = storeService.getStoreSettings();
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }
}
