package com.maito.promotion;

import com.maito.promotion.api.dto.CreatePromotionCommand;
import com.maito.promotion.api.dto.DiscountCalculationResult;
import com.maito.promotion.api.service.PromotionService;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class PromotionEvaluationTest {

    @Autowired
    private PromotionService promotionService;

    private final TenantContext tenantContext = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert CRUNCH20 percentage discount with max discount cap and threshold enforcement")
    void shouldEvaluatePercentageDiscountWithCap() {
        // Subtotal = 600 -> 20% = 120 (below cap of 200)
        DiscountCalculationResult res1 = promotionService.evaluateCoupon("CRUNCH20", new BigDecimal("600.00"), UUID.randomUUID());
        assertThat(res1.applied()).isTrue();
        assertThat(res1.discountAmount()).isEqualByComparingTo(new BigDecimal("120.00"));

        // Subtotal = 2000 -> 20% = 400, strictly capped at 200.00
        DiscountCalculationResult res2 = promotionService.evaluateCoupon("CRUNCH20", new BigDecimal("2000.00"), UUID.randomUUID());
        assertThat(res2.applied()).isTrue();
        assertThat(res2.discountAmount()).isEqualByComparingTo(new BigDecimal("200.00"));

        // Subtotal = 300 -> Below minimum_order_amount of 500 (Threshold violation)
        DiscountCalculationResult res3 = promotionService.evaluateCoupon("CRUNCH20", new BigDecimal("300.00"), UUID.randomUUID());
        assertThat(res3.applied()).isFalse();
        assertThat(res3.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Assert CRUNCHFREE free shipping coupon minimum threshold")
    void shouldEvaluateFreeShippingThreshold() {
        // Subtotal = 500 -> Above 499 threshold
        DiscountCalculationResult res1 = promotionService.evaluateCoupon("CRUNCHFREE", new BigDecimal("500.00"), UUID.randomUUID());
        assertThat(res1.applied()).isTrue();
        assertThat(res1.freeShippingSavings()).isEqualByComparingTo(new BigDecimal("50.00"));

        // Subtotal = 200 -> Below 499 threshold
        DiscountCalculationResult res2 = promotionService.evaluateCoupon("CRUNCHFREE", new BigDecimal("200.00"), UUID.randomUUID());
        assertThat(res2.applied()).isFalse();
        assertThat(res2.freeShippingSavings()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Assert rejection of expired and deactivated coupons")
    void shouldRejectExpiredAndDeactivatedCoupons() {
        // Create an expired coupon
        String expiredCode = "EXPIRED_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        promotionService.createPromotion(new CreatePromotionCommand(
                expiredCode,
                "Expired test promo",
                "PERCENTAGE",
                new BigDecimal("10.00"),
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                100,
                1,
                Instant.now().minus(10, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS),
                true
        ));

        DiscountCalculationResult expiredRes = promotionService.evaluateCoupon(expiredCode, new BigDecimal("500.00"), UUID.randomUUID());
        assertThat(expiredRes.applied()).isFalse();
        assertThat(expiredRes.message()).containsIgnoringCase("expired");

        // Create an inactive coupon
        String inactiveCode = "INACTIVE_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        promotionService.createPromotion(new CreatePromotionCommand(
                inactiveCode,
                "Inactive test promo",
                "FLAT",
                new BigDecimal("50.00"),
                new BigDecimal("100.00"),
                null,
                100,
                1,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS),
                false // deactivated
        ));

        DiscountCalculationResult inactiveRes = promotionService.evaluateCoupon(inactiveCode, new BigDecimal("500.00"), UUID.randomUUID());
        assertThat(inactiveRes.applied()).isFalse();
        assertThat(inactiveRes.message()).containsIgnoringCase("no longer active");
    }

    @Test
    @DisplayName("Assert rejection of non-existent or invalid coupon")
    void shouldRejectInvalidCoupon() {
        DiscountCalculationResult res = promotionService.evaluateCoupon("NON_EXISTENT_COUPON", new BigDecimal("1000.00"), UUID.randomUUID());
        assertThat(res.applied()).isFalse();
        assertThat(res.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
