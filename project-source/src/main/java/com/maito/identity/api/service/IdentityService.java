package com.maito.identity.api.service;

import com.maito.identity.api.dto.GlobalUserDto;
import java.util.Optional;
import java.util.UUID;

/**
 * Public domain contract for Master Control Plane Identity management.
 */
public interface IdentityService {

    GlobalUserDto createIdentity(String email, String rawPassword, String phone);

    Optional<GlobalUserDto> authenticate(String email, String rawPassword);

    Optional<GlobalUserDto> findByEmail(String email);

    Optional<GlobalUserDto> findById(UUID id);
}
