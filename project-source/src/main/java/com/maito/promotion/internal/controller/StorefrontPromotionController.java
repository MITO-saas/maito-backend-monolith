package com.maito.promotion.internal.controller;

import com.maito.auth.security.UserPrincipal;
import com.maito.promotion.api.dto.ApplyCouponCommand;
import com.maito.promotion.api.dto.DiscountCalculationResult;
import com.maito.promotion.api.service.PromotionService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
@Tag(name = "Storefront Promotions", description = "Public discount coupons and promotional rules evaluation")
public class StorefrontPromotionController {

    private final PromotionService promotionService;

    @PostMapping("/apply")
    @Operation(summary = "Validate and evaluate coupon code against current cart subtotal")
    public ResponseEntity<ApiResponse<DiscountCalculationResult>> applyCoupon(
            @Valid @RequestBody ApplyCouponCommand cmd,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID customerProfileId = (principal != null) ? principal.getProfileId() : null;
        DiscountCalculationResult result = promotionService.evaluateCoupon(cmd.code(), cmd.cartSubtotal(), customerProfileId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
