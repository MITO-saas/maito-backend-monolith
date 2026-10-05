package com.maito.store.internal.service;

import com.maito.store.api.dto.StoreSettingsDto;
import com.maito.store.api.dto.UpdateStoreSettingsCommand;
import com.maito.store.api.service.StoreService;
import com.maito.store.internal.domain.StoreSettings;
import com.maito.store.internal.repository.StoreSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoreServiceImpl implements StoreService {

    private final StoreSettingsRepository storeSettingsRepository;

    @Override
    @Transactional(readOnly = true)
    public StoreSettingsDto getStoreSettings() {
        StoreSettings settings = storeSettingsRepository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(() -> StoreSettings.builder()
                        .storeName("Mito Store")
                        .supportEmail("support@maito.com")
                        .baseCurrency("INR")
                        .supportedCurrencies(List.of("INR", "USD"))
                        .timezone("Asia/Kolkata")
                        .commercialSettings(Map.of())
                        .build());

        return toDto(settings);
    }

    @Override
    @Transactional
    public StoreSettingsDto updateStoreSettings(UpdateStoreSettingsCommand command) {
        StoreSettings settings = storeSettingsRepository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(() -> StoreSettings.builder()
                        .storeName(command.storeName())
                        .supportEmail(command.supportEmail())
                        .build());

        settings.setStoreName(command.storeName());
        settings.setSupportEmail(command.supportEmail());
        if (command.supportPhone() != null) {
            settings.setSupportPhone(command.supportPhone());
        }
        if (command.baseCurrency() != null) {
            settings.setBaseCurrency(command.baseCurrency());
        }
        if (command.supportedCurrencies() != null) {
            settings.setSupportedCurrencies(command.supportedCurrencies());
        }
        if (command.timezone() != null) {
            settings.setTimezone(command.timezone());
        }
        if (command.commercialSettings() != null) {
            settings.setCommercialSettings(command.commercialSettings());
        }

        StoreSettings saved = storeSettingsRepository.save(settings);
        log.info("Updated store settings for tenant store: {}", saved.getStoreName());
        return toDto(saved);
    }

    private StoreSettingsDto toDto(StoreSettings s) {
        return new StoreSettingsDto(
                s.getId(),
                s.getStoreName(),
                s.getSupportEmail(),
                s.getSupportPhone(),
                s.getBaseCurrency(),
                s.getSupportedCurrencies(),
                s.getTimezone(),
                s.getCommercialSettings()
        );
    }
}
