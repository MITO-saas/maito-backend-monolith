package com.maito.support.internal.controller;

import com.maito.auth.security.UserPrincipal;
import com.maito.shared.api.ApiResponse;
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
@RequestMapping("/api/v1/admin/support/tickets")
@RequiredArgsConstructor
@Tag(name = "Admin Support Tickets", description = "Admin tenant helpdesk management and ticket triage")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN')")
public class AdminSupportController {

    private final SupportTicketService supportTicketService;

    @GetMapping
    @Operation(summary = "View all tenant support tickets")
    public ResponseEntity<ApiResponse<Page<TicketResponse>>> getAllTickets(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<TicketResponse> page = supportTicketService.getAllTickets(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "View ticket details and message thread")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> getTicketDetails(@PathVariable UUID id) {
        TicketDetailResponse response = supportTicketService.getTicketDetails(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Change ticket status (e.g. IN_PROGRESS, WAITING_ON_CUSTOMER, RESOLVED, CLOSED)")
    public ResponseEntity<ApiResponse<TicketResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTicketStatusCommand cmd
    ) {
        TicketResponse response = supportTicketService.updateTicketStatus(id, cmd.status());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Admin agent reply to customer ticket")
    public ResponseEntity<ApiResponse<TicketMessageDto>> addAdminReply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddTicketMessageCommand cmd
    ) {
        UUID agentId = (principal != null && principal.getProfileId() != null)
                ? principal.getProfileId()
                : UUID.randomUUID();

        TicketMessageDto messageDto = supportTicketService.addMessage(
                id,
                agentId,
                "AGENT",
                cmd.message(),
                cmd.attachmentUrls()
        );
        return ResponseEntity.ok(ApiResponse.ok(messageDto));
    }
}