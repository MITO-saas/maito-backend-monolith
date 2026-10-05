package com.maito.auth.internal.seed;

import com.maito.identity.api.dto.GlobalUserDto;
import com.maito.identity.api.service.IdentityService;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class MitoCrunchUserSeeder implements CommandLineRunner {

    private final IdentityService identityService;
    private final UserService userService;

    public MitoCrunchUserSeeder(IdentityService identityService, UserService userService) {
        this.identityService = identityService;
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        log.info("Starting automated seed for Mito Crunch default accounts...");
        TenantContext mitoContext = new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch");

        try {
            // 1. Seed global identities in Master DB
            GlobalUserDto adminUser = seedGlobalIdentity("admin@mitocrunch.com", "CrunchAdmin@2026", "+919876543210");
            GlobalUserDto customerUser = seedGlobalIdentity("customer@mitocrunch.com", "Customer@2026", "+919876543211");

            // 2. Seed tenant profiles in Mito Crunch Tenant DB
            TenantContextHolder.set(mitoContext);
            try {
                if (adminUser != null) {
                    userService.createProfile(adminUser.id(), "Crunch", "Admin", "ROLE_TENANT_ADMIN", List.of("cms:manage", "catalog:manage", "orders:manage"));
                    log.info("Verified tenant profile for admin@mitocrunch.com (ROLE_TENANT_ADMIN)");
                }
                if (customerUser != null) {
                    userService.createProfile(customerUser.id(), "Crunch", "Customer", "ROLE_TENANT_CUSTOMER", List.of());
                    log.info("Verified tenant profile for customer@mitocrunch.com (ROLE_TENANT_CUSTOMER)");
                }
            } finally {
                TenantContextHolder.clear();
            }

            log.info("Mito Crunch default accounts successfully verified/seeded.");
        } catch (Exception e) {
            log.error("Failed to seed default accounts for Mito Crunch: {}", e.getMessage(), e);
        }
    }

    private GlobalUserDto seedGlobalIdentity(String email, String password, String phone) {
        try {
            Optional<GlobalUserDto> existingUser = identityService.findByEmail(email);
            if (existingUser.isPresent()) {
                log.info("Global user identity already exists: [{}]", email);
                return existingUser.get();
            }
            GlobalUserDto created = identityService.createIdentity(email, password, phone);
            log.info("Seeded global user identity in master DB: [{}]", email);
            return created;
        } catch (Exception e) {
            log.warn("Notice during global user seed for [{}]: {}", email, e.getMessage());
            return identityService.findByEmail(email).orElse(null);
        }
    }
}
