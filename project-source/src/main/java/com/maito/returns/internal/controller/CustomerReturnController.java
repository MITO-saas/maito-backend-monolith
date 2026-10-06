package com.maito.returns.internal.controller;

import com.maito.auth.security.UserPrincipal;
import com.maito.returns.api.dto.CreateReturnCommand;
import com.maito.returns.api.dto.ReturnResponse;
import com.maito.returns.api.service.ReturnService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
@Tag(name = "Customer Returns", description = "Customer reverse logistics and return claims ingress")
@PreAuthorize("hasAnyRole('TENANT_CUSTOMER', 'CUSTOMER', 'TENANT_ADMIN', 'ADMIN')")
public class CustomerReturnController {

    private final ReturnService returnService;

    @PostMapping("/request")
    @Operation(summary = "Initiate return on delivered order")
    public ResponseEntity<ApiResponse<ReturnResponse>> requestReturn(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateReturnCommand cmd
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer authentication required");
        }
        ReturnResponse response = returnService.createReturnRequest(principal.getProfileId(), cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/my-requests")
    @Operation(summary = "List customer return claims")
    public ResponseEntity<ApiResponse<Page<ReturnResponse>>> getMyRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer authentication required");
        }
        Page<ReturnResponse> page = returnService.getCustomerReturns(principal.getProfileId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get return claim details")
    public ResponseEntity<ApiResponse<ReturnResponse>> getReturnDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer authentication required");
        }
        ReturnResponse response = returnService.getReturnById(id);
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("ADMIN"));
        if (!isAdmin && !response.customerProfileId().equals(principal.getProfileId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Cannot view returns for another customer");
        }
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}