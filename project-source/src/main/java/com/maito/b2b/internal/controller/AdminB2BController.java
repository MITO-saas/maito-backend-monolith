package com.maito.b2b.internal.controller;

import com.maito.b2b.api.dto.B2BPartnerResponse;
import com.maito.b2b.api.dto.B2BPriceTierResponse;
import com.maito.b2b.api.dto.CreateB2BPriceTierCommand;
import com.maito.b2b.api.dto.VerifyPartnerCommand;
import com.maito.b2b.api.service.B2BPartnerService;
import com.maito.b2b.api.service.B2BPriceTierService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/b2b")
@RequiredArgsConstructor
@Tag(name = "Admin B2B Operations", description = "Admin partner verification, credit limit assignment, and wholesale price tier configuration")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN')")
public class AdminB2BController {

    private final B2BPartnerService partnerService;
    private final B2BPriceTierService priceTierService;

    @GetMapping("/partners")
    @Operation(summary = "List B2B partners filtered by verification status")
    public ResponseEntity<ApiResponse<Page<B2BPartnerResponse>>> listPartners(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<B2BPartnerResponse> response = partnerService.listPartners(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/partners/{id}/verify")
    @Operation(summary = "Verify B2B partner, assign credit limit and payment terms")
    public ResponseEntity<ApiResponse<B2BPartnerResponse>> verifyPartner(
            @PathVariable UUID id,
            @Valid @RequestBody VerifyPartnerCommand cmd
    ) {
        B2BPartnerResponse response = partnerService.verifyPartner(id, cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/pricing-tiers")
    @Operation(summary = "Add or update volume tiered wholesale pricing for a variant")
    public ResponseEntity<ApiResponse<B2BPriceTierResponse>> createOrUpdatePricingTier(
            @Valid @RequestBody CreateB2BPriceTierCommand cmd
    ) {
        B2BPriceTierResponse response = priceTierService.createOrUpdatePriceTier(cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}