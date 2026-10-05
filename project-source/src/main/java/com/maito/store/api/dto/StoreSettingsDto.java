package com.maito.store.api.dto;

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
) {}
