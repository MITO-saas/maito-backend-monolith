package com.maito.returns.internal.service;

import com.maito.catalog.api.dto.AdjustStockCommand;
import com.maito.catalog.api.service.InventoryService;
import com.maito.order.internal.domain.Order;
import com.maito.order.internal.domain.OrderItem;
import com.maito.order.internal.repository.OrderItemRepository;
import com.maito.order.internal.repository.OrderRepository;
import com.maito.returns.api.dto.*;
import com.maito.returns.api.service.ReturnService;
import com.maito.returns.internal.domain.ReturnItem;
import com.maito.returns.internal.domain.ReturnRequest;
import com.maito.returns.internal.repository.ReturnItemRepository;
import com.maito.returns.internal.repository.ReturnRequestRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.wallet.api.service.WalletService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Year;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnItemRepository returnItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final WalletService walletService;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public ReturnResponse createReturnRequest(UUID customerProfileId, CreateReturnCommand cmd) {
        if (customerProfileId == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer profile ID is required");
        }

        Order order = orderRepository.findById(cmd.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + cmd.orderId()));

        if (!order.getCustomerProfileId().equals(customerProfileId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ELIGIBLE_FOR_RETURN, "Order does not belong to authenticated customer");
        }

        if (!"DELIVERED".equalsIgnoreCase(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_DELIVERED,
                    "Only delivered orders are eligible for return. Current status: " + order.getOrderStatus());
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        Map<UUID, OrderItem> orderItemMap = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        String returnNumber = generateReturnNumber();

        ReturnRequest returnRequest = ReturnRequest.builder()
                .returnNumber(returnNumber)
                .orderId(order.getId())
                .customerProfileId(customerProfileId)
                .status("REQUESTED")
                .reasonCategory(cmd.reasonCategory())
                .customerNotes(cmd.customerNotes())
                .proofMediaUrls(cmd.proofMediaUrls() != null ? new ArrayList<>(cmd.proofMediaUrls()) : new ArrayList<>())
                .refundMode("WALLET")
                .build();

        BigDecimal totalRefund = BigDecimal.ZERO;

        for (ReturnItemRequestDto itemDto : cmd.items()) {
            OrderItem oi = orderItemMap.get(itemDto.orderItemId());
            if (oi == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "OrderItem " + itemDto.orderItemId() + " does not belong to order " + order.getId());
            }

            if (itemDto.quantity() == null || itemDto.quantity() <= 0 || itemDto.quantity() > oi.getQuantity()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid return quantity for item " + itemDto.orderItemId() +
                        ". Purchased: " + oi.getQuantity() + ", requested: " + itemDto.quantity());
            }

            UUID variantId = itemDto.variantId() != null ? itemDto.variantId() : oi.getVariantId();
            BigDecimal lineAmount = oi.getUnitPrice().multiply(BigDecimal.valueOf(itemDto.quantity()));
            totalRefund = totalRefund.add(lineAmount);

            ReturnItem returnItem = ReturnItem.builder()
                    .orderItemId(oi.getId())
                    .variantId(variantId)
                    .quantity(itemDto.quantity())
                    .unitPrice(oi.getUnitPrice())
                    .build();

            returnRequest.addItem(returnItem);
        }

        returnRequest.setRefundAmount(totalRefund);
        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        log.info("Created return request [{}] for order [{}] by customer [{}] with {} items, total refund: {}",
                saved.getReturnNumber(), order.getOrderNumber(), customerProfileId, saved.getItems().size(), saved.getRefundAmount());

        return toResponse(saved);
    }

    @Override
    @Transactional
    public ReturnResponse approveReturn(UUID returnId) {
        ReturnRequest req = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_NOT_FOUND, "Return request not found: " + returnId));

        if (!"REQUESTED".equalsIgnoreCase(req.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_RETURN_STATUS, "Cannot approve return with status: " + req.getStatus());
        }

        req.setStatus("APPROVED");
        ReturnRequest saved = returnRequestRepository.save(req);
        log.info("Approved return request [{}]", saved.getReturnNumber());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ReturnResponse submitQcEvaluation(UUID returnId, SubmitQcCommand cmd) {
        ReturnRequest req = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_NOT_FOUND, "Return request not found: " + returnId));

        String currentStatus = req.getStatus().toUpperCase();
        if (!List.of("APPROVED", "PICKUP_SCHEDULED", "QC_IN_PROGRESS").contains(currentStatus)) {
            throw new BusinessException(ErrorCode.INVALID_RETURN_STATUS,
                    "Cannot evaluate QC on return request in status: " + currentStatus);
        }

        if (cmd.qcNotes() != null) {
            req.setQcNotes(cmd.qcNotes());
        }

        if (Boolean.TRUE.equals(cmd.passed())) {
            req.setStatus("SETTLED");
            req.setSettledAt(Instant.now());
            if (cmd.refundMode() != null && !cmd.refundMode().isBlank()) {
                req.setRefundMode(cmd.refundMode());
            }

            // 1. Restock items in inventory
            for (ReturnItem item : req.getItems()) {
                try {
                    inventoryService.adjustStock(new AdjustStockCommand(
                            item.getVariantId(),
                            "DEFAULT_WH",
                            item.getQuantity(),
                            "Restock from return " + req.getReturnNumber()
                    ));
                    log.info("Restocked variant [{}] qty [{}] for return [{}]",
                            item.getVariantId(), item.getQuantity(), req.getReturnNumber());
                } catch (Exception ex) {
                    log.error("Failed to adjust inventory for variant [{}] on return [{}]: {}",
                            item.getVariantId(), req.getReturnNumber(), ex.getMessage(), ex);
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Inventory restock failed: " + ex.getMessage());
                }
            }

            // 2. Automated Refund to Customer Wallet
            if (req.getRefundAmount() != null && req.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
                walletService.credit(
                        req.getCustomerProfileId(),
                        req.getRefundAmount(),
                        "REFUND",
                        req.getReturnNumber(),
                        "Refund for return " + req.getReturnNumber()
                );
                log.info("Credited wallet for customer [{}] refund amount [{}] for return [{}]",
                        req.getCustomerProfileId(), req.getRefundAmount(), req.getReturnNumber());
            }
        } else {
            req.setStatus("REJECTED");
            log.info("QC evaluation failed for return [{}]. Transitioned to REJECTED.", req.getReturnNumber());
        }

        ReturnRequest saved = returnRequestRepository.save(req);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnResponse> getCustomerReturns(UUID customerProfileId, Pageable pageable) {
        return returnRequestRepository.findByCustomerProfileIdOrderByCreatedAtDesc(customerProfileId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnResponse> getAllReturns(ReturnFilter filter, Pageable pageable) {
        if (filter == null || (filter.status() == null && filter.customerProfileId() == null && filter.orderId() == null)) {
            return returnRequestRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
        }

        Specification<ReturnRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null && !filter.status().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), filter.status().trim().toUpperCase()));
            }
            if (filter.customerProfileId() != null) {
                predicates.add(cb.equal(root.get("customerProfileId"), filter.customerProfileId()));
            }
            if (filter.orderId() != null) {
                predicates.add(cb.equal(root.get("orderId"), filter.orderId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return returnRequestRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnResponse getReturnById(UUID returnId) {
        return returnRequestRepository.findById(returnId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_NOT_FOUND, "Return request not found: " + returnId));
    }

    private String generateReturnNumber() {
        int randomPart = 100000 + RANDOM.nextInt(900000);
        return String.format("RET-%d-%d", Year.now().getValue(), randomPart);
    }

    private ReturnResponse toResponse(ReturnRequest req) {
        List<ReturnItemResponseDto> itemDtos = (req.getItems() != null) ?
                req.getItems().stream()
                        .map(item -> ReturnItemResponseDto.builder()
                                .id(item.getId())
                                .returnId(req.getId())
                                .orderItemId(item.getOrderItemId())
                                .variantId(item.getVariantId())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .createdAt(item.getCreatedAt())
                                .build())
                        .collect(Collectors.toList())
                : Collections.emptyList();

        return ReturnResponse.builder()
                .id(req.getId())
                .returnNumber(req.getReturnNumber())
                .orderId(req.getOrderId())
                .customerProfileId(req.getCustomerProfileId())
                .status(req.getStatus())
                .reasonCategory(req.getReasonCategory())
                .customerNotes(req.getCustomerNotes())
                .proofMediaUrls(req.getProofMediaUrls())
                .qcNotes(req.getQcNotes())
                .refundAmount(req.getRefundAmount())
                .refundMode(req.getRefundMode())
                .settledAt(req.getSettledAt())
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .items(itemDtos)
                .build();
    }
}