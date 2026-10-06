package com.maito.returns.internal.controller;

import com.maito.returns.api.dto.ReturnFilter;
import com.maito.returns.api.dto.ReturnResponse;
import com.maito.returns.api.dto.SubmitQcCommand;
import com.maito.returns.api.service.ReturnService;
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
@RequestMapping("/api/v1/admin/returns")
@RequiredArgsConstructor
@Tag(name = "Admin Returns", description = "Admin reverse logistics management, approvals and QC processing")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN')")
public class AdminReturnController {

    private final ReturnService returnService;

    @GetMapping
    @Operation(summary = "List all tenant return requests with optional filtering")
    public ResponseEntity<ApiResponse<Page<ReturnResponse>>> getAllReturns(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerProfileId,
            @RequestParam(required = false) UUID orderId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        ReturnFilter filter = ReturnFilter.builder()
                .status(status)
                .customerProfileId(customerProfileId)
                .orderId(orderId)
                .build();
        Page<ReturnResponse> page = returnService.getAllReturns(filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get return claim details by ID")
    public ResponseEntity<ApiResponse<ReturnResponse>> getReturnById(@PathVariable UUID id) {
        ReturnResponse response = returnService.getReturnById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve customer return claim")
    public ResponseEntity<ApiResponse<ReturnResponse>> approveReturn(@PathVariable UUID id) {
        ReturnResponse response = returnService.approveReturn(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/qc-submit")
    @Operation(summary = "Submit QC evaluation (PASS/FAIL) and trigger auto-restock & refund")
    public ResponseEntity<ApiResponse<ReturnResponse>> submitQcEvaluation(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitQcCommand cmd
    ) {
        ReturnResponse response = returnService.submitQcEvaluation(id, cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}