package com.maito.catalog.api.service;

import com.maito.catalog.api.dto.AdjustStockCommand;
import com.maito.catalog.api.dto.InventoryLevelDto;

import java.util.UUID;

public interface InventoryService {
    void reserveStock(UUID variantId, String warehouseCode, int qty);
    void releaseStock(UUID variantId, String warehouseCode, int qty);
    void deductReservedStock(UUID variantId, String warehouseCode, int qty);
    InventoryLevelDto adjustStock(AdjustStockCommand command);
    InventoryLevelDto getInventoryLevel(UUID variantId, String warehouseCode);
}
