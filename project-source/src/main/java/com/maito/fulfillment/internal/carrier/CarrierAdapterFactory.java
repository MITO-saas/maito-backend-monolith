package com.maito.fulfillment.internal.carrier;

import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CarrierAdapterFactory {

    private final Map<CarrierType, CarrierAdapter> adapterMap = new EnumMap<>(CarrierType.class);

    public CarrierAdapterFactory(List<CarrierAdapter> adapters) {
        for (CarrierAdapter adapter : adapters) {
            adapterMap.put(adapter.getCarrierType(), adapter);
        }
    }

    public CarrierAdapter getAdapter(CarrierType type) {
        CarrierAdapter adapter = adapterMap.get(type);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "No logistics adapter registered for carrier type: " + type);
        }
        return adapter;
    }
}
