package com.maito.analytics;

import com.maito.analytics.api.dto.DailySalesPointDto;
import com.maito.analytics.api.dto.DashboardKpiResponse;
import com.maito.analytics.api.service.AnalyticsService;
import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
import com.maito.order.api.dto.CreateOrderCommand;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.dto.PaymentCallbackCommand;
import com.maito.order.api.service.OrderService;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class AnalyticsDashboardIntegrationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserService userService;

    private final TenantContext tenantContext = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    private final UUID periPeriVariantId = UUID.fromString("f1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private OrderResponse createAndPayOrder() {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "Analytics", "Shopper", "ROLE_TENANT_CUSTOMER", List.of()
        );
        CartResponse cart = cartService.getOrCreateCart(null, customer.id(), "INR");
        cartService.addItem(cart.id(), new AddCartItemCommand(periPeriVariantId, 3));

        OrderResponse order = orderService.createOrderFromCart(cart.id(), customer.id(), new CreateOrderCommand(
                Map.of("line1", "Fraser Road", "city", "Patna", "state", "Bihar", "pincode", "800001"),
                null
        ));

        PaymentCallbackCommand payCmd = new PaymentCallbackCommand("TXN-" + UUID.randomUUID().toString().substring(0, 8), "PAID", "mock-sig");
        return orderService.confirmPayment(order.id(), payCmd);
    }

    @Test
    @DisplayName("Assert AnalyticsService aggregates GMV, AOV, top-selling SKUs, and stock risk items")
    void shouldCalculateExecutiveKpisAccurately() {
        OrderResponse paidOrder = createAndPayOrder();
        assertThat(paidOrder.orderStatus()).isEqualTo("PAID");

        LocalDate today = LocalDate.now();
        LocalDate lastWeek = today.minusDays(7);

        DashboardKpiResponse kpis = analyticsService.getExecutiveKpis(lastWeek, today);
        assertThat(kpis).isNotNull();
        assertThat(kpis.grossMerchandiseValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(kpis.totalPaidOrders()).isGreaterThanOrEqualTo(1);
        assertThat(kpis.averageOrderValue()).isGreaterThan(BigDecimal.ZERO);

        // Top selling SKU assertion
        assertThat(kpis.topSellingVariants()).isNotEmpty();
        assertThat(kpis.topSellingVariants().get(0).variantId()).isEqualTo(periPeriVariantId);
        assertThat(kpis.topSellingVariants().get(0).unitsSold()).isGreaterThanOrEqualTo(3);

        // Stock risk items assertion (availableStock <= reorderThreshold)
        assertThat(kpis.stockRiskItems()).isNotNull();
    }

    @Test
    @DisplayName("Assert AnalyticsService aggregates daily sales trend points across date range")
    void shouldAggregateDailySalesTrend() {
        createAndPayOrder();

        LocalDate today = LocalDate.now();
        LocalDate lastWeek = today.minusDays(5);

        List<DailySalesPointDto> trend = analyticsService.getDailySalesTrend(lastWeek, today);
        assertThat(trend).isNotEmpty();
        assertThat(trend).hasSize(6); // 5 days ago to today inclusive

        // Today's point has sales
        DailySalesPointDto todayPoint = trend.stream()
                .filter(p -> p.date().equals(today))
                .findFirst()
                .orElse(null);

        assertThat(todayPoint).isNotNull();
        assertThat(todayPoint.orderCount()).isGreaterThanOrEqualTo(1);
        assertThat(todayPoint.salesAmount()).isGreaterThan(BigDecimal.ZERO);
    }
}
