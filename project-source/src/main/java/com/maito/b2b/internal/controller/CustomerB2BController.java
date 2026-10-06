package com.maito.b2b.internal.controller;

import com.maito.auth.security.UserPrincipal;
import com.maito.b2b.api.dto.*;
import com.maito.b2b.api.service.B2BInvoiceService;
import com.maito.b2b.api.service.B2BOrderService;
import com.maito.b2b.api.service.B2BPartnerService;
import com.maito.b2b.api.service.B2BPriceTierService;
import com.maito.order.api.dto.OrderResponse;
import com.maito.shared.api.ApiResponse;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/b2b")
@RequiredArgsConstructor
@Tag(name = "Customer B2B Portal", description = "Wholesale, HoReCa partner registration, contract pricing, and credit ordering")
@PreAuthorize("hasAnyRole('TENANT_CUSTOMER', 'CUSTOMER', 'TENANT_ADMIN', 'ADMIN')")
public class CustomerB2BController {

    private final B2BPartnerService partnerService;
    private final B2BPriceTierService priceTierService;
    private final B2BOrderService orderService;
    private final B2BInvoiceService invoiceService;

    @PostMapping("/register-partner")
    @Operation(summary = "Register company as wholesale/HoReCa B2B partner")
    public ResponseEntity<ApiResponse<B2BPartnerResponse>> registerPartner(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RegisterPartnerCommand cmd
    ) {
        validatePrincipal(principal);
        B2BPartnerResponse response = partnerService.registerPartner(principal.getProfileId(), cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/profile")
    @Operation(summary = "View partner profile, approved credit line, and payment terms")
    public ResponseEntity<ApiResponse<B2BPartnerResponse>> getProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validatePrincipal(principal);
        B2BPartnerResponse response = partnerService.getPartnerByCustomer(principal.getProfileId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/pricing")
    @Operation(summary = "View contract wholesale tiered price lists")
    public ResponseEntity<ApiResponse<List<B2BPriceTierResponse>>> getPricing(
            @RequestParam(required = false) UUID variantId
    ) {
        List<B2BPriceTierResponse> tiers = (variantId != null)
                ? priceTierService.getTiersForVariant(variantId)
                : priceTierService.getAllActiveTiers();
        return ResponseEntity.ok(ApiResponse.ok(tiers));
    }

    @PostMapping("/bulk-orders")
    @Operation(summary = "Place B2B bulk wholesale order with Net credit terms or Prepaid")
    public ResponseEntity<ApiResponse<OrderResponse>> placeBulkOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateB2BBulkOrderCommand cmd
    ) {
        validatePrincipal(principal);
        OrderResponse response = orderService.createBulkOrder(principal.getProfileId(), cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/invoices")
    @Operation(summary = "List tax invoices and due dates")
    public ResponseEntity<ApiResponse<Page<B2BInvoiceResponse>>> getInvoices(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        validatePrincipal(principal);
        Page<B2BInvoiceResponse> response = invoiceService.getCustomerInvoices(principal.getProfileId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/invoices/{id}/pay")
    @Operation(summary = "Settle tax invoice and restore available credit line")
    public ResponseEntity<ApiResponse<B2BInvoiceResponse>> payInvoice(
            @PathVariable UUID id
    ) {
        B2BInvoiceResponse response = invoiceService.payInvoice(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/credit-ledger")
    @Operation(summary = "View immutable credit ledger audit transactions")
    public ResponseEntity<ApiResponse<List<B2BCreditLedgerResponse>>> getCreditLedger(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validatePrincipal(principal);
        List<B2BCreditLedgerResponse> ledger = invoiceService.getPartnerCreditLedger(principal.getProfileId());
        return ResponseEntity.ok(ApiResponse.ok(ledger));
    }

    private void validatePrincipal(UserPrincipal principal) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer authentication required");
        }
    }
}