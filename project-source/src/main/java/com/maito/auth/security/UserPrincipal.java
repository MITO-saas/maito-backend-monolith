package com.maito.auth.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class UserPrincipal implements UserDetails {

    private final UUID globalUserId;
    private final UUID profileId;
    private final String email;
    private final String tenantId;
    private final String role;
    private final List<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(
            UUID globalUserId,
            UUID profileId,
            String email,
            String tenantId,
            String role,
            List<String> permissions) {
        this.globalUserId = globalUserId;
        this.profileId = profileId;
        this.email = email;
        this.tenantId = tenantId;
        this.role = role;
        this.permissions = permissions != null ? permissions : List.of();

        List<GrantedAuthority> auths = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            auths.add(new SimpleGrantedAuthority(role));
            if (role.startsWith("ROLE_")) {
                auths.add(new SimpleGrantedAuthority(role.substring(5)));
            } else {
                auths.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        for (String perm : this.permissions) {
            auths.add(new SimpleGrantedAuthority(perm));
        }
        this.authorities = auths;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email != null ? email : globalUserId.toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
