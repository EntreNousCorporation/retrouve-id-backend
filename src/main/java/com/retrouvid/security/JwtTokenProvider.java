package com.retrouvid.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessMinutes;
    private final long refreshDays;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration-minutes}") long accessMinutes,
            @Value("${app.jwt.refresh-expiration-days}") long refreshDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
    }

    public String generateAccessToken(UUID userId, String role) {
        return build(userId, role, "access", Instant.now().plus(accessMinutes, ChronoUnit.MINUTES));
    }

    public String generateRefreshToken(UUID userId) {
        return build(userId, null, "refresh", Instant.now().plus(refreshDays, ChronoUnit.DAYS));
    }

    private String build(UUID userId, String role, String type, Instant expiry) {
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("type", type)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(key);
        if (role != null) builder.claim("role", role);
        return builder.compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public boolean isRefresh(String token) {
        return "refresh".equals(parse(token).get("type", String.class));
    }
}
