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
import com.maito.order.internal.domain.Order;
import com.maito.order.internal.domain.OrderItem;
import com.maito.order.internal.repository.OrderItemRepository;
import com.maito.order.internal.repository.OrderRepository;
import com.maito.promotion.api.dto.DiscountCalculationResult;
import com.maito.promotion.api.service.PromotionService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
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

            preparedLines.add(new PreparedLineItem(
                    variant.getId(),
                    product.getName(),
                    variant.getSku(),
                    unitPrice,
                    ci.getQuantity(),
                    lineTotal
            ));
        }

        // Step 2: Atomic Inventory Reservation for EVERY item
        for (PreparedLineItem line : preparedLines) {
            // Throws BusinessException(ErrorCode.INSUFFICIENT_STOCK) if atomic reservation fails
            inventoryService.reserveStock(line.variantId(), "DEFAULT_WH", line.quantity());
        }

        // Step 3: Evaluate Coupon & Compute Totals
        String effectiveCoupon = (cmd.couponCode() != null && !cmd.couponCode().isBlank())
                ? cmd.couponCode()
                : cart.getAppliedCouponCode();

        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal shipping = subtotal.compareTo(new BigDecimal("499.00")) >= 0 ? BigDecimal.ZERO : new BigDecimal("50.00");

        if (effectiveCoupon != null && !effectiveCoupon.isBlank()) {
            DiscountCalculationResult discRes = promotionService.evaluateCoupon(effectiveCoupon, subtotal, customerProfileId);
            if (discRes.applied()) {
                discount = discRes.discountAmount();
                if ("FREE_SHIPPING".equalsIgnoreCase(discRes.discountType())) {
                    shipping = BigDecimal.ZERO;
                }
            }
        }

        BigDecimal taxRate = new BigDecimal("0.05"); // 5% GST
        BigDecimal taxableAmount = subtotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal tax = taxableAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = taxableAmount.add(tax).add(shipping);

        // Step 4: Generate Human-friendly Order Number
        String orderNumber = generateOrderNumber();

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .customerProfileId(customerProfileId)
                .orderStatus("PENDING_PAYMENT")
                .currencyCode(currency)
                .subtotalAmount(subtotal)
                .discountAmount(discount)
                .taxAmount(tax)
                .shippingAmount(shipping)
                .totalAmount(total)
                .couponCode(effectiveCoupon)
                .shippingAddressSnapshot(cmd.shippingAddress())
                .paymentStatus("UNPAID")
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

        log.info("Successfully created order [{}] (num: {}) with {} lines. Total: {}",
                savedOrder.getId(), savedOrder.getOrderNumber(), itemDtos.size(), savedOrder.getTotalAmount());

        return toDto(savedOrder, itemDtos);
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
            inventoryService.deductReservedStock(item.getVariantId(), "DEFAULT_WH", item.getQuantity());
        }

        log.info("Order [{}] successfully PAID and stock deducted permanently.", saved.getOrderNumber());
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
                inventoryService.releaseStock(item.getVariantId(), "DEFAULT_WH", item.getQuantity());
            }
        }

        order.setOrderStatus("CANCELLED");
        orderRepository.save(order);
        log.info("Cancelled order [{}] and released reserved stock.", order.getOrderNumber());
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
                items
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

    private String generateOrderNumber() {
        int randomNum = 100000 + RANDOM.nextInt(900000);
        return "MC-2026-" + randomNum;
    }

    private record PreparedLineItem(
            UUID variantId,
            String productName,
            String sku,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal totalAmount
    ) {}
}
