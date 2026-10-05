package com.maito.audit.api.service;

import com.maito.audit.api.dto.AuditCommand;
import com.maito.audit.api.dto.AuditFilter;
import com.maito.audit.api.dto.AuditLogDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {
    AuditLogDto recordAudit(AuditCommand cmd);
    Page<AuditLogDto> getAuditLogs(AuditFilter filter, Pageable pageable);
}
