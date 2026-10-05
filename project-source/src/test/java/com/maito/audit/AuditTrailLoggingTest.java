package com.maito.audit;

import com.maito.audit.api.dto.AuditCommand;
import com.maito.audit.api.dto.AuditFilter;
import com.maito.audit.api.dto.AuditLogDto;
import com.maito.audit.api.service.AuditLogService;
import com.maito.auth.security.UserPrincipal;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class AuditTrailLoggingTest {

    @Autowired
    private AuditLogService auditLogService;

    private final TenantContext tenantContext = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Assert AuditLogService records immutable audit record with actor, diffs, and IP")
    void shouldRecordAndQueryAuditLogs() {
        UUID actorId = UUID.randomUUID();
        String actionType = "INVENTORY_ADJUST";
        String entityType = "INVENTORY";
        String entityId = UUID.randomUUID().toString();

        AuditCommand cmd = new AuditCommand(
                actorId,
                "admin@mitocrunch.com",
                "ROLE_TENANT_ADMIN",
                actionType,
                entityType,
                entityId,
                "192.168.1.100",
                Map.of("availableStock", 100),
                Map.of("availableStock", 150)
        );

        AuditLogDto recorded = auditLogService.recordAudit(cmd);
        assertThat(recorded).isNotNull();
        assertThat(recorded.id()).isNotNull();
        assertThat(recorded.actorId()).isEqualTo(actorId);
        assertThat(recorded.actorEmail()).isEqualTo("admin@mitocrunch.com");
        assertThat(recorded.actionType()).isEqualTo(actionType);
        assertThat(recorded.ipAddress()).isEqualTo("192.168.1.100");
        assertThat(recorded.detailsBefore()).containsEntry("availableStock", 100);
        assertThat(recorded.detailsAfter()).containsEntry("availableStock", 150);
        assertThat(recorded.createdAt()).isNotNull();

        // Query with filter (actionType, entityType, actorId, date range)
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
        AuditFilter filter = new AuditFilter(actionType, entityType, actorId, from, to);
        Page<AuditLogDto> page = auditLogService.getAuditLogs(filter, PageRequest.of(0, 10));
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent().get(0).entityId()).isEqualTo(entityId);
    }

    @Test
    @DisplayName("Assert AuditLogService extracts actor details automatically from UserPrincipal in SecurityContext")
    void shouldExtractActorFromSecurityContextWhenOmitted() {
        UUID profileId = UUID.randomUUID();
        UUID globalUserId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                globalUserId, profileId, "sec-admin@mitocrunch.com", "mito_crunch", "ROLE_TENANT_ADMIN", List.of("catalog:write")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        AuditCommand cmd = new AuditCommand(
                null, // actorId omitted
                null, // actorEmail omitted
                null, // actorRole omitted
                "PROMOTION_CREATE",
                "PROMOTION",
                "PROMO-HOLI",
                "10.0.0.1",
                Map.of(),
                Map.of("code", "HOLI50", "discount", "50%")
        );

        AuditLogDto recorded = auditLogService.recordAudit(cmd);
        assertThat(recorded.actorId()).isEqualTo(profileId);
        assertThat(recorded.actorEmail()).isEqualTo("sec-admin@mitocrunch.com");
        assertThat(recorded.actorRole()).isEqualTo("ROLE_TENANT_ADMIN");
        assertThat(recorded.detailsAfter()).containsEntry("code", "HOLI50");
    }
}
