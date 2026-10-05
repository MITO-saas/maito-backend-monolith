package com.maito.store.api.service;

import com.maito.store.api.dto.StoreSettingsDto;
import com.maito.store.api.dto.UpdateStoreSettingsCommand;

public interface StoreService {
    StoreSettingsDto getStoreSettings();
    StoreSettingsDto updateStoreSettings(UpdateStoreSettingsCommand command);
}
