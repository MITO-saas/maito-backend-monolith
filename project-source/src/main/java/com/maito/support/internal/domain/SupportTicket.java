package com.maito.support.internal.domain;

import com.maito.shared.domain.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicket extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 64)
    private String ticketNumber;

    @Column(name = "customer_profile_id", nullable = false)
    private UUID customerProfileId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "priority", nullable = false, length = 32)
    @Builder.Default
    private String priority = "MEDIUM";

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "OPEN";

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<TicketMessage> messages = new ArrayList<>();

    public void addMessage(TicketMessage msg) {
        messages.add(msg);
        msg.setTicket(this);
    }
}