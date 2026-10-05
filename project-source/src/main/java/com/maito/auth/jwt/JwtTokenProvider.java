package com.maito.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${maito.security.jwt.secret:maito-enterprise-super-secure-secret-key-32-bytes-long!}") String secret,
            @Value("${maito.security.jwt.access-token-expiration-ms:900000}") long accessTokenExpirationMs,
            @Value("${maito.security.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(
            UUID globalUserId,
            String tenantId,
            String role,
            List<String> permissions,
            UUID profileId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(globalUserId.toString())
                .claim("tenantId", tenantId)
                .claim("role", role)
                .claim("permissions", permissions != null ? permissions : List.of())
                .claim("profileId", profileId != null ? profileId.toString() : null)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generateTokenWithCustomExpiry(
            UUID globalUserId,
            String tenantId,
            String role,
            List<String> permissions,
            UUID profileId,
            long ttlMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ttlMs);

        return Jwts.builder()
                .subject(globalUserId.toString())
                .claim("tenantId", tenantId)
                .claim("role", role)
                .claim("permissions", permissions != null ? permissions : List.of())
                .claim("profileId", profileId != null ? profileId.toString() : null)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UUID globalUserId, String tenantId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(globalUserId.toString())
                .claim("tenantId", tenantId)
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT token validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public UUID getGlobalUserId(String token) {
        return UUID.fromString(getClaims(token).getSubject());
    }

    public String getTenantId(String token) {
        return getClaims(token).get("tenantId", String.class);
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(String token) {
        return getClaims(token).get("permissions", List.class);
    }

    public UUID getProfileId(String token) {
        String pid = getClaims(token).get("profileId", String.class);
        return pid != null ? UUID.fromString(pid) : null;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }
}
