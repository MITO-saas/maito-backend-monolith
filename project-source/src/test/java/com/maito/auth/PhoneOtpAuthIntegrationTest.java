package com.maito.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.api.dto.SendOtpRequest;
import com.maito.auth.api.dto.VerifyOtpRequest;
import com.maito.auth.internal.controller.AuthController;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.auth.security.GoogleTokenVerifier;
import com.maito.auth.security.PhoneOtpService;
import com.maito.cart.api.service.CartService;
import com.maito.identity.api.dto.GlobalUserDto;
import com.maito.identity.api.service.IdentityService;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import com.maito.wallet.api.dto.WalletDto;
import com.maito.wallet.api.service.WalletService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class PhoneOtpAuthIntegrationTest {

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
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(tokenProvider.generateAccessToken(any(), any(), any(), any(), any())).thenReturn("mock-access-token-jwt");
        when(tokenProvider.generateRefreshToken(any(), any())).thenReturn("mock-refresh-token-jwt");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Send OTP: Returns success message and registers OTP request")
    void shouldSendOtpSuccessfully() throws Exception {
        SendOtpRequest request = new SendOtpRequest("+919876543210");
        when(phoneOtpService.sendOtp("+919876543210")).thenReturn("123456");

        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .header("X-Tenant-ID", "mitocrunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("OTP_SENT"));

        verify(phoneOtpService).sendOtp("+919876543210");
    }

    @Test
    @DisplayName("Verify OTP: Valid code auto-provisions customer and merges guest cart")
    void shouldVerifyOtpAndLogin() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest("+919876543210", "123456", "guest-cart-777");

        when(phoneOtpService.verifyOtp("+919876543210", "123456")).thenReturn(true);

        UUID globalUserId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        GlobalUserDto globalUser = new GlobalUserDto(
                globalUserId, "919876543210@otp.maito.com", "+919876543210", "ACTIVE", 0, Instant.now(), Instant.now(), Instant.now(), "PHONE_OTP", "+919876543210", null
        );
        when(identityService.createOrGetPhoneIdentity("+919876543210")).thenReturn(globalUser);

        when(userService.getProfileByGlobalUserId(globalUserId)).thenReturn(Optional.empty());

        TenantProfileDto createdProfile = new TenantProfileDto(
                profileId, globalUserId, "Customer", "3210", "ROLE_TENANT_CUSTOMER", List.of(), true, Instant.now(), Instant.now(), null
        );
        when(userService.createProfile(eq(globalUserId), eq("Customer"), anyString(), eq("ROLE_TENANT_CUSTOMER"), anyList(), isNull()))
                .thenReturn(createdProfile);

        when(walletService.getOrCreateWallet(profileId)).thenReturn(new WalletDto(
                UUID.randomUUID(), profileId, BigDecimal.ZERO, "INR", true, Instant.now()
        ));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .header("X-Tenant-ID", "mitocrunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token-jwt"))
                .andExpect(jsonPath("$.data.profile.role").value("ROLE_TENANT_CUSTOMER"));

        verify(cartService).mergeCarts(eq("guest-cart-777"), eq(profileId));
        verify(walletService).getOrCreateWallet(profileId);
    }

    @Test
    @DisplayName("Verify OTP: Invalid code returns 401 Authentication Failed")
    void shouldRejectInvalidOtp() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest("+919876543210", "999999", null);
        when(phoneOtpService.verifyOtp("+919876543210", "999999")).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .header("X-Tenant-ID", "mitocrunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
    @Test
    @DisplayName("Verify OTP: Existing customer returns correct profile and JWT without recreating profile")
    void shouldLoginExistingUserWithOtpWithoutRecreatingProfile() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest("+919876543210", "123456", null);
        when(phoneOtpService.verifyOtp("+919876543210", "123456")).thenReturn(true);

        UUID globalUserId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        GlobalUserDto globalUser = new GlobalUserDto(
                globalUserId, "919876543210@otp.maito.com", "+919876543210", "ACTIVE", 0, Instant.now(), Instant.now(), Instant.now(), "PHONE_OTP", "+919876543210", null
        );
        when(identityService.createOrGetPhoneIdentity("+919876543210")).thenReturn(globalUser);

        TenantProfileDto existingProfile = new TenantProfileDto(
                profileId, globalUserId, "Returning", "Customer", "ROLE_TENANT_CUSTOMER", List.of(), true, Instant.now(), Instant.now(), null
        );
        when(userService.getProfileByGlobalUserId(globalUserId)).thenReturn(Optional.of(existingProfile));

        when(walletService.getOrCreateWallet(profileId)).thenReturn(new WalletDto(
                UUID.randomUUID(), profileId, BigDecimal.valueOf(100), "INR", true, Instant.now()
        ));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .header("X-Tenant-ID", "mitocrunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token-jwt"))
                .andExpect(jsonPath("$.data.profile.firstName").value("Returning"))
                .andExpect(jsonPath("$.data.profile.role").value("ROLE_TENANT_CUSTOMER"));

        verify(userService, never()).createProfile(any(), any(), any(), any(), any(), any());
    }
}
