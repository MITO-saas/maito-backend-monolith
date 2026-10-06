package com.maito.b2b.internal.service;

import com.maito.b2b.api.dto.B2BPriceTierResponse;
import com.maito.b2b.api.dto.CreateB2BPriceTierCommand;
import com.maito.b2b.api.service.B2BPriceTierService;
import com.maito.b2b.internal.domain.B2BPriceTier;
import com.maito.b2b.internal.repository.B2BPriceTierRepository;
import com.maito.catalog.internal.repository.CatalogProductVariantRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class B2BPriceTierServiceImpl implements B2BPriceTierService {

    private final B2BPriceTierRepository tierRepository;
    private final CatalogProductVariantRepository variantRepository;

    @Override
    @Transactional
    public B2BPriceTierResponse createOrUpdatePriceTier(CreateB2BPriceTierCommand cmd) {
        if (!variantRepository.existsById(cmd.variantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Product variant not found: " + cmd.variantId());
        }

        B2BPriceTier tier = tierRepository.findByVariantIdAndMinQuantity(cmd.variantId(), cmd.minQuantity())
                .orElseGet(() -> B2BPriceTier.builder()
                        .variantId(cmd.variantId())
                        .minQuantity(cmd.minQuantity())
                        .build());

        tier.setWholesaleUnitPrice(cmd.wholesaleUnitPrice());
        if (cmd.currencyCode() != null && !cmd.currencyCode().isBlank()) {
            tier.setCurrencyCode(cmd.currencyCode().trim().toUpperCase());
        }
        if (cmd.isActive() != null) {
            tier.setIsActive(cmd.isActive());
        }

        B2BPriceTier saved = tierRepository.save(tier);
        log.info("Saved wholesale price tier [{}] for variant [{}] minQty [{}] unitPrice [{}]",
                saved.getId(), saved.getVariantId(), saved.getMinQuantity(), saved.getWholesaleUnitPrice());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<B2BPriceTierResponse> getTiersForVariant(UUID variantId) {
        return tierRepository.findByVariantIdAndIsActiveTrueOrderByMinQuantityAsc(variantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<B2BPriceTierResponse> getAllActiveTiers() {
        return tierRepository.findByIsActiveTrueOrderByMinQuantityAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal resolveUnitPrice(UUID variantId, int quantity, BigDecimal fallbackStandardPrice) {
        return tierRepository.findApplicableTier(variantId, quantity)
                .map(B2BPriceTier::getWholesaleUnitPrice)
                .orElse(fallbackStandardPrice);
    }

    private B2BPriceTierResponse toResponse(B2BPriceTier t) {
        return new B2BPriceTierResponse(
                t.getId(),
                t.getVariantId(),
                t.getMinQuantity(),
                t.getWholesaleUnitPrice(),
                t.getCurrencyCode(),
                t.getIsActive(),
                t.getCreatedAt()
        );
    }
}
