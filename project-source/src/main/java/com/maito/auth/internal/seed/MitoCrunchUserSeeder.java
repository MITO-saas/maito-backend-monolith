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
import java.util.UUID;

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
        log.info("Starting automated seed for multi-tenant default accounts...");
        TenantContext mitoContext = new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch");
        TenantContext solarContext = new TenantContext("vijiya_solar", "vijiyasolar", "IN", "INR", "en_IN", "db_vijiyasolar");
        TenantContext everritesContext = new TenantContext("everrites", "everrites", "IN", "INR", "en_IN", "db_everrites");

        try {
            // 1. Seed global identities in Master DB
            GlobalUserDto adminMito = seedGlobalIdentity("admin@mitocrunch.com", "Admin@2026", "+919876543210");
            GlobalUserDto custMito = seedGlobalIdentity("customer@mitocrunch.com", "Customer@2026", "+919876543211");

            GlobalUserDto adminSolar = seedGlobalIdentity("admin@vijiyasolar.com", "Admin@2026", "+919876543220");
            GlobalUserDto custSolar = seedGlobalIdentity("customer@vijiyasolar.com", "Customer@2026", "+919876543221");

            GlobalUserDto adminEver = seedGlobalIdentity("admin@everrites.com", "Admin@2026", "+919876543230");
            GlobalUserDto custEver = seedGlobalIdentity("customer@everrites.com", "Customer@2026", "+919876543231");

            List<String> adminPerms = List.of("cms:manage", "catalog:manage", "orders:manage", "logistics:manage");

            // 2. Seed Mito Crunch Tenant DB
            TenantContextHolder.set(mitoContext);
            try {
                if (adminMito != null) seedTenantProfile(adminMito.id(), "Crunch", "Admin", "ROLE_TENANT_ADMIN", adminPerms);
                if (custMito != null) seedTenantProfile(custMito.id(), "Crunch", "Customer", "ROLE_TENANT_CUSTOMER", List.of());
            } finally {
                TenantContextHolder.clear();
            }

            // 3. Seed Vijiya Solar Tenant DB
            TenantContextHolder.set(solarContext);
            try {
                if (adminSolar != null) seedTenantProfile(adminSolar.id(), "Solar", "Admin", "ROLE_TENANT_ADMIN", adminPerms);
                if (custSolar != null) seedTenantProfile(custSolar.id(), "Solar", "Customer", "ROLE_TENANT_CUSTOMER", List.of());
                if (adminMito != null) seedTenantProfile(adminMito.id(), "Mito", "Admin", "ROLE_TENANT_ADMIN", adminPerms);
                if (custMito != null) seedTenantProfile(custMito.id(), "Mito", "Customer", "ROLE_TENANT_CUSTOMER", List.of());
            } finally {
                TenantContextHolder.clear();
            }

            // 4. Seed Everrites Tenant DB
            TenantContextHolder.set(everritesContext);
            try {
                if (adminEver != null) seedTenantProfile(adminEver.id(), "Everrites", "Admin", "ROLE_TENANT_ADMIN", adminPerms);
                if (custEver != null) seedTenantProfile(custEver.id(), "Everrites", "Customer", "ROLE_TENANT_CUSTOMER", List.of());
                if (adminMito != null) seedTenantProfile(adminMito.id(), "Mito", "Admin", "ROLE_TENANT_ADMIN", adminPerms);
                if (custMito != null) seedTenantProfile(custMito.id(), "Mito", "Customer", "ROLE_TENANT_CUSTOMER", List.of());
            } finally {
                TenantContextHolder.clear();
            }

            log.info("Multi-tenant default accounts successfully verified/seeded.");
        } catch (Exception e) {
            log.error("Failed to seed default accounts: {}", e.getMessage(), e);
        }
    }

    private void seedTenantProfile(UUID globalUserId, String firstName, String lastName, String role, List<String> permissions) {
        if (globalUserId == null) return;
        try {
            if (userService.getProfileByGlobalUserId(globalUserId).isEmpty()) {
                userService.createProfile(globalUserId, firstName, lastName, role, permissions);
                log.info("Created tenant profile for global user [{}] with role [{}]", globalUserId, role);
            }
        } catch (Exception e) {
            log.debug("Tenant profile check notice for [{}]: {}", globalUserId, e.getMessage());
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
