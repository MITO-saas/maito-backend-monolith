package com.maito.cart.api.service;

import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartResponse;

import java.util.UUID;

public interface CartService {
    CartResponse getOrCreateCart(String guestCartId, UUID customerProfileId, String currency);
    CartResponse addItem(UUID cartId, AddCartItemCommand cmd);
    CartResponse updateItem(UUID cartId, UUID itemId, int quantity);
    CartResponse removeItem(UUID cartId, UUID itemId);
    CartResponse mergeCarts(String guestCartId, UUID customerProfileId);
    void applyCoupon(UUID cartId, String couponCode);
    void clearCart(UUID cartId);
}
