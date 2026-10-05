package com.maito.promotion.internal.service;

import com.maito.promotion.api.dto.CreatePromotionCommand;
import com.maito.promotion.api.dto.DiscountCalculationResult;
import com.maito.promotion.api.dto.PromotionDto;
import com.maito.promotion.api.service.PromotionService;
import com.maito.promotion.internal.domain.Promotion;
import com.maito.promotion.internal.repository.PromotionRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;

    @Override
    @Transactional(readOnly = true)
    public DiscountCalculationResult evaluateCoupon(String code, BigDecimal cartSubtotal, UUID customerProfileId) {
        if (code == null || code.isBlank()) {
            return new DiscountCalculationResult("", "NONE", BigDecimal.ZERO, BigDecimal.ZERO, false, "No coupon specified");
        }

        String safeCode = code.trim().toUpperCase();
        Optional<Promotion> promoOpt = promotionRepository.findByCodeIgnoreCase(safeCode);
        if (promoOpt.isEmpty()) {
            return new DiscountCalculationResult(safeCode, "NONE", BigDecimal.ZERO, BigDecimal.ZERO, false, "Invalid coupon code: " + safeCode);
        }

        Promotion promo = promoOpt.get();
        if (!promo.getIsActive()) {
            return new DiscountCalculationResult(safeCode, promo.getDiscountType(), BigDecimal.ZERO, BigDecimal.ZERO, false, "Coupon is no longer active");
        }

        Instant now = Instant.now();
        if (now.isBefore(promo.getValidFrom()) || now.isAfter(promo.getValidTo())) {
            return new DiscountCalculationResult(safeCode, promo.getDiscountType(), BigDecimal.ZERO, BigDecimal.ZERO, false, "Coupon has expired or is not yet valid");
        }

        if (cartSubtotal != null && cartSubtotal.compareTo(promo.getMinimumOrderAmount()) < 0) {
            return new DiscountCalculationResult(safeCode, promo.getDiscountType(), BigDecimal.ZERO, BigDecimal.ZERO, false,
                    "Order minimum of ₹" + promo.getMinimumOrderAmount() + " required to use this coupon");
        }

        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal freeShippingSavings = BigDecimal.ZERO;

        switch (promo.getDiscountType().toUpperCase()) {
            case "PERCENTAGE" -> {
                BigDecimal factor = promo.getDiscountValue().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                discount = cartSubtotal != null ? cartSubtotal.multiply(factor).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                if (promo.getMaxDiscountCap() != null && discount.compareTo(promo.getMaxDiscountCap()) > 0) {
                    discount = promo.getMaxDiscountCap();
                }
            }
            case "FLAT" -> {
                discount = promo.getDiscountValue();
                if (cartSubtotal != null && discount.compareTo(cartSubtotal) > 0) {
                    discount = cartSubtotal;
                }
            }
            case "FREE_SHIPPING" -> {
                freeShippingSavings = new BigDecimal("50.00");
                discount = BigDecimal.ZERO;
            }
            default -> {
                return new DiscountCalculationResult(safeCode, "NONE", BigDecimal.ZERO, BigDecimal.ZERO, false, "Unsupported discount type");
            }
        }

        log.info("Coupon [{}] successfully applied: type [{}], discount [{}], freeShippingSavings [{}]",
                safeCode, promo.getDiscountType(), discount, freeShippingSavings);

        return new DiscountCalculationResult(safeCode, promo.getDiscountType(), discount, freeShippingSavings, true, "Coupon applied successfully");
    }

    @Override
    @Transactional
    public PromotionDto createPromotion(CreatePromotionCommand cmd) {
        String safeCode = cmd.code().trim().toUpperCase();
        if (promotionRepository.findByCodeIgnoreCase(safeCode).isPresent()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Promotion code already exists: " + safeCode);
        }

        Promotion promo = Promotion.builder()
                .code(safeCode)
                .description(cmd.description().trim())
                .discountType(cmd.discountType().trim().toUpperCase())
                .discountValue(cmd.discountValue())
                .minimumOrderAmount(cmd.minimumOrderAmount())
                .maxDiscountCap(cmd.maxDiscountCap())
                .usageLimitTotal(cmd.usageLimitTotal())
                .usageLimitPerCustomer(cmd.usageLimitPerCustomer() != null ? cmd.usageLimitPerCustomer() : 1)
                .validFrom(cmd.validFrom())
                .validTo(cmd.validTo())
                .isActive(cmd.isActive() != null ? cmd.isActive() : true)
                .build();

        Promotion saved = promotionRepository.save(promo);
        log.info("Created new promotion coupon: code=[{}]", saved.getCode());
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDto> getActivePromotions() {
        return promotionRepository.findByIsActiveTrue().stream()
                .map(this::toDto)
                .toList();
    }

    private PromotionDto toDto(Promotion p) {
        return new PromotionDto(
                p.getId(),
                p.getCode(),
                p.getDescription(),
                p.getDiscountType(),
                p.getDiscountValue(),
                p.getMinimumOrderAmount(),
                p.getMaxDiscountCap(),
                p.getUsageLimitTotal(),
                p.getUsageLimitPerCustomer(),
                p.getValidFrom(),
                p.getValidTo(),
                p.getIsActive()
        );
    }
}
