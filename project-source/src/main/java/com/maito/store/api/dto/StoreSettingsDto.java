package com.maito.store.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StoreSettingsDto(
    UUID id,
    String storeName,
    String supportEmail,
    String supportPhone,
    String baseCurrency,
    List<String> supportedCurrencies,
    String timezone,
    Map<String, Object> commercialSettings
) {
    public String getDefaultWarehouseCode() {
        if (commercialSettings != null && commercialSettings.get("defaultWarehouseCode") != null) {
            return commercialSettings.get("defaultWarehouseCode").toString();
        }
        return null;
    }

    public BigDecimal getFreeShippingThreshold() {
        if (commercialSettings != null && commercialSettings.get("freeShippingThreshold") != null) {
            try {
                return new BigDecimal(commercialSettings.get("freeShippingThreshold").toString());
            } catch (Exception ignored) {}
        }
        return null;
    }

    public BigDecimal getMinOrderAmount() {
        if (commercialSettings != null && commercialSettings.get("minOrderAmount") != null) {
            try {
                return new BigDecimal(commercialSettings.get("minOrderAmount").toString());
            } catch (Exception ignored) {}
        }
        return null;
    }

    public BigDecimal getDefaultTaxRate() {
        if (commercialSettings != null && commercialSettings.get("defaultTaxRate") != null) {
            try {
                return new BigDecimal(commercialSettings.get("defaultTaxRate").toString());
            } catch (Exception ignored) {}
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getBranding() {
        if (commercialSettings != null && commercialSettings.get("branding") instanceof Map) {
            return (Map<String, Object>) commercialSettings.get("branding");
        }
        return null;
    }
}
