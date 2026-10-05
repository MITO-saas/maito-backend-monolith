package com.maito.user.internal.controller;

import com.maito.auth.security.UserPrincipal;
import com.maito.shared.api.ApiResponse;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.user.api.dto.AddressDto;
import com.maito.user.api.dto.CreateAddressCommand;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Customer Account Management", description = "Endpoints for customer profile and shipping address management")
@Slf4j
public class AccountController {

    private final UserService userService;

    public AccountController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get authenticated customer profile")
    @PreAuthorize("hasRole('ROLE_TENANT_CUSTOMER') or hasRole('TENANT_CUSTOMER') or hasRole('ROLE_TENANT_ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<TenantProfileDto>> getProfile(
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantProfileDto profile = userService.getProfileById(principal.getProfileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Profile not found"));
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }

    @GetMapping("/addresses")
    @Operation(summary = "List shipping addresses for authenticated customer")
    @PreAuthorize("hasRole('ROLE_TENANT_CUSTOMER') or hasRole('TENANT_CUSTOMER') or hasRole('ROLE_TENANT_ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<List<AddressDto>>> getAddresses(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<AddressDto> addresses = userService.getUserAddresses(principal.getProfileId());
        return ResponseEntity.ok(ApiResponse.ok(addresses));
    }

    @PostMapping("/addresses")
    @Operation(summary = "Add shipping address for authenticated customer")
    @PreAuthorize("hasRole('ROLE_TENANT_CUSTOMER') or hasRole('TENANT_CUSTOMER') or hasRole('ROLE_TENANT_ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<AddressDto>> addAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAddressCommand cmd) {
        AddressDto address = userService.addAddress(principal.getProfileId(), cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(address));
    }
}
