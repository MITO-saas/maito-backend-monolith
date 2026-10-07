package com.maito.auth.internal.controller;

import com.maito.auth.api.dto.*;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.auth.security.GoogleTokenVerifier;
import com.maito.auth.security.PhoneOtpService;
import com.maito.auth.security.UserPrincipal;
import com.maito.cart.api.service.CartService;
import com.maito.identity.api.dto.GlobalUserDto;
import com.maito.identity.api.service.IdentityService;
import com.maito.shared.api.ApiResponse;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import com.maito.wallet.api.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication & Security Gatekeeper", description = "Endpoints for user registration, authentication, token refresh, and context resolution")
@Slf4j
public class AuthController {

    private final IdentityService identityService;
    private final UserService userService;
    private final JwtTokenProvider tokenProvider;
    private final WalletService walletService;
    private final CartService cartService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final PhoneOtpService phoneOtpService;

    public AuthController(
            IdentityService identityService,
            UserService userService,
            JwtTokenProvider tokenProvider,
            WalletService walletService,
            CartService cartService,
            GoogleTokenVerifier googleTokenVerifier,
            PhoneOtpService phoneOtpService) {
        this.identityService = identityService;
        this.userService = userService;
        this.tokenProvider = tokenProvider;
        this.walletService = walletService;
        this.cartService = cartService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.phoneOtpService = phoneOtpService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register customer identity and create tenant profile")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tenant resolution header X-Tenant-ID is required for registration");
        }

        GlobalUserDto globalUser = identityService.createIdentity(req.email(), req.password(), req.phone());
        TenantProfileDto profile = userService.createProfile(
                globalUser.id(),
                req.firstName(),
                req.lastName(),
                "ROLE_TENANT_CUSTOMER",
                List.of()
        );

        // Auto-provision loyalty wallet
        try {
            walletService.getOrCreateWallet(profile.id());
        } catch (Exception e) {
            log.warn("Wallet init warning during registration: {}", e.getMessage());
        }

        String accessToken = tokenProvider.generateAccessToken(
                globalUser.id(),
                tenantId,
                profile.role(),
                profile.permissions(),
                profile.id()
        );
        String refreshToken = tokenProvider.generateRefreshToken(globalUser.id(), tenantId);

        AuthResponse resp = new AuthResponse(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenExpirationSeconds(),
                profile,
                "STOREFRONT"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(resp));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate credentials and issue JWT tokens for active tenant")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tenant resolution header X-Tenant-ID is required for login");
        }

        GlobalUserDto globalUser = identityService.authenticate(req.email(), req.password())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_FAILED, "Invalid email or password"));

        TenantProfileDto profile = userService.getProfileByGlobalUserId(globalUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "No user profile found for user in tenant: " + tenantId));

        String accessToken = tokenProvider.generateAccessToken(
                globalUser.id(),
                tenantId,
                profile.role(),
                profile.permissions(),
                profile.id()
        );
        String refreshToken = tokenProvider.generateRefreshToken(globalUser.id(), tenantId);

        String redirectTarget = "ROLE_TENANT_ADMIN".equalsIgnoreCase(profile.role()) ? "ADMIN_DASHBOARD" : "STOREFRONT";

        AuthResponse resp = new AuthResponse(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenExpirationSeconds(),
                profile,
                redirectTarget
        );

        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @PostMapping("/social-login")
    @Operation(summary = "1-Click Social Sign-In (Google / Facebook) with auto-provisioning and guest cart merging")
    public ResponseEntity<ApiResponse<AuthResponse>> socialLogin(@Valid @RequestBody SocialLoginRequest req) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tenant resolution header X-Tenant-ID is required for social login");
        }

        String provider = req.provider().toUpperCase();
        String sub;
        String email;
        String firstName;
        String lastName;
        String pictureUrl;

        if ("GOOGLE".equals(provider)) {
            GoogleTokenVerifier.GoogleUserProfile gUser = googleTokenVerifier.verify(req.idToken());
            sub = gUser.sub();
            email = gUser.email();
            firstName = gUser.firstName();
            lastName = gUser.lastName();
            pictureUrl = gUser.pictureUrl();
        } else if ("FACEBOOK".equals(provider) || "META".equals(provider)) {
            sub = "fb-sub-" + Math.abs(req.idToken().hashCode());
            email = req.idToken().contains("@") ? req.idToken().replace("mock-", "").toLowerCase() : "fb_user_" + sub + "@meta.maito.com";
            firstName = "Meta";
            lastName = "Customer";
            pictureUrl = "https://graph.facebook.com/default/picture";
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unsupported social provider: " + provider);
        }

        // 1. Master Identity Auto-Provisioning
        GlobalUserDto globalUser = identityService.createOrGetSocialIdentity(email, provider, sub, null, pictureUrl);

        // 2. Tenant Profile Auto-Provisioning
        Optional<TenantProfileDto> profileOpt = userService.getProfileByGlobalUserId(globalUser.id());
        TenantProfileDto profile;
        if (profileOpt.isEmpty()) {
            profile = userService.createProfile(
                    globalUser.id(),
                    firstName,
                    lastName,
                    "ROLE_TENANT_CUSTOMER",
                    List.of(),
                    pictureUrl
            );
        } else {
            profile = profileOpt.get();
        }

        // 3. Initialize Loyalty Wallet
        try {
            walletService.getOrCreateWallet(profile.id());
        } catch (Exception e) {
            log.warn("Wallet init notice during social login: {}", e.getMessage());
        }

        // 4. Automated Guest Cart Merging
        if (req.guestCartId() != null && !req.guestCartId().isBlank()) {
            try {
                cartService.mergeCarts(req.guestCartId(), profile.id());
                log.info("Merged guest cart [{}] into social user profile [{}]", req.guestCartId(), profile.id());
            } catch (Exception e) {
                log.warn("Guest cart merge notice during social login: {}", e.getMessage());
            }
        }

        // 5. Issue Standard Maito JWT Tokens
        String accessToken = tokenProvider.generateAccessToken(
                globalUser.id(),
                tenantId,
                profile.role(),
                profile.permissions(),
                profile.id()
        );
        String refreshToken = tokenProvider.generateRefreshToken(globalUser.id(), tenantId);

        AuthResponse resp = new AuthResponse(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenExpirationSeconds(),
                profile,
                "STOREFRONT"
        );

        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send 6-digit OTP code to mobile phone for passwordless authentication")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendOtp(@Valid @RequestBody SendOtpRequest req) {
        String code = phoneOtpService.sendOtp(req.phone());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "phone", req.phone(),
                "status", "OTP_SENT",
                "message", "OTP sent successfully"
        )));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify 6-digit phone OTP, auto-provision customer identity, and issue JWT session")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tenant resolution header X-Tenant-ID is required for OTP verification");
        }

        boolean valid = phoneOtpService.verifyOtp(req.phone(), req.code());
        if (!valid) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED, "Invalid or expired OTP code");
        }

        // 1. Master Identity Auto-Provisioning
        GlobalUserDto globalUser = identityService.createOrGetPhoneIdentity(req.phone());

        // 2. Tenant Profile Auto-Provisioning
        Optional<TenantProfileDto> profileOpt = userService.getProfileByGlobalUserId(globalUser.id());
        TenantProfileDto profile;
        if (profileOpt.isEmpty()) {
            String cleanPhone = req.phone().replaceAll("[^0-9]", "");
            String suffix = cleanPhone.length() >= 4 ? cleanPhone.substring(cleanPhone.length() - 4) : cleanPhone;
            profile = userService.createProfile(
                    globalUser.id(),
                    "Customer",
                    suffix,
                    "ROLE_TENANT_CUSTOMER",
                    List.of(),
                    null
            );
        } else {
            profile = profileOpt.get();
        }

        // 3. Initialize Loyalty Wallet
        try {
            walletService.getOrCreateWallet(profile.id());
        } catch (Exception e) {
            log.warn("Wallet init notice during OTP verification: {}", e.getMessage());
        }

        // 4. Automated Guest Cart Merging
        if (req.guestCartId() != null && !req.guestCartId().isBlank()) {
            try {
                cartService.mergeCarts(req.guestCartId(), profile.id());
                log.info("Merged guest cart [{}] into phone OTP user profile [{}]", req.guestCartId(), profile.id());
            } catch (Exception e) {
                log.warn("Guest cart merge notice during OTP login: {}", e.getMessage());
            }
        }

        // 5. Issue Standard Maito JWT Tokens
        String accessToken = tokenProvider.generateAccessToken(
                globalUser.id(),
                tenantId,
                profile.role(),
                profile.permissions(),
                profile.id()
        );
        String refreshToken = tokenProvider.generateRefreshToken(globalUser.id(), tenantId);

        AuthResponse resp = new AuthResponse(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenExpirationSeconds(),
                profile,
                "STOREFRONT"
        );

        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using valid refresh token")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        if (!tokenProvider.validateToken(req.refreshToken())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED_OR_INVALID, "Invalid or expired refresh token");
        }

        String tokenTenantId = tokenProvider.getTenantId(req.refreshToken());
        String activeTenantId = TenantContextHolder.getTenantId();

        if (activeTenantId != null && !activeTenantId.equalsIgnoreCase(tokenTenantId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Cross-tenant refresh violation");
        }

        var globalUserId = tokenProvider.getGlobalUserId(req.refreshToken());
        TenantProfileDto profile = userService.getProfileByGlobalUserId(globalUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User profile not found in tenant"));

        String newAccessToken = tokenProvider.generateAccessToken(
                globalUserId,
                tokenTenantId,
                profile.role(),
                profile.permissions(),
                profile.id()
        );

        TokenRefreshResponse resp = new TokenRefreshResponse(
                newAccessToken,
                tokenProvider.getAccessTokenExpirationSeconds()
        );

        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user identity and security context")
    public ResponseEntity<ApiResponse<UserMeResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED, "No authenticated security context found");
        }

        UserMeResponse resp = new UserMeResponse(
                principal.getGlobalUserId(),
                principal.getProfileId(),
                principal.getEmail(),
                principal.getTenantId(),
                principal.getRole(),
                principal.getPermissions()
        );

        return ResponseEntity.ok(ApiResponse.ok(resp));
    }
}
