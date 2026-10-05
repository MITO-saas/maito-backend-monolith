package com.maito.cart.internal.service;

import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartItemDto;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
import com.maito.cart.internal.domain.Cart;
import com.maito.cart.internal.domain.CartItem;
import com.maito.cart.internal.repository.CartItemRepository;
import com.maito.cart.internal.repository.CartRepository;
import com.maito.catalog.internal.domain.CatalogProduct;
import com.maito.catalog.internal.domain.CatalogProductVariant;
import com.maito.catalog.internal.domain.InventoryLevel;
import com.maito.catalog.internal.repository.CatalogProductRepository;
import com.maito.catalog.internal.repository.CatalogProductVariantRepository;
import com.maito.catalog.internal.repository.InventoryLevelRepository;
import com.maito.promotion.api.dto.DiscountCalculationResult;
import com.maito.promotion.api.service.PromotionService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogProductVariantRepository variantRepository;
    private final CatalogProductRepository productRepository;
    private final InventoryLevelRepository inventoryRepository;
    private final PromotionService promotionService;

    @Override
    @Transactional
    public CartResponse getOrCreateCart(String guestCartId, UUID customerProfileId, String currency) {
        String safeCurrency = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "INR";
        Cart cart = null;

        if (customerProfileId != null) {
            cart = cartRepository.findByCustomerProfileId(customerProfileId).orElse(null);
        }

        if (cart == null && guestCartId != null && !guestCartId.isBlank()) {
            cart = cartRepository.findByGuestCartId(guestCartId).orElse(null);
        }

        if (cart == null) {
            cart = Cart.builder()
                    .guestCartId(customerProfileId == null ? guestCartId : null)
                    .customerProfileId(customerProfileId)
                    .currencyCode(safeCurrency)
                    .build();
            cart = cartRepository.save(cart);
            log.info("Created new cart [{}]: guest=[{}], customer=[{}]", cart.getId(), guestCartId, customerProfileId);
        }

        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(UUID cartId, AddCartItemCommand cmd) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found: " + cartId));

        CatalogProductVariant variant = variantRepository.findById(cmd.variantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Product variant not found: " + cmd.variantId()));

        if (!variant.getIsActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Product variant is currently inactive: " + variant.getSku());
        }

        // Validate available inventory
        List<InventoryLevel> levels = inventoryRepository.findByVariantId(variant.getId());
        int totalAvailable = levels.stream().mapToInt(InventoryLevel::getAvailableStock).sum();
        if (totalAvailable < cmd.quantity()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Only " + totalAvailable + " units available for SKU: " + variant.getSku());
        }

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartIdAndVariantId(cartId, cmd.variantId());
        if (existingItemOpt.isPresent()) {
            CartItem existing = existingItemOpt.get();
            int newQty = existing.getQuantity() + cmd.quantity();
            if (totalAvailable < newQty) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Only " + totalAvailable + " units available for SKU: " + variant.getSku());
            }
            existing.setQuantity(newQty);
            cartItemRepository.save(existing);
        } else {
            CartItem newItem = CartItem.builder()
                    .cartId(cartId)
                    .variantId(cmd.variantId())
                    .quantity(cmd.quantity())
                    .build();
            cartItemRepository.save(newItem);
        }

        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(UUID cartId, UUID itemId, int quantity) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found: " + cartId));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart item not found: " + itemId));

        if (!item.getCartId().equals(cartId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Item does not belong to specified cart");
        }

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            List<InventoryLevel> levels = inventoryRepository.findByVariantId(item.getVariantId());
            int totalAvailable = levels.stream().mapToInt(InventoryLevel::getAvailableStock).sum();
            if (totalAvailable < quantity) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Only " + totalAvailable + " units available");
            }
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(UUID cartId, UUID itemId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found: " + cartId));

        cartItemRepository.findById(itemId).ifPresent(item -> {
            if (item.getCartId().equals(cartId)) {
                cartItemRepository.delete(item);
            }
        });

        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse mergeCarts(String guestCartId, UUID customerProfileId) {
        if (guestCartId == null || guestCartId.isBlank() || customerProfileId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Both guestCartId and customerProfileId are required for merge");
        }

        Optional<Cart> guestCartOpt = cartRepository.findByGuestCartId(guestCartId);
        if (guestCartOpt.isEmpty()) {
            return getOrCreateCart(null, customerProfileId, "INR");
        }

        Cart guestCart = guestCartOpt.get();
        List<CartItem> guestItems = cartItemRepository.findByCartId(guestCart.getId());

        Cart customerCart = cartRepository.findByCustomerProfileId(customerProfileId)
                .orElseGet(() -> cartRepository.save(Cart.builder()
                        .customerProfileId(customerProfileId)
                        .currencyCode(guestCart.getCurrencyCode())
                        .build()));

        for (CartItem guestItem : guestItems) {
            Optional<CartItem> existingCustomerItem = cartItemRepository.findByCartIdAndVariantId(customerCart.getId(), guestItem.getVariantId());
            if (existingCustomerItem.isPresent()) {
                CartItem ci = existingCustomerItem.get();
                ci.setQuantity(ci.getQuantity() + guestItem.getQuantity());
                cartItemRepository.save(ci);
            } else {
                CartItem newItem = CartItem.builder()
                        .cartId(customerCart.getId())
                        .variantId(guestItem.getVariantId())
                        .quantity(guestItem.getQuantity())
                        .build();
                cartItemRepository.save(newItem);
            }
        }

        if (customerCart.getAppliedCouponCode() == null && guestCart.getAppliedCouponCode() != null) {
            customerCart.setAppliedCouponCode(guestCart.getAppliedCouponCode());
            cartRepository.save(customerCart);
        }

        // Delete guest cart and items
        cartItemRepository.deleteByCartId(guestCart.getId());
        cartRepository.delete(guestCart);
        log.info("Successfully merged guest cart [{}] into customer cart [{}]", guestCartId, customerCart.getId());

        return buildCartResponse(customerCart);
    }

    @Override
    @Transactional
    public void applyCoupon(UUID cartId, String couponCode) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found: " + cartId));
        cart.setAppliedCouponCode(couponCode != null ? couponCode.trim().toUpperCase() : null);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void clearCart(UUID cartId) {
        cartItemRepository.deleteByCartId(cartId);
        cartRepository.findById(cartId).ifPresent(c -> {
            c.setAppliedCouponCode(null);
            cartRepository.save(c);
        });
        log.info("Cleared cart [{}]", cartId);
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemDto> itemDtos = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        String cur = cart.getCurrencyCode() != null ? cart.getCurrencyCode() : "INR";

        for (CartItem item : items) {
            Optional<CatalogProductVariant> variantOpt = variantRepository.findById(item.getVariantId());
            if (variantOpt.isEmpty()) continue;
            CatalogProductVariant variant = variantOpt.get();

            Optional<CatalogProduct> productOpt = productRepository.findById(variant.getProductId());
            String productName = productOpt.map(CatalogProduct::getName).orElse("Product");

            BigDecimal unitPrice = extractPrice(variant.getPricingTiers(), cur);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            String primaryImage = (variant.getMediaGallery() != null && !variant.getMediaGallery().isEmpty())
                    ? variant.getMediaGallery().get(0) : "";

            itemDtos.add(new CartItemDto(
                    item.getId(),
                    variant.getId(),
                    variant.getSku(),
                    productName,
                    variant.getWeightGrams(),
                    unitPrice,
                    item.getQuantity(),
                    lineTotal,
                    primaryImage
            ));
        }

        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal shipping = subtotal.compareTo(new BigDecimal("499.00")) >= 0 ? BigDecimal.ZERO : new BigDecimal("50.00");

        if (cart.getAppliedCouponCode() != null && !cart.getAppliedCouponCode().isBlank()) {
            try {
                DiscountCalculationResult discRes = promotionService.evaluateCoupon(
                        cart.getAppliedCouponCode(), subtotal, cart.getCustomerProfileId()
                );
                if (discRes.applied()) {
                    discount = discRes.discountAmount();
                    if ("FREE_SHIPPING".equalsIgnoreCase(discRes.discountType())) {
                        shipping = BigDecimal.ZERO;
                    }
                }
            } catch (Exception ex) {
                log.warn("Coupon evaluation skipped for cart [{}]: {}", cart.getId(), ex.getMessage());
            }
        }

        BigDecimal total = subtotal.subtract(discount).add(shipping).max(BigDecimal.ZERO);

        return new CartResponse(
                cart.getId(),
                cart.getGuestCartId(),
                cart.getCustomerProfileId(),
                cur,
                cart.getAppliedCouponCode(),
                subtotal,
                discount,
                shipping,
                total,
                itemDtos
        );
    }

    private BigDecimal extractPrice(Map<String, Object> pricingTiers, String currency) {
        if (pricingTiers == null) return BigDecimal.ZERO;
        Object tierObj = pricingTiers.getOrDefault(currency, pricingTiers.get("INR"));
        if (tierObj instanceof Map<?, ?> map) {
            Object salePrice = map.get("salePrice");
            if (salePrice != null) {
                try {
                    return new BigDecimal(String.valueOf(salePrice));
                } catch (Exception ignored) {}
            }
        }
        return BigDecimal.ZERO;
    }
}
