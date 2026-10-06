package com.maito.support.internal.controller;

import com.maito.auth.security.UserPrincipal;
import com.maito.shared.api.ApiResponse;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.support.api.dto.*;
import com.maito.support.api.service.SupportTicketService;
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
@RequestMapping("/api/v1/support/tickets")
@RequiredArgsConstructor
@Tag(name = "Customer Support", description = "Customer helpdesk and ticket support ingress")
@PreAuthorize("hasAnyRole('TENANT_CUSTOMER', 'CUSTOMER', 'TENANT_ADMIN', 'ADMIN')")
public class CustomerSupportController {

    private final SupportTicketService supportTicketService;

    @PostMapping
    @Operation(summary = "Create customer support ticket")
    public ResponseEntity<ApiResponse<TicketResponse>> createTicket(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTicketCommand cmd
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer authentication required");
        }
        TicketResponse response = supportTicketService.createTicket(principal.getProfileId(), cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "View customer tickets")
    public ResponseEntity<ApiResponse<Page<TicketResponse>>> getMyTickets(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer authentication required");
        }
        Page<TicketResponse> page = supportTicketService.getCustomerTickets(principal.getProfileId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{identifier}")
    @Operation(summary = "View ticket details by ID or Ticket Number")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> getTicketDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String identifier
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer authentication required");
        }

        TicketDetailResponse response;
        try {
            UUID id = UUID.fromString(identifier);
            response = supportTicketService.getTicketDetails(id);
        } catch (IllegalArgumentException e) {
            response = supportTicketService.getTicketDetailsByNumber(identifier);
        }

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("ADMIN"));
        if (!isAdmin && !response.customerProfileId().equals(principal.getProfileId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Cannot access tickets for another customer");
        }

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Add message to conversation thread")
    public ResponseEntity<ApiResponse<TicketMessageDto>> addMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddTicketMessageCommand cmd
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer authentication required");
        }
        TicketDetailResponse ticket = supportTicketService.getTicketDetails(id);
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("ADMIN"));
        if (!isAdmin && !ticket.customerProfileId().equals(principal.getProfileId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Cannot reply to another customer's ticket");
        }

        TicketMessageDto messageDto = supportTicketService.addMessage(
                id,
                principal.getProfileId(),
                "CUSTOMER",
                cmd.message(),
                cmd.attachmentUrls()
        );
        return ResponseEntity.ok(ApiResponse.ok(messageDto));
    }
}