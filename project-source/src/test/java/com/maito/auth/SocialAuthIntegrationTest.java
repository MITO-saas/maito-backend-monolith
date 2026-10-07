package com.maito.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.api.dto.SocialLoginRequest;
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
class SocialAuthIntegrationTest {

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
    @DisplayName("1-Click Google Sign-In: Auto-provisions new user, profile, wallet and merges cart")
    void shouldLoginWithGoogleAndAutoProvisionUser() throws Exception {
        String idToken = "mock-google-token-alice@gmail.com";
        String guestCartId = "guest-cart-12345";
        SocialLoginRequest request = new SocialLoginRequest("GOOGLE", idToken, guestCartId);

        UUID globalUserId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        when(googleTokenVerifier.verify(idToken)).thenReturn(new GoogleTokenVerifier.GoogleUserProfile(
                "google-sub-alice", "alice@gmail.com", "Alice", "Sharma", "https://avatar.google.com/alice.jpg"
        ));

        GlobalUserDto globalUser = new GlobalUserDto(
                globalUserId, "alice@gmail.com", null, "ACTIVE", 0, Instant.now(), Instant.now(), Instant.now(), "GOOGLE", "google-sub-alice", "https://avatar.google.com/alice.jpg"
        );
        when(identityService.createOrGetSocialIdentity(eq("alice@gmail.com"), eq("GOOGLE"), eq("google-sub-alice"), isNull(), anyString()))
                .thenReturn(globalUser);

        when(userService.getProfileByGlobalUserId(globalUserId)).thenReturn(Optional.empty());

        TenantProfileDto createdProfile = new TenantProfileDto(
                profileId, globalUserId, "Alice", "Sharma", "ROLE_TENANT_CUSTOMER", List.of(), true, Instant.now(), Instant.now(), "https://avatar.google.com/alice.jpg"
        );
        when(userService.createProfile(eq(globalUserId), eq("Alice"), eq("Sharma"), eq("ROLE_TENANT_CUSTOMER"), anyList(), anyString()))
                .thenReturn(createdProfile);

        when(walletService.getOrCreateWallet(profileId)).thenReturn(new WalletDto(
                UUID.randomUUID(), profileId, BigDecimal.ZERO, "INR", true, Instant.now()
        ));

        mockMvc.perform(post("/api/v1/auth/social-login")
                        .header("X-Tenant-ID", "mitocrunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token-jwt"))
                .andExpect(jsonPath("$.data.profile.firstName").value("Alice"))
                .andExpect(jsonPath("$.data.profile.role").value("ROLE_TENANT_CUSTOMER"));

        verify(cartService).mergeCarts(eq("guest-cart-12345"), eq(profileId));
        verify(walletService).getOrCreateWallet(profileId);
    }

    @Test
    @DisplayName("Facebook Sign-In: Auto-provisions and returns authenticated session")
    void shouldLoginWithFacebook() throws Exception {
        SocialLoginRequest request = new SocialLoginRequest("FACEBOOK", "mock-fb-token-bob@gmail.com", null);

        UUID globalUserId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        GlobalUserDto globalUser = new GlobalUserDto(
                globalUserId, "bob@gmail.com", null, "ACTIVE", 0, Instant.now(), Instant.now(), Instant.now(), "FACEBOOK", "fb-sub-12345", "https://graph.facebook.com/bob"
        );
        when(identityService.createOrGetSocialIdentity(anyString(), eq("FACEBOOK"), anyString(), isNull(), anyString()))
                .thenReturn(globalUser);

        TenantProfileDto profile = new TenantProfileDto(
                profileId, globalUserId, "Meta", "Customer", "ROLE_TENANT_CUSTOMER", List.of(), true, Instant.now(), Instant.now(), "https://graph.facebook.com/bob"
        );
        when(userService.getProfileByGlobalUserId(globalUserId)).thenReturn(Optional.of(profile));

        mockMvc.perform(post("/api/v1/auth/social-login")
                        .header("X-Tenant-ID", "mitocrunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.profile.role").value("ROLE_TENANT_CUSTOMER"));
    }
    @Test
    @DisplayName("1-Click Google Sign-In: Existing user login returns correct profile and JWT without recreating profile")
    void shouldLoginExistingUserWithGoogleWithoutRecreatingProfile() throws Exception {
        String idToken = "mock-google-token-existing@gmail.com";
        SocialLoginRequest request = new SocialLoginRequest("GOOGLE", idToken, null);

        UUID globalUserId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        when(googleTokenVerifier.verify(idToken)).thenReturn(new GoogleTokenVerifier.GoogleUserProfile(
                "google-sub-existing", "existing@gmail.com", "Existing", "Customer", "https://avatar.google.com/existing.jpg"
        ));

        GlobalUserDto existingGlobalUser = new GlobalUserDto(
                globalUserId, "existing@gmail.com", null, "ACTIVE", 0, Instant.now(), Instant.now(), Instant.now(), "GOOGLE", "google-sub-existing", "https://avatar.google.com/existing.jpg"
        );
        when(identityService.createOrGetSocialIdentity(eq("existing@gmail.com"), eq("GOOGLE"), eq("google-sub-existing"), isNull(), anyString()))
                .thenReturn(existingGlobalUser);

        TenantProfileDto existingProfile = new TenantProfileDto(
                profileId, globalUserId, "Existing", "Customer", "ROLE_TENANT_CUSTOMER", List.of(), true, Instant.now(), Instant.now(), "https://avatar.google.com/existing.jpg"
        );
        when(userService.getProfileByGlobalUserId(globalUserId)).thenReturn(Optional.of(existingProfile));

        when(walletService.getOrCreateWallet(profileId)).thenReturn(new WalletDto(
                UUID.randomUUID(), profileId, BigDecimal.valueOf(50), "INR", true, Instant.now()
        ));

        mockMvc.perform(post("/api/v1/auth/social-login")
                        .header("X-Tenant-ID", "mitocrunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token-jwt"))
                .andExpect(jsonPath("$.data.profile.firstName").value("Existing"))
                .andExpect(jsonPath("$.data.profile.role").value("ROLE_TENANT_CUSTOMER"));

        verify(userService, never()).createProfile(any(), any(), any(), any(), any(), any());
    }
}
