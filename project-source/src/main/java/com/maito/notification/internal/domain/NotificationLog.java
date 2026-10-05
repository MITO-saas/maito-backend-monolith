package com.maito.notification.internal.domain;

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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "channel", length = 32, nullable = false)
    private String channel;

    @Column(name = "template_code", length = 64, nullable = false)
    private String templateCode;

    @Column(name = "status", length = 32, nullable = false)
    @Builder.Default
    private String status = "SENT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_snapshot", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> payloadSnapshot = new HashMap<>();

    @Column(name = "sent_at", nullable = false)
    @Builder.Default
    private Instant sentAt = Instant.now();
}
