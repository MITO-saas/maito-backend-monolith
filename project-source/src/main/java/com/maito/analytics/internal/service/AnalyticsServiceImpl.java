package com.maito.analytics.internal.service;

import com.maito.analytics.api.dto.DailySalesPointDto;
import com.maito.analytics.api.dto.DashboardKpiResponse;
import com.maito.analytics.api.dto.StockRiskItemDto;
import com.maito.analytics.api.dto.TopSellingVariantDto;
import com.maito.analytics.api.service.AnalyticsService;
import com.maito.catalog.internal.domain.InventoryLevel;
import com.maito.catalog.internal.repository.InventoryLevelRepository;
import com.maito.order.internal.domain.Order;
import com.maito.order.internal.domain.OrderItem;
import com.maito.order.internal.repository.OrderItemRepository;
import com.maito.order.internal.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryLevelRepository inventoryLevelRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardKpiResponse getExecutiveKpis(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        Instant startInstant = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endInstant = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Order> paidOrders = orderRepository.findPaidOrdersBetween(startInstant, endInstant);

        BigDecimal gmv = BigDecimal.ZERO;
        for (Order o : paidOrders) {
            if (o.getTotalAmount() != null) {
                gmv = gmv.add(o.getTotalAmount());
            }
        }

        long totalOrders = paidOrders.size();
        BigDecimal aov = totalOrders > 0
                ? gmv.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // Compute top-selling variants
        List<UUID> orderIds = paidOrders.stream().map(Order::getId).toList();
        List<TopSellingVariantDto> topSelling = new ArrayList<>();
        if (!orderIds.isEmpty()) {
            List<OrderItem> items = orderItemRepository.findByOrderIdIn(orderIds);
            Map<UUID, AggregatedVariantSale> saleMap = new HashMap<>();
            for (OrderItem item : items) {
                AggregatedVariantSale agg = saleMap.computeIfAbsent(item.getVariantId(), k ->
                        new AggregatedVariantSale(item.getVariantId(), item.getSkuSnapshot(), item.getProductNameSnapshot()));
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                BigDecimal revenue = item.getUnitPrice() != null
                        ? item.getUnitPrice().multiply(BigDecimal.valueOf(qty))
                        : BigDecimal.ZERO;
                agg.unitsSold += qty;
                agg.totalRevenue = agg.totalRevenue.add(revenue);
            }

            topSelling = saleMap.values().stream()
                    .sorted(Comparator.comparingLong((AggregatedVariantSale a) -> a.unitsSold)
                            .thenComparing(a -> a.totalRevenue).reversed())
                    .limit(5)
                    .map(a -> new TopSellingVariantDto(a.variantId, a.sku, a.productName, a.unitsSold, a.totalRevenue))
                    .toList();
        }

        // Compute stock risk items
        List<InventoryLevel> riskLevels = inventoryLevelRepository.findStockRiskItems();
        List<StockRiskItemDto> stockRiskItems = riskLevels.stream()
                .map(i -> new StockRiskItemDto(
                        i.getVariantId(),
                        i.getWarehouseCode(),
                        i.getAvailableStock(),
                        i.getReorderThreshold(),
                        i.getAvailableStock() <= 0
                ))
                .toList();

        return new DashboardKpiResponse(gmv, totalOrders, aov, topSelling, stockRiskItems);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailySalesPointDto> getDailySalesTrend(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        Instant startInstant = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endInstant = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Order> paidOrders = orderRepository.findPaidOrdersBetween(startInstant, endInstant);

        Map<LocalDate, DailySalesAgg> dailyMap = new TreeMap<>();
        // Seed map with all dates in the range
        LocalDate curr = start;
        while (!curr.isAfter(end)) {
            dailyMap.put(curr, new DailySalesAgg(curr));
            curr = curr.plusDays(1);
        }

        for (Order o : paidOrders) {
            LocalDate orderDate = o.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            DailySalesAgg agg = dailyMap.get(orderDate);
            if (agg != null) {
                agg.orderCount++;
                if (o.getTotalAmount() != null) {
                    agg.salesAmount = agg.salesAmount.add(o.getTotalAmount());
                }
            }
        }

        return dailyMap.values().stream()
                .map(a -> new DailySalesPointDto(a.date, a.orderCount, a.salesAmount))
                .toList();
    }

    private static class AggregatedVariantSale {
        final UUID variantId;
        final String sku;
        final String productName;
        long unitsSold = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        AggregatedVariantSale(UUID variantId, String sku, String productName) {
            this.variantId = variantId;
            this.sku = sku;
            this.productName = productName;
        }
    }

    private static class DailySalesAgg {
        final LocalDate date;
        long orderCount = 0;
        BigDecimal salesAmount = BigDecimal.ZERO;

        DailySalesAgg(LocalDate date) {
            this.date = date;
        }
    }
}
