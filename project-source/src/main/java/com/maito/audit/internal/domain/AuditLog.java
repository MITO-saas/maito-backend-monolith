package com.maito.audit.internal.domain;

import com.maito.shared.domain.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_email", nullable = false)
    private String actorEmail;

    @Column(name = "actor_role", length = 64, nullable = false)
    private String actorRole;

    @Column(name = "action_type", length = 64, nullable = false)
    private String actionType;

    @Column(name = "entity_type", length = 64, nullable = false)
    private String entityType;

    @Column(name = "entity_id", length = 128, nullable = false)
    private String entityId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_before", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> detailsBefore = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_after", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> detailsAfter = new HashMap<>();
}
