package com.maito.support.internal.service;

import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.support.api.dto.*;
import com.maito.support.api.service.SupportTicketService;
import com.maito.support.internal.domain.SupportTicket;
import com.maito.support.internal.domain.TicketMessage;
import com.maito.support.internal.repository.SupportTicketRepository;
import com.maito.support.internal.repository.TicketMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketServiceImpl implements SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final TicketMessageRepository ticketMessageRepository;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public TicketResponse createTicket(UUID customerProfileId, CreateTicketCommand cmd) {
        if (customerProfileId == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer profile ID required");
        }

        String ticketNumber = generateTicketNumber();
        String priority = (cmd.priority() != null && !cmd.priority().isBlank()) ? cmd.priority().toUpperCase() : "MEDIUM";

        SupportTicket ticket = SupportTicket.builder()
                .ticketNumber(ticketNumber)
                .customerProfileId(customerProfileId)
                .orderId(cmd.orderId())
                .category(cmd.category())
                .subject(cmd.subject())
                .priority(priority)
                .status("OPEN")
                .build();

        TicketMessage initialMsg = TicketMessage.builder()
                .senderProfileId(customerProfileId)
                .senderRole("CUSTOMER")
                .message(cmd.message())
                .attachmentUrls(cmd.attachmentUrls() != null ? new ArrayList<>(cmd.attachmentUrls()) : new ArrayList<>())
                .build();

        ticket.addMessage(initialMsg);
        SupportTicket saved = supportTicketRepository.save(ticket);

        log.info("Created support ticket [{}] for customer [{}]", saved.getTicketNumber(), customerProfileId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public TicketMessageDto addMessage(UUID ticketId, UUID senderProfileId, String role, String message, List<String> attachments) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND, "Ticket not found: " + ticketId));

        String normalizedRole = (role != null) ? role.trim().toUpperCase() : "CUSTOMER";

        TicketMessage msg = TicketMessage.builder()
                .ticket(ticket)
                .senderProfileId(senderProfileId)
                .senderRole(normalizedRole)
                .message(message)
                .attachmentUrls(attachments != null ? new ArrayList<>(attachments) : new ArrayList<>())
                .build();

        TicketMessage savedMsg = ticketMessageRepository.save(msg);

        // State Machine transition based on conversational turn
        if ("AGENT".equalsIgnoreCase(normalizedRole)) {
            ticket.setStatus("WAITING_ON_CUSTOMER");
        } else if ("CUSTOMER".equalsIgnoreCase(normalizedRole)) {
            if ("WAITING_ON_CUSTOMER".equalsIgnoreCase(ticket.getStatus()) || "OPEN".equalsIgnoreCase(ticket.getStatus())) {
                ticket.setStatus("IN_PROGRESS");
            }
        }
        supportTicketRepository.save(ticket);

        log.info("Appended message to ticket [{}], senderRole [{}], new ticket status [{}]",
                ticket.getTicketNumber(), normalizedRole, ticket.getStatus());

        return toMessageDto(savedMsg);
    }

    @Override
    @Transactional
    public TicketResponse updateTicketStatus(UUID ticketId, String status) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND, "Ticket not found: " + ticketId));

        if (status == null || status.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_STATUS, "Ticket status cannot be empty");
        }

        String normalized = status.trim().toUpperCase();
        ticket.setStatus(normalized);
        SupportTicket saved = supportTicketRepository.save(ticket);
        log.info("Updated ticket [{}] status to [{}]", saved.getTicketNumber(), normalized);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDetailResponse getTicketDetails(UUID ticketId) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND, "Ticket not found: " + ticketId));

        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return toDetailResponse(ticket, messages);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDetailResponse getTicketDetailsByNumber(String ticketNumber) {
        SupportTicket ticket = supportTicketRepository.findByTicketNumber(ticketNumber.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND, "Ticket not found: " + ticketNumber));

        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        return toDetailResponse(ticket, messages);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getCustomerTickets(UUID customerProfileId, Pageable pageable) {
        return supportTicketRepository.findByCustomerProfileIdOrderByCreatedAtDesc(customerProfileId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getAllTickets(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return supportTicketRepository.findByStatusIgnoreCaseOrderByCreatedAtDesc(status.trim(), pageable)
                    .map(this::toResponse);
        }
        return supportTicketRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    private String generateTicketNumber() {
        int randomPart = 100000 + RANDOM.nextInt(900000);
        return String.format("TCK-%d-%d", Year.now().getValue(), randomPart);
    }

    private TicketResponse toResponse(SupportTicket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .customerProfileId(ticket.getCustomerProfileId())
                .orderId(ticket.getOrderId())
                .category(ticket.getCategory())
                .subject(ticket.getSubject())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    private TicketMessageDto toMessageDto(TicketMessage msg) {
        return TicketMessageDto.builder()
                .id(msg.getId())
                .ticketId(msg.getTicket() != null ? msg.getTicket().getId() : null)
                .senderProfileId(msg.getSenderProfileId())
                .senderRole(msg.getSenderRole())
                .message(msg.getMessage())
                .attachmentUrls(msg.getAttachmentUrls())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private TicketDetailResponse toDetailResponse(SupportTicket ticket, List<TicketMessage> messages) {
        List<TicketMessageDto> messageDtos = messages.stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());

        return TicketDetailResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .customerProfileId(ticket.getCustomerProfileId())
                .orderId(ticket.getOrderId())
                .category(ticket.getCategory())
                .subject(ticket.getSubject())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .messages(messageDtos)
                .build();
    }
}