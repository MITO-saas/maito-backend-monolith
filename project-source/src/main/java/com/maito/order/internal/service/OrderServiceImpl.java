package com.maito.order.internal.service;

import com.maito.cart.internal.domain.Cart;
import com.maito.cart.internal.domain.CartItem;
import com.maito.cart.internal.repository.CartItemRepository;
import com.maito.cart.internal.repository.CartRepository;
import com.maito.catalog.api.service.InventoryService;
import com.maito.catalog.internal.domain.CatalogProduct;
import com.maito.catalog.internal.domain.CatalogProductVariant;
import com.maito.catalog.internal.repository.CatalogProductRepository;
import com.maito.catalog.internal.repository.CatalogProductVariantRepository;
import com.maito.order.api.dto.CreateOrderCommand;
import com.maito.order.api.dto.OrderItemDto;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.dto.PaymentCallbackCommand;
import com.maito.order.api.service.OrderService;
import com.maito.store.api.dto.StoreSettingsDto;
import com.maito.store.api.service.StoreService;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.order.internal.domain.Order;
import com.maito.order.internal.domain.OrderItem;
import com.maito.order.internal.repository.OrderItemRepository;
import com.maito.order.internal.repository.OrderRepository;
import com.maito.promotion.api.dto.DiscountCalculationResult;
import com.maito.promotion.api.service.PromotionService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.wallet.api.dto.WalletDto;
import com.maito.wallet.api.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogProductVariantRepository variantRepository;
    private final CatalogProductRepository productRepository;
    private final InventoryService inventoryService;
    private final PromotionService promotionService;
    private final WalletService walletService;
    private final StoreService storeService;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public OrderResponse createOrderFromCart(UUID cartId, UUID customerProfileId, CreateOrderCommand cmd) {
        if (customerProfileId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED, "Authenticated customer profile required for checkout");
        }

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found: " + cartId));

        List<CartItem> cartItems = cartItemRepository.findByCartId(cartId);
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot checkout with an empty cart");
        }

        if (cmd.shippingAddress() == null || cmd.shippingAddress().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Shipping address is mandatory for order creation");
        }

        String currency = cart.getCurrencyCode() != null ? cart.getCurrencyCode() : "INR";
        BigDecimal subtotal = BigDecimal.ZERO;

        // Step 1: Pre-calculate pricing & validate items
        List<PreparedLineItem> preparedLines = new ArrayList<>();
        for (CartItem ci : cartItems) {
            CatalogProductVariant variant = variantRepository.findById(ci.getVariantId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Product variant not found: " + ci.getVariantId()));

            CatalogProduct product = productRepository.findById(variant.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found: " + variant.getProductId()));

            BigDecimal unitPrice = extractPrice(variant.getPricingTiers(), currency);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(ci.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            BigDecimal variantTax = variant.getTaxRate();
            if (variantTax == null && product.getTaxRatePercent() != null) {
                variantTax = product.getTaxRatePercent().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            }
            preparedLines.add(new PreparedLineItem(
                    variant.getId(),
                    product.getName(),
                    variant.getSku(),
                    unitPrice,
                    ci.getQuantity(),
                    lineTotal,
                    variantTax
            ));
        }

        // Loyalty Coins Redemption Pre-check & Debit
        BigDecimal coinsToRedeem = (cmd.coinsToRedeem() != null && cmd.coinsToRedeem().compareTo(BigDecimal.ZERO) > 0)
                ? cmd.coinsToRedeem().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        boolean coinsDebited = false;
        if (coinsToRedeem.compareTo(BigDecimal.ZERO) > 0) {
            WalletDto wallet = walletService.getOrCreateWallet(customerProfileId);
            if (wallet.balance().compareTo(coinsToRedeem) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_WALLET_BALANCE,
                        "Insufficient loyalty coin balance: available " + wallet.balance() + ", requested " + coinsToRedeem);
            }
            walletService.debit(customerProfileId, coinsToRedeem, "CHECKOUT_REDEMPTION", "PENDING_ORDER", "Redeemed loyalty coins for checkout");
            coinsDebited = true;
        }

        List<UUID> reservedVariantIds = new ArrayList<>();
        try {
            // Resolve Store Settings dynamically
            StoreSettingsDto storeSettings = null;
            try {
                storeSettings = storeService.getStoreSettings();
            } catch (Exception ex) {
                log.warn("Unable to fetch store settings: {}", ex.getMessage());
            }

            // Enforce minimum order value
            if (storeSettings != null && storeSettings.getMinOrderAmount() != null) {
                BigDecimal minOrder = storeSettings.getMinOrderAmount();
                if (subtotal.compareTo(minOrder) < 0) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "Order subtotal (" + subtotal + ") is below minimum order amount (" + minOrder + ")");
                }
            }

            // Dynamic warehouse code resolution
            String warehouseCode = resolveWarehouseCode(cmd.warehouseCode(), storeSettings);

            // Step 2: Atomic Inventory Reservation for EVERY item
            for (PreparedLineItem line : preparedLines) {
                inventoryService.reserveStock(line.variantId(), warehouseCode, line.quantity());
                reservedVariantIds.add(line.variantId());
            }

            // Step 3: Evaluate Coupon & Compute Totals
            String effectiveCoupon = (cmd.couponCode() != null && !cmd.couponCode().isBlank())
                    ? cmd.couponCode()
                    : cart.getAppliedCouponCode();

            BigDecimal discount = BigDecimal.ZERO;
            BigDecimal freeShippingThreshold = (storeSettings != null && storeSettings.getFreeShippingThreshold() != null)
                    ? storeSettings.getFreeShippingThreshold()
                    : new BigDecimal("499.00");
            BigDecimal shipping = subtotal.compareTo(freeShippingThreshold) >= 0 ? BigDecimal.ZERO : new BigDecimal("50.00");

            if (effectiveCoupon != null && !effectiveCoupon.isBlank()) {
                DiscountCalculationResult discRes = promotionService.evaluateCoupon(effectiveCoupon, subtotal, customerProfileId);
                if (discRes.applied()) {
                    discount = discRes.discountAmount();
                    if ("FREE_SHIPPING".equalsIgnoreCase(discRes.discountType())) {
                        shipping = BigDecimal.ZERO;
                    }
                }
            }

            BigDecimal taxableAmount = subtotal.subtract(discount).max(BigDecimal.ZERO);
            BigDecimal defaultStoreTaxRate = (storeSettings != null && storeSettings.getDefaultTaxRate() != null)
                    ? storeSettings.getDefaultTaxRate()
                    : new BigDecimal("0.05");

            // Dynamic itemized tax computation across product variants
            BigDecimal totalTax = BigDecimal.ZERO;
            for (PreparedLineItem line : preparedLines) {
                BigDecimal itemTaxRate = (line.taxRate() != null) ? line.taxRate() : defaultStoreTaxRate;
                BigDecimal lineTotal = line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()));
                BigDecimal lineTaxable = (subtotal.compareTo(BigDecimal.ZERO) > 0)
                        ? lineTotal.multiply(taxableAmount).divide(subtotal, 4, RoundingMode.HALF_UP)
                        : lineTotal;
                BigDecimal lineTax = lineTaxable.multiply(itemTaxRate).setScale(2, RoundingMode.HALF_UP);
                totalTax = totalTax.add(lineTax);
            }
            BigDecimal tax = totalTax;
            BigDecimal totalBeforeCoins = taxableAmount.add(tax).add(shipping);
            BigDecimal coinDeduction = coinsToRedeem.min(totalBeforeCoins);
            BigDecimal total = totalBeforeCoins.subtract(coinDeduction).max(BigDecimal.ZERO);

            // Step 4: Generate Human-friendly Order Number
            String orderNumber = generateOrderNumber();

            Order order = Order.builder()
                    .orderNumber(orderNumber)
                    .customerProfileId(customerProfileId)
                    .orderStatus("PENDING_PAYMENT")
                    .currencyCode(currency)
                    .subtotalAmount(subtotal)
                    .discountAmount(discount.add(coinDeduction))
                    .taxAmount(tax)
                    .shippingAmount(shipping)
                    .totalAmount(total)
                    .couponCode(effectiveCoupon)
                    .shippingAddressSnapshot(cmd.shippingAddress())
                    .paymentStatus("UNPAID")
                    .coinsRedeemed(coinsToRedeem)
                    .build();

            Order savedOrder = orderRepository.save(order);

            // Step 5: Save Snapshot Order Items
            List<OrderItemDto> itemDtos = new ArrayList<>();
            for (PreparedLineItem line : preparedLines) {
                OrderItem orderItem = OrderItem.builder()
                        .orderId(savedOrder.getId())
                        .variantId(line.variantId())
                        .productNameSnapshot(line.productName())
                        .skuSnapshot(line.sku())
                        .unitPrice(line.unitPrice())
                        .quantity(line.quantity())
                        .totalLineAmount(line.totalAmount())
                        .build();

                OrderItem savedItem = orderItemRepository.save(orderItem);
                itemDtos.add(new OrderItemDto(
                        savedItem.getId(),
                        savedItem.getVariantId(),
                        savedItem.getProductNameSnapshot(),
                        savedItem.getSkuSnapshot(),
                        savedItem.getUnitPrice(),
                        savedItem.getQuantity(),
                        savedItem.getTotalLineAmount()
                ));
            }

            // Step 6: Clear Cart
            cartItemRepository.deleteByCartId(cartId);
            cart.setAppliedCouponCode(null);
            cartRepository.save(cart);

            log.info("Successfully created order [{}] (num: {}) with {} lines. Total: {}, Coins Redeemed: {}",
                    savedOrder.getId(), savedOrder.getOrderNumber(), itemDtos.size(), savedOrder.getTotalAmount(), coinsToRedeem);

            return toDto(savedOrder, itemDtos);

        } catch (Exception e) {
            log.error("Order creation failed, triggering compensating rollback: {}", e.getMessage());
            // Compensating rollback for debited coins
            if (coinsDebited) {
                try {
                    walletService.credit(customerProfileId, coinsToRedeem, "REFUND", "FAILED_ORDER", "Compensating rollback for failed checkout");
                } catch (Exception we) {
                    log.error("Failed to compensate wallet rollback: {}", we.getMessage(), we);
                }
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public OrderResponse confirmPayment(UUID orderId, PaymentCallbackCommand cmd) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderId));

        if ("PAID".equalsIgnoreCase(order.getOrderStatus())) {
            log.info("Order [{}] already confirmed as PAID", order.getOrderNumber());
            return toDto(order, getItemDtos(orderId));
        }

        if (!"SUCCESS".equalsIgnoreCase(cmd.status()) && !"PAID".equalsIgnoreCase(cmd.status())) {
            log.warn("Payment callback failure for order [{}]: status={}", order.getOrderNumber(), cmd.status());
            order.setPaymentStatus("FAILED");
            orderRepository.save(order);
            return toDto(order, getItemDtos(orderId));
        }

        order.setOrderStatus("PAID");
        order.setPaymentStatus("PAID");
        order.setPaymentReference(cmd.paymentReference());
        Order saved = orderRepository.save(order);

        // Deduct reserved stock permanently
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            String deductWh = resolveWarehouseCode(null, null);
            inventoryService.deductReservedStock(item.getVariantId(), deductWh, item.getQuantity());
        }

        log.info("Payment confirmed for order [{}]. Status transitioned to PAID and stock permanently deducted.",
                saved.getOrderNumber());

        return toDto(saved, getItemDtos(orderId));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderId));

        order.setOrderStatus(newStatus.trim().toUpperCase());
        Order saved = orderRepository.save(order);
        log.info("Updated order [{}] status to [{}]", saved.getOrderNumber(), saved.getOrderStatus());
        return toDto(saved, getItemDtos(orderId));
    }

    @Override
    @Transactional
    public void cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderId));

        if ("CANCELLED".equalsIgnoreCase(order.getOrderStatus())) {
            return;
        }

        // If order was PENDING_PAYMENT, release reserved inventory
        if ("PENDING_PAYMENT".equalsIgnoreCase(order.getOrderStatus())) {
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : items) {
                String releaseWh = resolveWarehouseCode(null, null);
                inventoryService.releaseStock(item.getVariantId(), releaseWh, item.getQuantity());
            }
        }

        // Refund redeemed coins if any
        if (order.getCoinsRedeemed() != null && order.getCoinsRedeemed().compareTo(BigDecimal.ZERO) > 0) {
            walletService.credit(order.getCustomerProfileId(), order.getCoinsRedeemed(), "REFUND", order.getOrderNumber(), "Refunded loyalty coins for cancelled order: " + order.getOrderNumber());
        }

        order.setOrderStatus("CANCELLED");
        orderRepository.save(order);
        log.info("Cancelled order [{}] and released reserved stock.", order.getOrderNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderId));
        return toDto(order, getItemDtos(order.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber, UUID customerProfileId) {
        Order order = orderRepository.findByOrderNumber(orderNumber.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderNumber));

        if (customerProfileId != null && !customerProfileId.equals(order.getCustomerProfileId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Access denied to requested order");
        }

        return toDto(order, getItemDtos(order.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(UUID customerProfileId) {
        return orderRepository.findByCustomerProfileIdOrderByCreatedAtDesc(customerProfileId).stream()
                .map(o -> toDto(o, getItemDtos(o.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> searchOrders(String status, int page, int size) {
        List<Order> orders;
        if (status != null && !status.isBlank()) {
            orders = orderRepository.findByOrderStatusOrderByCreatedAtDesc(status.trim().toUpperCase());
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }

        int fromIndex = Math.min(page * size, orders.size());
        int toIndex = Math.min(fromIndex + size, orders.size());

        return orders.subList(fromIndex, toIndex).stream()
                .map(o -> toDto(o, getItemDtos(o.getId())))
                .toList();
    }

    private List<OrderItemDto> getItemDtos(UUID orderId) {
        return orderItemRepository.findByOrderId(orderId).stream()
                .map(i -> new OrderItemDto(
                        i.getId(),
                        i.getVariantId(),
                        i.getProductNameSnapshot(),
                        i.getSkuSnapshot(),
                        i.getUnitPrice(),
                        i.getQuantity(),
                        i.getTotalLineAmount()
                ))
                .toList();
    }

    private OrderResponse toDto(Order o, List<OrderItemDto> items) {
        return new OrderResponse(
                o.getId(),
                o.getOrderNumber(),
                o.getCustomerProfileId(),
                o.getOrderStatus(),
                o.getCurrencyCode(),
                o.getSubtotalAmount(),
                o.getDiscountAmount(),
                o.getTaxAmount(),
                o.getShippingAmount(),
                o.getTotalAmount(),
                o.getCouponCode(),
                o.getShippingAddressSnapshot(),
                o.getPaymentReference(),
                o.getPaymentStatus(),
                o.getCreatedAt(),
                items,
                o.getCoinsRedeemed() != null ? o.getCoinsRedeemed() : BigDecimal.ZERO
        );
    }

    private BigDecimal extractPrice(Map<String, Object> pricingTiers, String currency) {
        if (pricingTiers == null || pricingTiers.isEmpty()) return new BigDecimal("149.00");
        String safeCurrency = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "INR";
        Object tierObj = pricingTiers.get(safeCurrency);
        if (tierObj == null) {
            tierObj = pricingTiers.get("INR");
        }
        if (tierObj == null) {
            tierObj = pricingTiers.get("inr");
        }
        if (tierObj == null && !pricingTiers.isEmpty()) {
            tierObj = pricingTiers.values().iterator().next();
        }
        if (tierObj instanceof com.maito.catalog.api.dto.PriceTierDto dto) {
            if (dto.salePrice() != null) return dto.salePrice();
            if (dto.mrp() != null) return dto.mrp();
        }
        if (tierObj instanceof Map<?, ?> map) {
            for (String key : List.of("salePrice", "sale_price", "price", "amount", "mrp", "basePrice", "base_price")) {
                Object val = map.get(key);
                if (val != null) {
                    try {
                        return new BigDecimal(String.valueOf(val));
                    } catch (Exception ignored) {}
                }
            }
        }
        if (tierObj instanceof com.fasterxml.jackson.databind.JsonNode node) {
            for (String key : List.of("salePrice", "sale_price", "price", "amount", "mrp", "basePrice", "base_price")) {
                if (node.has(key) && !node.get(key).isNull()) {
                    try {
                        return new BigDecimal(node.get(key).asText());
                    } catch (Exception ignored) {}
                }
            }
        }
        if (tierObj instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        if (tierObj != null) {
            // Support Scala Map / Vavr Map via reflection
            try {
                java.lang.reflect.Method getMethod = tierObj.getClass().getMethod("get", Object.class);
                for (String key : List.of("salePrice", "sale_price", "price", "amount", "mrp", "basePrice", "base_price")) {
                    Object opt = getMethod.invoke(tierObj, key);
                    if (opt != null) {
                        if (opt instanceof java.util.Optional<?> jOpt && jOpt.isPresent()) {
                            return new BigDecimal(String.valueOf(jOpt.get()));
                        }
                        try {
                            java.lang.reflect.Method isDefined = opt.getClass().getMethod("isDefined");
                            if (Boolean.TRUE.equals(isDefined.invoke(opt))) {
                                java.lang.reflect.Method getVal = opt.getClass().getMethod("get");
                                return new BigDecimal(String.valueOf(getVal.invoke(opt)));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            // Fallback string / regex parsing for Scala Map(key -> val) / custom objects
            String tierStr = String.valueOf(tierObj);
            for (String patternKey : List.of("salePrice", "sale_price", "price", "amount", "mrp", "basePrice", "base_price")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(patternKey + "\\s*(?:->|=|:)\\s*([0-9.]+)").matcher(tierStr);
                if (m.find()) {
                    try {
                        return new BigDecimal(m.group(1));
                    } catch (Exception ignored) {}
                }
            }
        }
        for (String key : List.of("salePrice", "sale_price", "price", "amount", "mrp")) {
            Object val = pricingTiers.get(key);
            if (val != null) {
                try {
                    return new BigDecimal(String.valueOf(val));
                } catch (Exception ignored) {}
            }
        }
        return new BigDecimal("149.00");
    }

    private String generateOrderNumber() {
        int randomPart = 100000 + RANDOM.nextInt(900000);
        return "MC-2026-" + randomPart;
    }

    private record PreparedLineItem(
            UUID variantId,
            String productName,
            String sku,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal totalAmount,
            BigDecimal taxRate
    ) {}

    private String resolveWarehouseCode(String requestedCode, StoreSettingsDto storeSettings) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            return requestedCode.trim();
        }
        if (storeSettings == null) {
            try {
                storeSettings = storeService.getStoreSettings();
            } catch (Exception ignored) {}
        }
        if (storeSettings != null && storeSettings.getDefaultWarehouseCode() != null && !storeSettings.getDefaultWarehouseCode().isBlank()) {
            return storeSettings.getDefaultWarehouseCode().trim();
        }
        return "DEFAULT_WH";
    }
}
