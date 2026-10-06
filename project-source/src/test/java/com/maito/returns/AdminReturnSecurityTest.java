package com.maito.returns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.returns.api.dto.SubmitQcCommand;
import com.maito.support.api.dto.AddTicketMessageCommand;
import com.maito.support.api.dto.CreateTicketCommand;
import com.maito.support.api.dto.TicketResponse;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminReturnSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupportTicketService supportTicketService;

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert HTTP 401 UNAUTHORIZED when anonymous attempts admin return approval")
    void testAnonymousReturnApprovalRejected() throws Exception {
        UUID fakeReturnId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/admin/returns/" + fakeReturnId + "/approve")
                        .header("X-Tenant-ID", "mito_crunch"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Assert HTTP 403 FORBIDDEN when customer token attempts admin return approval")
    void testCustomerTokenForbiddenOnAdminReturnApprove() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        UUID fakeReturnId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/admin/returns/" + fakeReturnId + "/approve")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Assert HTTP 403 FORBIDDEN when customer token attempts admin QC submission")
    void testCustomerTokenForbiddenOnAdminQcSubmit() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        UUID fakeReturnId = UUID.randomUUID();
        SubmitQcCommand cmd = SubmitQcCommand.builder()
                .passed(true)
                .qcNotes("Attempting illegal customer approval")
                .refundMode("WALLET")
                .build();

        mockMvc.perform(post("/api/v1/admin/returns/" + fakeReturnId + "/qc-submit")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Assert HTTP 200 OK when tenant admin accesses admin returns list")
    void testTenantAdminCanAccessAdminReturns() throws Exception {
        String adminToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_ADMIN",
                List.of(),
                UUID.randomUUID()
        );

        mockMvc.perform(get("/api/v1/admin/returns")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Assert HTTP 401 UNAUTHORIZED when anonymous attempts admin support tickets access")
    void testAnonymousAdminSupportRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/support/tickets")
                        .header("X-Tenant-ID", "mito_crunch"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Assert HTTP 403 FORBIDDEN when customer token attempts admin support tickets access")
    void testCustomerForbiddenOnAdminSupport() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        mockMvc.perform(get("/api/v1/admin/support/tickets")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Assert BOLA / IDOR protection: Customer B cannot view or reply to Customer A ticket")
    void testCustomerCannotAccessAnotherCustomerTicketBOLA() throws Exception {
        TenantContextHolder.set(new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch"));

        UUID ticketAId;
        String tokenB;
        try {
            TenantProfileDto customerA = userService.createProfile(
                    UUID.randomUUID(), "CustA_" + System.currentTimeMillis(), "User", "ROLE_TENANT_CUSTOMER", List.of()
            );
            TenantProfileDto customerB = userService.createProfile(
                    UUID.randomUUID(), "CustB_" + System.currentTimeMillis(), "User", "ROLE_TENANT_CUSTOMER", List.of()
            );

            TicketResponse ticketA = supportTicketService.createTicket(customerA.id(), CreateTicketCommand.builder()
                    .category("GENERAL_INQUIRY")
                    .subject("Customer A Private Ticket")
                    .message("Private message from customer A")
                    .build());
            ticketAId = ticketA.id();

            tokenB = tokenProvider.generateAccessToken(
                    customerB.globalUserId(),
                    "mito_crunch",
                    "ROLE_TENANT_CUSTOMER",
                    List.of(),
                    customerB.id()
            );
        } finally {
            TenantContextHolder.clear();
        }

        // Customer B tries to view ticket A -> 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/support/tickets/" + ticketAId)
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // Customer B tries to reply to ticket A -> 403 FORBIDDEN
        AddTicketMessageCommand msgCmd = AddTicketMessageCommand.builder()
                .message("Customer B unauthorized reply injection")
                .build();

        mockMvc.perform(post("/api/v1/support/tickets/" + ticketAId + "/messages")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgCmd)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}