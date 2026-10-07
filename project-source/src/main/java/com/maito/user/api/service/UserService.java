package com.maito.user.api.service;

import com.maito.user.api.dto.AddressDto;
import com.maito.user.api.dto.CreateAddressCommand;
import com.maito.user.api.dto.TenantProfileDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public domain contract for tenant-isolated user profiles and addresses.
 */
public interface UserService {

    TenantProfileDto createProfile(UUID globalUserId, String firstName, String lastName, String role, List<String> permissions);

    TenantProfileDto createProfile(UUID globalUserId, String firstName, String lastName, String role, List<String> permissions, String avatarUrl);

    Optional<TenantProfileDto> getProfileByGlobalUserId(UUID globalUserId);

    Optional<TenantProfileDto> getProfileById(UUID profileId);

    List<AddressDto> getUserAddresses(UUID profileId);

    AddressDto addAddress(UUID profileId, CreateAddressCommand cmd);
}
