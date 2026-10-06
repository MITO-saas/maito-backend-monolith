package com.maito.support;

import com.maito.order.internal.domain.Order;
import com.maito.order.internal.repository.OrderRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.support.api.dto.*;
import com.maito.support.api.service.SupportTicketService;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class SupportTicketIntegrationTest {

    @Autowired
    private SupportTicketService supportTicketService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserService userService;

    private final TenantContext tenantContextMito = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    private UUID customerProfileId;
    private UUID agentProfileId;

    private final Map<String, Object> testShippingAddress = Map.of(
            "fullName", "Mito Crunch Customer",
            "addressLine1", "123 Farm fresh road",
            "city", "Bengaluru",
            "state", "Karnataka",
            "postalCode", "560001"
    );

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContextMito);
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(),
                "TicketCustomer_" + System.currentTimeMillis(),
                "User",
                "ROLE_TENANT_CUSTOMER",
                List.of()
        );
        customerProfileId = customer.id();

        TenantProfileDto agent = userService.createProfile(
                UUID.randomUUID(),
                "SupportAgent_" + System.currentTimeMillis(),
                "Agent",
                "ROLE_TENANT_ADMIN",
                List.of()
        );
        agentProfileId = agent.id();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert support ticket creation, order context linking, and threaded two-way conversation")
    void testSupportTicketCreationAndThreading() {
        Order order = orderRepository.save(Order.builder()
                .orderNumber("ORD-SUPP-" + System.currentTimeMillis())
                .customerProfileId(customerProfileId)
                .orderStatus("DELIVERED")
                .currencyCode("INR")
                .subtotalAmount(new BigDecimal("199.00"))
                .totalAmount(new BigDecimal("199.00"))
                .shippingAddressSnapshot(testShippingAddress)
                .build());

        CreateTicketCommand cmd = CreateTicketCommand.builder()
                .orderId(order.getId())
                .category("ORDER_DELIVERY_ISSUE")
                .subject("Late delivery dispute")
                .priority("HIGH")
                .message("My package arrived 3 days after the promised delivery date.")
                .attachmentUrls(List.of("https://cdn.example.com/receipt.pdf"))
                .build();

        // 1. Customer creates ticket
        TicketResponse ticket = supportTicketService.createTicket(customerProfileId, cmd);
        assertThat(ticket).isNotNull();
        assertThat(ticket.ticketNumber()).startsWith("TCK-");
        assertThat(ticket.status()).isEqualTo("OPEN");
        assertThat(ticket.priority()).isEqualTo("HIGH");
        assertThat(ticket.orderId()).isEqualTo(order.getId());

        // 2. Agent replies -> transitions to WAITING_ON_CUSTOMER
        TicketMessageDto agentMsg = supportTicketService.addMessage(
                ticket.id(),
                agentProfileId,
                "AGENT",
                "Hello, we have forwarded your complaint to the courier partner.",
                List.of()
        );
        assertThat(agentMsg).isNotNull();
        assertThat(agentMsg.senderRole()).isEqualTo("AGENT");

        TicketDetailResponse detailAfterAgent = supportTicketService.getTicketDetails(ticket.id());
        assertThat(detailAfterAgent.status()).isEqualTo("WAITING_ON_CUSTOMER");

        // 3. Customer replies back -> transitions to IN_PROGRESS
        TicketMessageDto customerReply = supportTicketService.addMessage(
                ticket.id(),
                customerProfileId,
                "CUSTOMER",
                "Thank you, please let me know when you receive a tracking update.",
                List.of()
        );
        assertThat(customerReply.senderRole()).isEqualTo("CUSTOMER");

        TicketDetailResponse detailAfterCustomer = supportTicketService.getTicketDetails(ticket.id());
        assertThat(detailAfterCustomer.status()).isEqualTo("IN_PROGRESS");

        // 4. Admin updates status to RESOLVED
        TicketResponse resolved = supportTicketService.updateTicketStatus(ticket.id(), "RESOLVED");
        assertThat(resolved.status()).isEqualTo("RESOLVED");

        // 5. Verify conversation history thread ordering
        TicketDetailResponse finalDetails = supportTicketService.getTicketDetailsByNumber(ticket.ticketNumber());
        assertThat(finalDetails.status()).isEqualTo("RESOLVED");
        assertThat(finalDetails.messages()).hasSize(3);
        assertThat(finalDetails.messages().get(0).senderRole()).isEqualTo("CUSTOMER");
        assertThat(finalDetails.messages().get(1).senderRole()).isEqualTo("AGENT");
        assertThat(finalDetails.messages().get(2).senderRole()).isEqualTo("CUSTOMER");

        // 6. Verify Customer Listing
        Page<TicketResponse> customerTickets = supportTicketService.getCustomerTickets(customerProfileId, PageRequest.of(0, 10));
        assertThat(customerTickets.getContent()).anyMatch(t -> t.ticketNumber().equals(ticket.ticketNumber()));
    }
}