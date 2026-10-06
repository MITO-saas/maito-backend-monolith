package com.maito.wallet.internal.controller;

import com.maito.shared.api.ApiResponse;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.wallet.api.dto.AdjustWalletCommand;
import com.maito.wallet.api.dto.WalletDto;
import com.maito.wallet.api.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/wallet")
@RequiredArgsConstructor
@Tag(name = "Admin Wallet Management", description = "Administrative credit and debit adjustments with audit tracking")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN')")
public class AdminWalletController {

    private final WalletService walletService;

    @PostMapping("/adjust")
    @Operation(summary = "Manually adjust customer wallet balance")
    public ResponseEntity<ApiResponse<WalletDto>> adjustWallet(@Valid @RequestBody AdjustWalletCommand cmd) {
        WalletDto result;
        if ("CREDIT".equalsIgnoreCase(cmd.transactionType())) {
            result = walletService.credit(cmd.customerProfileId(), cmd.amount(), cmd.category(), cmd.referenceId(), cmd.reason());
        } else if ("DEBIT".equalsIgnoreCase(cmd.transactionType())) {
            result = walletService.debit(cmd.customerProfileId(), cmd.amount(), cmd.category(), cmd.referenceId(), cmd.reason());
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Transaction type must be CREDIT or DEBIT");
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
