package com.example.test.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private static final int MIN_JWT_SECRET_LENGTH = 32;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.jwt.expiration-ms:3600000}")
    private long jwtExpirationMs;

    public TokenDetails generateToken(String username) {
        validateConfiguration();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(jwtExpirationMs);
        String token = Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey())
                .compact();
        return new TokenDetails(token, expiresAt);
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equalsIgnoreCase(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    private Claims extractAllClaims(String token) {
        try {
            validateConfiguration();
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (SecurityException ex) {
            throw new IllegalArgumentException("Invalid JWT signature", ex);
        }
    }

    private SecretKey signingKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private void validateConfiguration() {
        if (jwtSecret == null || jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
        if (jwtExpirationMs <= 0) {
            throw new IllegalStateException("JWT expiration must be greater than zero");
        }
    }

    public record TokenDetails(String token, Instant expiresAt) {
    }
}
