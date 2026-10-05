package com.maito.catalog.internal.repository;

import com.maito.catalog.internal.domain.InventoryLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {

    Optional<InventoryLevel> findByVariantIdAndWarehouseCode(UUID variantId, String warehouseCode);

    List<InventoryLevel> findByVariantId(UUID variantId);

    @Modifying
    @Query("UPDATE InventoryLevel i SET i.availableStock = i.availableStock - :qty, i.reservedStock = i.reservedStock + :qty " +
           "WHERE i.variantId = :variantId AND i.warehouseCode = :wh AND i.availableStock >= :qty")
    int reserveStockAtomic(@Param("variantId") UUID variantId, @Param("wh") String wh, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE InventoryLevel i SET i.availableStock = i.availableStock + :qty, i.reservedStock = i.reservedStock - :qty " +
           "WHERE i.variantId = :variantId AND i.warehouseCode = :wh AND i.reservedStock >= :qty")
    int releaseStockAtomic(@Param("variantId") UUID variantId, @Param("wh") String wh, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE InventoryLevel i SET i.reservedStock = i.reservedStock - :qty " +
           "WHERE i.variantId = :variantId AND i.warehouseCode = :wh AND i.reservedStock >= :qty")
    int deductReservedStockAtomic(@Param("variantId") UUID variantId, @Param("wh") String wh, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE InventoryLevel i SET i.availableStock = i.availableStock + :qty " +
           "WHERE i.variantId = :variantId AND i.warehouseCode = :wh")
    int replenishStockAtomic(@Param("variantId") UUID variantId, @Param("wh") String wh, @Param("qty") int qty);
}
