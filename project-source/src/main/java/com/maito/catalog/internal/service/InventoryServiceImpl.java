package com.maito.catalog.internal.service;

import com.maito.catalog.api.dto.AdjustStockCommand;
import com.maito.catalog.api.event.StockAdjustedEvent;
import com.maito.tenant.routing.TenantContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import com.maito.catalog.api.dto.InventoryLevelDto;
import com.maito.catalog.api.service.InventoryService;
import com.maito.catalog.internal.domain.InventoryLevel;
import com.maito.catalog.internal.repository.InventoryLevelRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryLevelRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void reserveStock(UUID variantId, String warehouseCode, int qty) {
        if (qty <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Reservation quantity must be strictly greater than 0");
        }
        String wh = (warehouseCode != null && !warehouseCode.isBlank()) ? warehouseCode : "DEFAULT_WH";
        int updated = inventoryRepository.reserveStockAtomic(variantId, wh, qty);
        if (updated == 0) {
            log.warn("Failed atomic stock reservation for variant [{}] wh [{}] qty [{}]: Insufficient stock", variantId, wh, qty);
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Insufficient inventory available for variant: " + variantId);
        }
        log.info("Reserved stock atomically: variant [{}] wh [{}] qty [{}]", variantId, wh, qty);
    }

    @Override
    @Transactional
    public void releaseStock(UUID variantId, String warehouseCode, int qty) {
        if (qty <= 0) {
            return;
        }
        String wh = (warehouseCode != null && !warehouseCode.isBlank()) ? warehouseCode : "DEFAULT_WH";
        int updated = inventoryRepository.releaseStockAtomic(variantId, wh, qty);
        if (updated == 0) {
            log.warn("Failed atomic stock release: variant [{}] wh [{}] qty [{}]", variantId, wh, qty);
        } else {
            log.info("Released stock atomically: variant [{}] wh [{}] qty [{}]", variantId, wh, qty);
        }
    }

    @Override
    @Transactional
    public void deductReservedStock(UUID variantId, String warehouseCode, int qty) {
        if (qty <= 0) {
            return;
        }
        String wh = (warehouseCode != null && !warehouseCode.isBlank()) ? warehouseCode : "DEFAULT_WH";
        int updated = inventoryRepository.deductReservedStockAtomic(variantId, wh, qty);
        if (updated == 0) {
            log.warn("Failed atomic stock deduction: variant [{}] wh [{}] qty [{}]", variantId, wh, qty);
        } else {
            log.info("Deducted reserved stock atomically: variant [{}] wh [{}] qty [{}]", variantId, wh, qty);
        }
    }

    @Override
    @Transactional
    public InventoryLevelDto adjustStock(AdjustStockCommand command) {
        String wh = (command.warehouseCode() != null && !command.warehouseCode().isBlank())
                ? command.warehouseCode()
                : "DEFAULT_WH";

        InventoryLevel level = inventoryRepository.findByVariantIdAndWarehouseCode(command.variantId(), wh)
                .orElseGet(() -> InventoryLevel.builder()
                        .variantId(command.variantId())
                        .warehouseCode(wh)
                        .availableStock(0)
                        .reservedStock(0)
                        .reorderThreshold(10)
                        .build());

        int newStock = level.getAvailableStock() + command.quantityDelta();
        if (newStock < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Adjusted stock cannot fall below zero: " + newStock);
        }

        level.setAvailableStock(newStock);
        InventoryLevel saved = inventoryRepository.save(level);
        log.info("Stock adjusted for variant [{}] wh [{}]: delta [{}], reason [{}], new stock [{}]",
                command.variantId(), wh, command.quantityDelta(), command.reason(), newStock);

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryLevelDto getInventoryLevel(UUID variantId, String warehouseCode) {
        String wh = (warehouseCode != null && !warehouseCode.isBlank()) ? warehouseCode : "DEFAULT_WH";
        InventoryLevel level = inventoryRepository.findByVariantIdAndWarehouseCode(variantId, wh)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Inventory level not found for variant: " + variantId));

        return toDto(level);
    }

    private InventoryLevelDto toDto(InventoryLevel l) {
        String stockStatus;
        if (l.getAvailableStock() <= 0) {
            stockStatus = "OUT_OF_STOCK";
        } else if (l.getAvailableStock() <= l.getReorderThreshold()) {
            stockStatus = "LOW_STOCK";
        } else {
            stockStatus = "IN_STOCK";
        }

        return new InventoryLevelDto(
                l.getId(),
                l.getVariantId(),
                l.getWarehouseCode(),
                l.getAvailableStock(),
                l.getReservedStock(),
                l.getReorderThreshold(),
                stockStatus
        );
    }
}
