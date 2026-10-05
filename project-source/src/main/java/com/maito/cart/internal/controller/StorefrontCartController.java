package com.maito.cart.internal.controller;

import com.maito.auth.security.UserPrincipal;
import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.dto.MergeCartCommand;
import com.maito.cart.api.dto.UpdateCartItemCommand;
import com.maito.cart.api.service.CartService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Storefront Cart", description = "Shopping cart state engine for guests and customers")
public class StorefrontCartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get or create active shopping cart")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @RequestHeader(value = "X-Cart-ID", required = false) String guestCartId,
            @RequestParam(required = false, defaultValue = "INR") String currency,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new com.maito.shared.exception.BusinessException(com.maito.shared.exception.ErrorCode.ACCESS_DENIED, "Customer login required for cart merge");
        }
        UUID customerProfileId = principal.getProfileId();
        CartResponse response = cartService.getOrCreateCart(guestCartId, customerProfileId, currency);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/items")
    @Operation(summary = "Add SKU variant to active cart")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @RequestHeader(value = "X-Cart-ID", required = false) String guestCartId,
            @RequestParam(required = false, defaultValue = "INR") String currency,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddCartItemCommand cmd
    ) {
        UUID customerProfileId = (principal != null) ? principal.getProfileId() : null;
        CartResponse cart = cartService.getOrCreateCart(guestCartId, customerProfileId, currency);
        CartResponse updated = cartService.addItem(cart.id(), cmd);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update cart line item quantity")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @RequestHeader(value = "X-Cart-ID", required = false) String guestCartId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCartItemCommand cmd
    ) {
        UUID customerProfileId = (principal != null) ? principal.getProfileId() : null;
        CartResponse cart = cartService.getOrCreateCart(guestCartId, customerProfileId, "INR");
        CartResponse updated = cartService.updateItem(cart.id(), itemId, cmd.quantity());
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove cart line item")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @RequestHeader(value = "X-Cart-ID", required = false) String guestCartId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID customerProfileId = (principal != null) ? principal.getProfileId() : null;
        CartResponse cart = cartService.getOrCreateCart(guestCartId, customerProfileId, "INR");
        CartResponse updated = cartService.removeItem(cart.id(), itemId);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @PostMapping("/merge")
    @Operation(summary = "Merge guest cart into authenticated customer profile")
    public ResponseEntity<ApiResponse<CartResponse>> mergeCart(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MergeCartCommand cmd
    ) {
        UUID customerProfileId = (principal != null) ? principal.getProfileId() : null;
        CartResponse merged = cartService.mergeCarts(cmd.guestCartId(), customerProfileId);
        return ResponseEntity.ok(ApiResponse.ok(merged));
    }
}
