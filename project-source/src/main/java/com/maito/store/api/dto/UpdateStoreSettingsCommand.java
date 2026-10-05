package com.maito.store.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record UpdateStoreSettingsCommand(
    @NotBlank String storeName,
    @NotBlank String supportEmail,
    String supportPhone,
    String baseCurrency,
    List<String> supportedCurrencies,
    String timezone,
    Map<String, Object> commercialSettings
) {}
