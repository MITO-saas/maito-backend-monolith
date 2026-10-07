package com.maito.search.internal.listener;

import com.maito.catalog.api.event.ProductCreatedEvent;
import com.maito.catalog.api.event.ProductDeletedEvent;
import com.maito.catalog.api.event.ProductUpdatedEvent;
import com.maito.catalog.api.event.StockAdjustedEvent;
import com.maito.search.api.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductSearchEventListener {

    private final ProductSearchService searchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductCreated(ProductCreatedEvent event) {
        try {
            log.debug("Received ProductCreatedEvent for product [{}]", event.productId());
            searchService.indexProduct(event.productId(), event.tenantId());
        } catch (Exception ex) {
            log.warn("Non-blocking error indexing created product [{}]: {}", event.productId(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductUpdated(ProductUpdatedEvent event) {
        try {
            log.debug("Received ProductUpdatedEvent for product [{}]", event.productId());
            searchService.indexProduct(event.productId(), event.tenantId());
        } catch (Exception ex) {
            log.warn("Non-blocking error updating indexed product [{}]: {}", event.productId(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductDeleted(ProductDeletedEvent event) {
        try {
            log.debug("Received ProductDeletedEvent for product [{}]", event.productId());
            searchService.deleteProduct(event.productId(), event.tenantId());
        } catch (Exception ex) {
            log.warn("Non-blocking error deleting indexed product [{}]: {}", event.productId(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockAdjusted(StockAdjustedEvent event) {
        try {
            log.debug("Received StockAdjustedEvent for variant [{}] stock [{}]", event.variantId(), event.availableStock());
            searchService.updateStock(event.variantId(), event.availableStock(), event.inStock(), event.tenantId());
        } catch (Exception ex) {
            log.warn("Non-blocking error updating stock in search index for variant [{}]: {}", event.variantId(), ex.getMessage());
        }
    }
}
