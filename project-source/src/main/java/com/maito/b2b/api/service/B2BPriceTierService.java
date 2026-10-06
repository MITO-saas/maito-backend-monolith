package com.maito.b2b.api.service;

import com.maito.b2b.api.dto.B2BPriceTierResponse;
import com.maito.b2b.api.dto.CreateB2BPriceTierCommand;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface B2BPriceTierService {
    B2BPriceTierResponse createOrUpdatePriceTier(CreateB2BPriceTierCommand cmd);
    List<B2BPriceTierResponse> getTiersForVariant(UUID variantId);
    List<B2BPriceTierResponse> getAllActiveTiers();
    BigDecimal resolveUnitPrice(UUID variantId, int quantity, BigDecimal fallbackStandardPrice);
}
