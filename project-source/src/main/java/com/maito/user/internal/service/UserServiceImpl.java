package com.maito.user.internal.service;

import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.user.api.dto.AddressDto;
import com.maito.user.api.dto.CreateAddressCommand;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import com.maito.user.internal.domain.TenantUserAddress;
import com.maito.user.internal.domain.TenantUserProfile;
import com.maito.user.internal.repository.TenantUserAddressRepository;
import com.maito.user.internal.repository.TenantUserProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final TenantUserProfileRepository profileRepository;
    private final TenantUserAddressRepository addressRepository;

    public UserServiceImpl(
            TenantUserProfileRepository profileRepository,
            TenantUserAddressRepository addressRepository) {
        this.profileRepository = profileRepository;
        this.addressRepository = addressRepository;
    }

    @Override
    @Transactional
    public TenantProfileDto createProfile(UUID globalUserId, String firstName, String lastName, String role, List<String> permissions) {
        Optional<TenantUserProfile> existing = profileRepository.findByGlobalUserId(globalUserId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        TenantUserProfile profile = TenantUserProfile.builder()
                .globalUserId(globalUserId)
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .permissionMatrix(permissions != null ? new ArrayList<>(permissions) : new ArrayList<>())
                .isActive(true)
                .build();

        TenantUserProfile saved = profileRepository.save(profile);
        log.info("Created tenant user profile: id={}, globalUserId={}, role={}", saved.getId(), globalUserId, role);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantProfileDto> getProfileByGlobalUserId(UUID globalUserId) {
        return profileRepository.findByGlobalUserId(globalUserId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantProfileDto> getProfileById(UUID profileId) {
        return profileRepository.findById(profileId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressDto> getUserAddresses(UUID profileId) {
        return addressRepository.findByProfileIdOrderByCreatedAtDesc(profileId).stream()
                .map(this::toAddressDto)
                .toList();
    }

    @Override
    @Transactional
    public AddressDto addAddress(UUID profileId, CreateAddressCommand cmd) {
        TenantUserProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User profile not found: " + profileId));

        boolean isDefault = Boolean.TRUE.equals(cmd.isDefault());
        if (isDefault) {
            List<TenantUserAddress> existingDefaults = addressRepository.findByProfileIdAndIsDefaultTrue(profileId);
            for (TenantUserAddress addr : existingDefaults) {
                addr.setDefault(false);
                addressRepository.save(addr);
            }
        }

        TenantUserAddress address = TenantUserAddress.builder()
                .profileId(profile.getId())
                .addressType(cmd.addressType() != null ? cmd.addressType() : "SHIPPING")
                .recipientName(cmd.recipientName())
                .phone(cmd.phone())
                .addressLine1(cmd.addressLine1())
                .addressLine2(cmd.addressLine2())
                .city(cmd.city())
                .state(cmd.state())
                .postalCode(cmd.postalCode())
                .countryCode(cmd.countryCode() != null ? cmd.countryCode() : "IN")
                .isDefault(isDefault)
                .build();

        TenantUserAddress saved = addressRepository.save(address);
        log.info("Created address: id={}, profileId={}, type={}", saved.getId(), profileId, saved.getAddressType());
        return toAddressDto(saved);
    }

    private TenantProfileDto toDto(TenantUserProfile p) {
        return new TenantProfileDto(
                p.getId(),
                p.getGlobalUserId(),
                p.getFirstName(),
                p.getLastName(),
                p.getRole(),
                p.getPermissionMatrix() != null ? List.copyOf(p.getPermissionMatrix()) : List.of(),
                p.isActive(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private AddressDto toAddressDto(TenantUserAddress a) {
        return new AddressDto(
                a.getId(),
                a.getProfileId(),
                a.getAddressType(),
                a.getRecipientName(),
                a.getPhone(),
                a.getAddressLine1(),
                a.getAddressLine2(),
                a.getCity(),
                a.getState(),
                a.getPostalCode(),
                a.getCountryCode(),
                a.isDefault(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
