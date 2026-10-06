package com.maito.wallet.internal.controller;

import com.maito.auth.security.UserPrincipal;
import com.maito.shared.api.ApiResponse;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.wallet.api.dto.WalletDto;
import com.maito.wallet.api.dto.WalletTransactionDto;
import com.maito.wallet.api.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Customer Wallet", description = "Customer wallet balance and transactional history")
@PreAuthorize("hasAnyRole('TENANT_CUSTOMER', 'CUSTOMER', 'TENANT_ADMIN', 'ADMIN')")
public class CustomerWalletController {

    private final WalletService walletService;

    @GetMapping("/balance")
    @Operation(summary = "Get active customer wallet balance and coin ledger")
    public ResponseEntity<ApiResponse<WalletDto>> getBalance(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer authentication required");
        }
        WalletDto wallet = walletService.getOrCreateWallet(principal.getProfileId());
        return ResponseEntity.ok(ApiResponse.ok(wallet));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get customer wallet transaction ledger")
    public ResponseEntity<ApiResponse<Page<WalletTransactionDto>>> getTransactions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer authentication required");
        }
        Page<WalletTransactionDto> transactions = walletService.getTransactions(principal.getProfileId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }
}
