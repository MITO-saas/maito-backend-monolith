package com.maito.promotion.api.service;

import com.maito.promotion.api.dto.CreatePromotionCommand;
import com.maito.promotion.api.dto.DiscountCalculationResult;
import com.maito.promotion.api.dto.PromotionDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PromotionService {
    DiscountCalculationResult evaluateCoupon(String code, BigDecimal cartSubtotal, UUID customerProfileId);
    PromotionDto createPromotion(CreatePromotionCommand cmd);
    List<PromotionDto> getActivePromotions();
}
