package com.maito.audit.internal.service;

import com.maito.audit.api.dto.AuditCommand;
import com.maito.audit.api.dto.AuditFilter;
import com.maito.audit.api.dto.AuditLogDto;
import com.maito.audit.api.service.AuditLogService;
import com.maito.audit.internal.domain.AuditLog;
import com.maito.audit.internal.repository.AuditLogRepository;
import com.maito.auth.security.UserPrincipal;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public AuditLogDto recordAudit(AuditCommand cmd) {
        UUID actorId = cmd.actorId();
        String actorEmail = cmd.actorEmail();
        String actorRole = cmd.actorRole();

        // Fallback to SecurityContextHolder if actor info not explicitly supplied
        if (actorId == null || actorEmail == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                if (actorId == null) {
                    actorId = principal.getProfileId() != null ? principal.getProfileId() : principal.getGlobalUserId();
                }
                if (actorEmail == null) {
                    actorEmail = principal.getEmail();
                }
                if (actorRole == null) {
                    actorRole = principal.getRole();
                }
            }
        }

        if (actorId == null) {
            actorId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
        if (actorEmail == null) {
            actorEmail = "system@maito.io";
        }
        if (actorRole == null) {
            actorRole = "SYSTEM";
        }

        AuditLog logEntity = AuditLog.builder()
                .actorId(actorId)
                .actorEmail(actorEmail)
                .actorRole(actorRole)
                .actionType(cmd.actionType())
                .entityType(cmd.entityType())
                .entityId(cmd.entityId())
                .ipAddress(cmd.ipAddress() != null ? cmd.ipAddress() : "127.0.0.1")
                .detailsBefore(cmd.detailsBefore() != null ? cmd.detailsBefore() : new HashMap<>())
                .detailsAfter(cmd.detailsAfter() != null ? cmd.detailsAfter() : new HashMap<>())
                .build();

        AuditLog saved = auditLogRepository.save(logEntity);
        log.info("Audit log recorded: id=[{}] action=[{}] entity=[{}:{}] actor=[{}]",
                saved.getId(), saved.getActionType(), saved.getEntityType(), saved.getEntityId(), saved.getActorEmail());

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogs(AuditFilter filter, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null) {
                if (filter.actionType() != null && !filter.actionType().isBlank()) {
                    predicates.add(cb.equal(root.get("actionType"), filter.actionType()));
                }
                if (filter.entityType() != null && !filter.entityType().isBlank()) {
                    predicates.add(cb.equal(root.get("entityType"), filter.entityType()));
                }
                if (filter.actorId() != null) {
                    predicates.add(cb.equal(root.get("actorId"), filter.actorId()));
                }
                if (filter.fromDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.fromDate()));
                }
                if (filter.toDate() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.toDate()));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable).map(this::toDto);
    }

    private AuditLogDto toDto(AuditLog l) {
        return new AuditLogDto(
                l.getId(),
                l.getActorId(),
                l.getActorEmail(),
                l.getActorRole(),
                l.getActionType(),
                l.getEntityType(),
                l.getEntityId(),
                l.getIpAddress(),
                l.getDetailsBefore(),
                l.getDetailsAfter(),
                l.getCreatedAt()
        );
    }
}
