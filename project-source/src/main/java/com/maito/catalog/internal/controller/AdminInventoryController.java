package com.maito.catalog.internal.controller;

import com.maito.catalog.api.dto.AdjustStockCommand;
import com.maito.catalog.api.dto.InventoryLevelDto;
import com.maito.catalog.api.service.InventoryService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
@Tag(name = "Admin Inventory", description = "Tenant Admin inventory replenishments and adjustments")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/adjust")
    @Operation(summary = "Replenish or adjust inventory stock (Admin only)")
    public ResponseEntity<ApiResponse<InventoryLevelDto>> adjustStock(@Valid @RequestBody AdjustStockCommand command) {
        InventoryLevelDto dto = inventoryService.adjustStock(command);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }
}
