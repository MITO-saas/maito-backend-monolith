package com.maito.promotion.internal.controller;

import com.maito.promotion.api.dto.CreatePromotionCommand;
import com.maito.promotion.api.dto.PromotionDto;
import com.maito.promotion.api.service.PromotionService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/promotions")
@RequiredArgsConstructor
@Tag(name = "Admin Promotions", description = "Tenant Admin promotions and coupons management")
public class AdminPromotionController {

    private final PromotionService promotionService;

    @PostMapping
    @Operation(summary = "Create coupon promotion (Admin only)")
    public ResponseEntity<ApiResponse<PromotionDto>> createPromotion(@Valid @RequestBody CreatePromotionCommand cmd) {
        PromotionDto dto = promotionService.createPromotion(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @GetMapping
    @Operation(summary = "List all active promotions (Admin only)")
    public ResponseEntity<ApiResponse<List<PromotionDto>>> listPromotions() {
        List<PromotionDto> list = promotionService.getActivePromotions();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }
}
