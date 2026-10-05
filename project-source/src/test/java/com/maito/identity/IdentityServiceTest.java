package com.maito.identity;

import com.maito.identity.api.dto.GlobalUserDto;
import com.maito.identity.internal.domain.GlobalUser;
import com.maito.identity.internal.repository.GlobalUserRepository;
import com.maito.identity.internal.service.IdentityServiceImpl;
import com.maito.identity.internal.service.MasterIdentityTxService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private GlobalUserRepository globalUserRepository;

    private PasswordEncoder passwordEncoder;
    private MasterIdentityTxService txService;
    private IdentityServiceImpl identityService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        txService = new MasterIdentityTxService(globalUserRepository, passwordEncoder);
        identityService = new IdentityServiceImpl(txService);
    }

    @Test
    @DisplayName("createIdentity hashes raw password with BCrypt (strength 12) and creates active user")
    void createIdentity_ShouldHashPasswordWithBcrypt() {
        when(globalUserRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(globalUserRepository.save(any(GlobalUser.class))).thenAnswer(invocation -> {
            GlobalUser user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        GlobalUserDto dto = identityService.createIdentity("user@example.com", "PlainSecret123", "+919876543210");

        assertThat(dto).isNotNull();
        assertThat(dto.email()).isEqualTo("user@example.com");
        assertThat(dto.accountStatus()).isEqualTo("ACTIVE");
        assertThat(dto.failedLoginAttempts()).isEqualTo(0);

        verify(globalUserRepository).save(argThat(user ->
                passwordEncoder.matches("PlainSecret123", user.getPasswordHash()) &&
                user.getPasswordHash().startsWith("$2a$12$")
        ));
    }

    @Test
    @DisplayName("createIdentity throws USER_ALREADY_EXISTS when email already registered")
    void createIdentity_ShouldThrowWhenEmailExists() {
        when(globalUserRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> identityService.createIdentity("existing@example.com", "Secret123", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("authenticate returns GlobalUserDto on valid credentials and resets failed attempts")
    void authenticate_ShouldSucceedOnValidCredentials() {
        String hashed = passwordEncoder.encode("ValidPassword@2026");
        GlobalUser user = GlobalUser.builder()
                .id(UUID.randomUUID())
                .email("auth@example.com")
                .passwordHash(hashed)
                .accountStatus("ACTIVE")
                .failedLoginAttempts(2)
                .build();

        when(globalUserRepository.findByEmail("auth@example.com")).thenReturn(Optional.of(user));
        when(globalUserRepository.save(any(GlobalUser.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<GlobalUserDto> result = identityService.authenticate("auth@example.com", "ValidPassword@2026");

        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo("auth@example.com");
        assertThat(result.get().failedLoginAttempts()).isEqualTo(0);
        assertThat(result.get().lastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("authenticate increments failed attempts on invalid password and locks account at 5th attempt")
    void authenticate_ShouldIncrementFailedAttemptsAndLock() {
        String hashed = passwordEncoder.encode("CorrectPassword");
        GlobalUser user = GlobalUser.builder()
                .id(UUID.randomUUID())
                .email("lock@example.com")
                .passwordHash(hashed)
                .accountStatus("ACTIVE")
                .failedLoginAttempts(4)
                .build();

        when(globalUserRepository.findByEmail("lock@example.com")).thenReturn(Optional.of(user));
        when(globalUserRepository.save(any(GlobalUser.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<GlobalUserDto> result = identityService.authenticate("lock@example.com", "WrongPassword");

        assertThat(result).isEmpty();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getAccountStatus()).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("authenticate throws ACCOUNT_LOCKED when account status is LOCKED")
    void authenticate_ShouldThrowWhenAccountAlreadyLocked() {
        GlobalUser user = GlobalUser.builder()
                .id(UUID.randomUUID())
                .email("locked@example.com")
                .passwordHash("anyHash")
                .accountStatus("LOCKED")
                .failedLoginAttempts(5)
                .build();

        when(globalUserRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> identityService.authenticate("locked@example.com", "AnyPassword"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_LOCKED);
    }
}
