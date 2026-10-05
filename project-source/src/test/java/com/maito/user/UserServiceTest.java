package com.maito.user;

import com.maito.user.api.dto.AddressDto;
import com.maito.user.api.dto.CreateAddressCommand;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.internal.domain.TenantUserAddress;
import com.maito.user.internal.domain.TenantUserProfile;
import com.maito.user.internal.repository.TenantUserAddressRepository;
import com.maito.user.internal.repository.TenantUserProfileRepository;
import com.maito.user.internal.service.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private TenantUserProfileRepository profileRepository;

    @Mock
    private TenantUserAddressRepository addressRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(profileRepository, addressRepository);
    }

    @Test
    @DisplayName("createProfile creates tenant-scoped profile with dynamic role and JSONB permissions")
    void createProfile_ShouldCreateProfileWithPermissions() {
        UUID globalUserId = UUID.randomUUID();
        when(profileRepository.findByGlobalUserId(globalUserId)).thenReturn(Optional.empty());
        when(profileRepository.save(any(TenantUserProfile.class))).thenAnswer(inv -> {
            TenantUserProfile p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        TenantProfileDto profile = userService.createProfile(
                globalUserId,
                "Mithila",
                "Farmer",
                "ROLE_TENANT_ADMIN",
                List.of("cms:manage", "catalog:manage")
        );

        assertThat(profile).isNotNull();
        assertThat(profile.globalUserId()).isEqualTo(globalUserId);
        assertThat(profile.firstName()).isEqualTo("Mithila");
        assertThat(profile.lastName()).isEqualTo("Farmer");
        assertThat(profile.role()).isEqualTo("ROLE_TENANT_ADMIN");
        assertThat(profile.permissions()).containsExactly("cms:manage", "catalog:manage");
        assertThat(profile.active()).isTrue();
    }

    @Test
    @DisplayName("addAddress creates shipping address and clears previous default when isDefault is true")
    void addAddress_ShouldHandleDefaultAddressSwitching() {
        UUID profileId = UUID.randomUUID();
        TenantUserProfile mockProfile = TenantUserProfile.builder()
                .id(profileId)
                .globalUserId(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .role("ROLE_TENANT_CUSTOMER")
                .build();

        TenantUserAddress oldDefault = TenantUserAddress.builder()
                .id(UUID.randomUUID())
                .profileId(profileId)
                .isDefault(true)
                .build();

        when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));
        when(addressRepository.findByProfileIdAndIsDefaultTrue(profileId)).thenReturn(new ArrayList<>(List.of(oldDefault)));
        when(addressRepository.save(any(TenantUserAddress.class))).thenAnswer(inv -> {
            TenantUserAddress a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });

        CreateAddressCommand cmd = new CreateAddressCommand(
                "SHIPPING",
                "John Doe",
                "+919876543210",
                "Plot 42, Green Avenue",
                "Phase 2",
                "Patna",
                "Bihar",
                "800001",
                "IN",
                true
        );

        AddressDto saved = userService.addAddress(profileId, cmd);

        assertThat(saved).isNotNull();
        assertThat(saved.recipientName()).isEqualTo("John Doe");
        assertThat(saved.isDefault()).isTrue();
        assertThat(oldDefault.isDefault()).isFalse();
        verify(addressRepository).save(oldDefault);
    }
}
