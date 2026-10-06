package com.maito.support.api.service;

import com.maito.support.api.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface SupportTicketService {
    TicketResponse createTicket(UUID customerProfileId, CreateTicketCommand cmd);
    TicketMessageDto addMessage(UUID ticketId, UUID senderProfileId, String role, String message, List<String> attachments);
    TicketResponse updateTicketStatus(UUID ticketId, String status);
    TicketDetailResponse getTicketDetails(UUID ticketId);
    TicketDetailResponse getTicketDetailsByNumber(String ticketNumber);
    Page<TicketResponse> getCustomerTickets(UUID customerProfileId, Pageable pageable);
    Page<TicketResponse> getAllTickets(String status, Pageable pageable);
}