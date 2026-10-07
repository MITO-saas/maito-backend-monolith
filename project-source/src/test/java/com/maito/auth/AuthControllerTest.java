package com.maito.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.api.dto.LoginRequest;
import com.maito.auth.api.dto.RegisterRequest;
import com.maito.auth.internal.controller.AuthController;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.identity.api.dto.GlobalUserDto;
import com.maito.identity.api.service.IdentityService;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import com.maito.wallet.api.service.WalletService;
import com.maito.cart.api.service.CartService;
import com.maito.auth.security.GoogleTokenVerifier;
import com.maito.auth.security.PhoneOtpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IdentityService identityService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private WalletService walletService;

    @MockBean
    private CartService cartService;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    @MockBean
    private PhoneOtpService phoneOtpService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register creates identity, profile, and returns tokens with STOREFRONT redirect")
    void register_ShouldReturn201WithTokens() throws Exception {
        RegisterRequest req = new RegisterRequest("newcustomer@mitocrunch.com", "Password@123", "Amit", "Kumar", "+919876543210");
        UUID globalId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        GlobalUserDto mockUser = new GlobalUserDto(globalId, req.email(), req.phone(), "ACTIVE", 0, null, Instant.now(), Instant.now());
        TenantProfileDto mockProfile = new TenantProfileDto(profileId, globalId, req.firstName(), req.lastName(), "ROLE_TENANT_CUSTOMER", List.of(), true, Instant.now(), Instant.now());

        when(identityService.createIdentity(eq(req.email()), eq(req.password()), eq(req.phone()))).thenReturn(mockUser);
        when(userService.createProfile(eq(globalId), eq(req.firstName()), eq(req.lastName()), eq("ROLE_TENANT_CUSTOMER"), any())).thenReturn(mockProfile);
        when(tokenProvider.generateAccessToken(eq(globalId), eq("mito_crunch"), eq("ROLE_TENANT_CUSTOMER"), any(), eq(profileId))).thenReturn("mock-access-token");
        when(tokenProvider.generateRefreshToken(eq(globalId), eq("mito_crunch"))).thenReturn("mock-refresh-token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("mock-refresh-token"))
                .andExpect(jsonPath("$.data.redirectTarget").value("STOREFRONT"))
                .andExpect(jsonPath("$.data.profile.role").value("ROLE_TENANT_CUSTOMER"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 200 with tokens and ADMIN_DASHBOARD redirect for admin role")
    void login_ShouldReturnAdminDashboardRedirectForAdmin() throws Exception {
        LoginRequest req = new LoginRequest("admin@mitocrunch.com", "CrunchAdmin@2026");
        UUID globalId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        GlobalUserDto mockUser = new GlobalUserDto(globalId, req.email(), null, "ACTIVE", 0, null, Instant.now(), Instant.now());
        TenantProfileDto adminProfile = new TenantProfileDto(profileId, globalId, "Crunch", "Admin", "ROLE_TENANT_ADMIN", List.of("cms:manage"), true, Instant.now(), Instant.now());

        when(identityService.authenticate(eq(req.email()), eq(req.password()))).thenReturn(Optional.of(mockUser));
        when(userService.getProfileByGlobalUserId(eq(globalId))).thenReturn(Optional.of(adminProfile));
        when(tokenProvider.generateAccessToken(eq(globalId), eq("mito_crunch"), eq("ROLE_TENANT_ADMIN"), any(), eq(profileId))).thenReturn("admin-access-token");
        when(tokenProvider.generateRefreshToken(eq(globalId), eq("mito_crunch"))).thenReturn("admin-refresh-token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("admin-access-token"))
                .andExpect(jsonPath("$.data.redirectTarget").value("ADMIN_DASHBOARD"));
    }
}
