package com.maito.support.internal.domain;

import com.maito.shared.domain.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ticket_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketMessage extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @Column(name = "sender_profile_id", nullable = false)
    private UUID senderProfileId;

    @Column(name = "sender_role", nullable = false, length = 32)
    private String senderRole; // CUSTOMER, AGENT

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_urls", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> attachmentUrls = new ArrayList<>();
}