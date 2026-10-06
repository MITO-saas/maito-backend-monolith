package com.maito.b2b.internal.service;

import com.maito.b2b.api.dto.B2BBulkOrderItemCommand;
import com.maito.b2b.api.dto.CreateB2BBulkOrderCommand;
import com.maito.b2b.api.service.B2BOrderService;
import com.maito.b2b.internal.domain.B2BCreditLedger;
import com.maito.b2b.internal.domain.B2BInvoice;
import com.maito.b2b.internal.domain.B2BPartner;
import com.maito.b2b.internal.domain.B2BPriceTier;
import com.maito.b2b.internal.repository.B2BCreditLedgerRepository;
import com.maito.b2b.internal.repository.B2BInvoiceRepository;
import com.maito.b2b.internal.repository.B2BPartnerRepository;
import com.maito.b2b.internal.repository.B2BPriceTierRepository;
import com.maito.catalog.api.service.InventoryService;
import com.maito.catalog.internal.domain.CatalogProduct;
import com.maito.catalog.internal.domain.CatalogProductVariant;
import com.maito.catalog.internal.repository.CatalogProductRepository;
import com.maito.catalog.internal.repository.CatalogProductVariantRepository;
import com.maito.order.api.dto.OrderItemDto;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.internal.domain.Order;
import com.maito.order.internal.domain.OrderItem;
import com.maito.order.internal.repository.OrderItemRepository;
import com.maito.order.internal.repository.OrderRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class B2BOrderServiceImpl implements B2BOrderService {

    private final B2BPartnerRepository partnerRepository;
    private final B2BPriceTierRepository priceTierRepository;
    private final B2BInvoiceRepository invoiceRepository;
    private final B2BCreditLedgerRepository creditLedgerRepository;
    private final CatalogProductVariantRepository variantRepository;
    private final CatalogProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public OrderResponse createBulkOrder(UUID customerProfileId, CreateB2BBulkOrderCommand cmd) {
        if (customerProfileId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Customer profile ID cannot be null");
        }

        B2BPartner partner = partnerRepository.findByCustomerProfileId(customerProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.B2B_PARTNER_NOT_FOUND, "B2B partner profile not found for customer: " + customerProfileId));

        if (!"VERIFIED".equalsIgnoreCase(partner.getVerificationStatus())) {
            throw new BusinessException(ErrorCode.B2B_PARTNER_NOT_VERIFIED,
                    "B2B partner account is " + partner.getVerificationStatus() + ". Only VERIFIED partners can place wholesale orders.");
        }

        String terms = cmd.paymentTerms() != null ? cmd.paymentTerms().trim().toUpperCase() : "PREPAID";
        if (!List.of("PREPAID", "NET_30", "NET_60").contains(terms)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unsupported payment terms: " + terms);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderItemDto> itemDtos = new ArrayList<>();

        for (B2BBulkOrderItemCommand itemCmd : cmd.items()) {
            CatalogProductVariant variant = variantRepository.findById(itemCmd.variantId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Product variant not found: " + itemCmd.variantId()));
            CatalogProduct product = productRepository.findById(variant.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found: " + variant.getProductId()));

            BigDecimal standardPrice = extractStandardPrice(variant.getPricingTiers());
            BigDecimal unitPrice = priceTierRepository.findApplicableTier(variant.getId(), itemCmd.quantity())
                    .map(B2BPriceTier::getWholesaleUnitPrice)
                    .orElse(standardPrice);

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemCmd.quantity()));
            subtotal = subtotal.add(lineTotal);

            // Reserve warehouse stock
            inventoryService.reserveStock(variant.getId(), "DEFAULT_WH", itemCmd.quantity());

            OrderItem orderItem = OrderItem.builder()
                    .variantId(variant.getId())
                    .productNameSnapshot(product.getName())
                    .skuSnapshot(variant.getSku())
                    .unitPrice(unitPrice)
                    .quantity(itemCmd.quantity())
                    .totalLineAmount(lineTotal)
                    .build();
            orderItems.add(orderItem);
        }

        // Tax Calculation (5% GST: 2.5% CGST + 2.5% SGST)
        BigDecimal gstRate = new BigDecimal("0.05");
        BigDecimal taxAmount = subtotal.multiply(gstRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(taxAmount);
        BigDecimal cgst = taxAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal sgst = taxAmount.subtract(cgst);

        boolean isNetCredit = "NET_30".equals(terms) || "NET_60".equals(terms);
        String orderNumber = "ORD-B2B-" + Year.now().getValue() + "-" + (100000 + RANDOM.nextInt(900000));

        if (isNetCredit) {
            BigDecimal availableCredit = partner.getAvailableCredit();
            if (totalAmount.compareTo(availableCredit) > 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_B2B_CREDIT,
                        "Insufficient B2B credit line. Available: " + availableCredit + ", Required: " + totalAmount);
            }

            partner.setUsedCredit(partner.getUsedCredit().add(totalAmount));
            partnerRepository.save(partner);

            B2BCreditLedger hold = B2BCreditLedger.builder()
                    .partnerId(partner.getId())
                    .entryType("CREDIT_HOLD")
                    .amount(totalAmount)
                    .usedCreditAfter(partner.getUsedCredit())
                    .referenceId(orderNumber)
                    .notes("Credit hold for B2B " + terms + " bulk order: " + orderNumber)
                    .build();
            creditLedgerRepository.save(hold);
        }

        Map<String, Object> shippingAddress = (cmd.shippingAddress() != null && !cmd.shippingAddress().isEmpty())
                ? cmd.shippingAddress()
                : partner.getBillingAddress();

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .customerProfileId(customerProfileId)
                .orderStatus(isNetCredit ? "PROCESSING" : "PENDING_PAYMENT")
                .currencyCode("INR")
                .subtotalAmount(subtotal)
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(taxAmount)
                .shippingAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .shippingAddressSnapshot(shippingAddress)
                .paymentStatus("UNPAID")
                .paymentReference(isNetCredit ? terms + "_CREDIT" : null)
                .coinsRedeemed(BigDecimal.ZERO)
                .build();

        Order savedOrder = orderRepository.save(order);

        for (OrderItem oi : orderItems) {
            oi.setOrderId(savedOrder.getId());
            OrderItem savedOi = orderItemRepository.save(oi);
            itemDtos.add(new OrderItemDto(
                    savedOi.getId(),
                    savedOi.getVariantId(),
                    savedOi.getProductNameSnapshot(),
                    savedOi.getSkuSnapshot(),
                    savedOi.getUnitPrice(),
                    savedOi.getQuantity(),
                    savedOi.getTotalLineAmount()
            ));
        }

        // Generate GST Tax Invoice
        int termsDays = "NET_60".equals(terms) ? 60 : ("NET_30".equals(terms) ? 30 : (partner.getPaymentTermsDays() != null ? partner.getPaymentTermsDays() : 0));
        Instant dueAt = Instant.now().plus(Duration.ofDays(termsDays));

        Map<String, Object> taxBreakdown = new HashMap<>();
        taxBreakdown.put("gstRate", "5%");
        taxBreakdown.put("cgst", cgst);
        taxBreakdown.put("sgst", sgst);
        taxBreakdown.put("igst", BigDecimal.ZERO);
        taxBreakdown.put("hsnCode", "19041090");
        taxBreakdown.put("gstin", partner.getGstin());
        taxBreakdown.put("companyLegalName", partner.getCompanyLegalName());

        B2BInvoice invoice = B2BInvoice.builder()
                .invoiceNumber("INV-B2B-" + System.currentTimeMillis() + "-" + (100 + RANDOM.nextInt(900)))
                .partnerId(partner.getId())
                .orderId(savedOrder.getId())
                .subtotalAmount(subtotal)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .paymentStatus("UNPAID")
                .paymentTerms(terms)
                .dueAt(dueAt)
                .taxBreakdown(taxBreakdown)
                .build();
        invoiceRepository.save(invoice);

        log.info("Created B2B bulk order [{}] (num: {}) with {} lines. Total: {}, Terms: {}",
                savedOrder.getId(), savedOrder.getOrderNumber(), itemDtos.size(), savedOrder.getTotalAmount(), terms);

        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getOrderNumber(),
                savedOrder.getCustomerProfileId(),
                savedOrder.getOrderStatus(),
                savedOrder.getCurrencyCode(),
                savedOrder.getSubtotalAmount(),
                savedOrder.getDiscountAmount(),
                savedOrder.getTaxAmount(),
                savedOrder.getShippingAmount(),
                savedOrder.getTotalAmount(),
                savedOrder.getCouponCode(),
                savedOrder.getShippingAddressSnapshot(),
                savedOrder.getPaymentReference(),
                savedOrder.getPaymentStatus(),
                savedOrder.getCreatedAt(),
                itemDtos,
                savedOrder.getCoinsRedeemed()
        );
    }

    private BigDecimal extractStandardPrice(Map<String, Object> pricingTiers) {
        if (pricingTiers == null || pricingTiers.isEmpty()) return new BigDecimal("149.00");
        Object val = pricingTiers.get("INR");
        if (val == null) val = pricingTiers.values().iterator().next();
        if (val instanceof Map<?, ?> m) {
            Object sale = m.get("salePrice");
            if (sale != null) return new BigDecimal(sale.toString());
            Object mrp = m.get("mrp");
            if (mrp != null) return new BigDecimal(mrp.toString());
        } else if (val instanceof com.maito.catalog.api.dto.PriceTierDto dto) {
            if (dto.salePrice() != null) return dto.salePrice();
            if (dto.mrp() != null) return dto.mrp();
        } else if (val instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        return new BigDecimal("149.00");
    }
}
