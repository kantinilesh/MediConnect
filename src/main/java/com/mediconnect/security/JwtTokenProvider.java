package com.mediconnect.security;

import com.mediconnect.config.JwtConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stateless JWT utility: generate, parse, and validate access + refresh tokens.
 *
 * <p>Tokens carry a {@code token_type} claim ({@code "ACCESS"} or {@code "REFRESH"})
 * so the filter can reject a refresh token being used as an access token and vice-versa.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private static final String CLAIM_ROLES       = "roles";
    private static final String CLAIM_TOKEN_TYPE  = "token_type";
    private static final String TYPE_ACCESS       = "ACCESS";
    private static final String TYPE_REFRESH      = "REFRESH";

    private final JwtConfig jwtConfig;

    // ── Access Token ──────────────────────────────────────────────────────────

    /**
     * Generates a signed ACCESS JWT from a Spring Security {@link Authentication}.
     */
    public String generateAccessToken(Authentication authentication) {
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        return buildToken(authentication.getName(), roles, jwtConfig.getExpiration(), TYPE_ACCESS);
    }

    /**
     * Generates a signed ACCESS JWT directly from a {@link UserDetails} object.
     * Used when refreshing tokens without re-authenticating.
     */
    public String generateAccessTokenFromUserDetails(UserDetails userDetails) {
        String roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        return buildToken(userDetails.getUsername(), roles, jwtConfig.getExpiration(), TYPE_ACCESS);
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    /**
     * Generates a unique random string used as the refresh token value stored in DB.
     * (We store refresh tokens in DB so they can be explicitly revoked.)
     */
    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ── Token Parsing ─────────────────────────────────────────────────────────

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRoles(String token) {
        return parseClaims(token).get(CLAIM_ROLES, String.class);
    }

    public String extractTokenType(String token) {
        return parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validates an access token (must be present, non-expired, and of type ACCESS).
     */
    public boolean validateAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                log.warn("JWT token_type is not ACCESS");
                return false;
            }
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT access token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT signature invalid: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Backward-compatible alias used by {@link JwtAuthenticationFilter}.
     */
    public boolean validateToken(String token) {
        return validateAccessToken(token);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String buildToken(String subject, String roles, long expirationMs, String tokenType) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(subject)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (RuntimeException e) {
            // Secret is not valid base-64 — derive key directly from UTF-8 bytes.
            // Note: HMAC-SHA256 requires at least 32 bytes; the secret must be long enough.
            return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes());
        }
    }
}
