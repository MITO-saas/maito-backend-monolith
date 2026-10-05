package com.maito.store.internal.controller;

import com.maito.shared.api.ApiResponse;
import com.maito.store.api.dto.StoreSettingsDto;
import com.maito.store.api.dto.UpdateStoreSettingsCommand;
import com.maito.store.api.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/store")
@RequiredArgsConstructor
@Tag(name = "Admin Store", description = "Tenant Admin store configuration management")
public class AdminStoreController {

    private final StoreService storeService;

    @PutMapping("/settings")
    @Operation(summary = "Update store settings (Admin only)")
    public ResponseEntity<ApiResponse<StoreSettingsDto>> updateStoreSettings(@Valid @RequestBody UpdateStoreSettingsCommand command) {
        StoreSettingsDto dto = storeService.updateStoreSettings(command);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }
}
