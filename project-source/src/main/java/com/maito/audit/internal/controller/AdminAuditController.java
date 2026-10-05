package com.maito.audit.internal.controller;

import com.maito.audit.api.dto.AuditFilter;
import com.maito.audit.api.dto.AuditLogDto;
import com.maito.audit.api.service.AuditLogService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@Tag(name = "Admin Compliance Audit Logs", description = "Regulatory audit trails, tamper-proof activity logs, and administrative change tracking")
public class AdminAuditController {

    private final AuditLogService auditLogService;

    @GetMapping("/logs")
    @Operation(summary = "Search and paginate compliance audit logs")
    public ResponseEntity<ApiResponse<Page<AuditLogDto>>> getAuditLogs(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AuditFilter filter = new AuditFilter(actionType, entityType, actorId, fromDate, toDate);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogDto> result = auditLogService.getAuditLogs(filter, pageRequest);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
