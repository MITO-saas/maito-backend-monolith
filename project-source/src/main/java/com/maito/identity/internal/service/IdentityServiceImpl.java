package com.maito.identity.internal.service;

import com.maito.identity.api.dto.GlobalUserDto;
import com.maito.identity.api.service.IdentityService;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdentityServiceImpl implements IdentityService {

    private final MasterIdentityTxService txService;

    public IdentityServiceImpl(MasterIdentityTxService txService) {
        this.txService = txService;
    }

    private <T> T inMasterContext(Supplier<T> action) {
        TenantContext prev = TenantContextHolder.get();
        try {
            TenantContextHolder.clear();
            return action.get();
        } finally {
            if (prev != null) {
                TenantContextHolder.set(prev);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    @Override
    public GlobalUserDto createIdentity(String email, String rawPassword, String phone) {
        return inMasterContext(() -> txService.createIdentity(email, rawPassword, phone));
    }

    @Override
    public Optional<GlobalUserDto> authenticate(String email, String rawPassword) {
        return inMasterContext(() -> txService.authenticate(email, rawPassword));
    }

    @Override
    public Optional<GlobalUserDto> findByEmail(String email) {
        return inMasterContext(() -> txService.findByEmail(email));
    }

    @Override
    public Optional<GlobalUserDto> findById(UUID id) {
        return inMasterContext(() -> txService.findById(id));
    }
}
