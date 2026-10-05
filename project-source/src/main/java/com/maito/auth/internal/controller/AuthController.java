package com.maito.auth.internal.controller;

import com.maito.auth.api.dto.*;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.auth.security.UserPrincipal;
import com.maito.identity.api.dto.GlobalUserDto;
import com.maito.identity.api.service.IdentityService;
import com.maito.shared.api.ApiResponse;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication & Security Gatekeeper", description = "Endpoints for user registration, authentication, token refresh, and context resolution")
@Slf4j
public class AuthController {

    private final IdentityService identityService;
    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    public AuthController(
            IdentityService identityService,
            UserService userService,
            JwtTokenProvider tokenProvider) {
        this.identityService = identityService;
        this.userService = userService;
        this.tokenProvider = tokenProvider;
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
