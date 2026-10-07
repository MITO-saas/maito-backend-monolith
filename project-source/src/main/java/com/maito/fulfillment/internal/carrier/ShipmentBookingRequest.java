package com.maito.fulfillment.internal.carrier;

import java.util.Map;
import java.util.UUID;

public record ShipmentBookingRequest(
        UUID orderId,
        String orderNumber,
        String shipmentNumber,
        String customerName,
        String customerPhone,
        Map<String, Object> shippingAddress,
        int totalWeightGrams,
        int volumetricWeightGrams,
        Map<String, Object> carrierSettings,
        Map<String, Object> carrierCredentials,
        String riderName,
        String riderPhone,
        String pickupLocation
) {
    public ShipmentBookingRequest(
            UUID orderId,
            String orderNumber,
            String shipmentNumber,
            String customerName,
            String customerPhone,
            Map<String, Object> shippingAddress,
            int totalWeightGrams,
            int volumetricWeightGrams,
            Map<String, Object> carrierSettings,
            Map<String, Object> carrierCredentials,
            String riderName,
            String riderPhone
    ) {
        this(orderId, orderNumber, shipmentNumber, customerName, customerPhone,
                shippingAddress, totalWeightGrams, volumetricWeightGrams,
                carrierSettings, carrierCredentials, riderName, riderPhone, null);
    }
}
