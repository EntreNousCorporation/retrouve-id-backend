package com.retrouvid.shared.hashing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class HashingService {

    private final String salt;

    public HashingService(@Value("${app.hashing.salt:retrouvid-dev-salt-change-me}") String salt) {
        this.salt = salt;
    }

    public String hash(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("\\s+", "").toUpperCase();
        if (normalized.isEmpty()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((salt + ":" + normalized).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 introuvable", e);
        }
    }

    public boolean matches(String value, String expectedHash) {
        if (value == null || expectedHash == null) return false;
        String computed = hash(value);
        return computed != null && constantTimeEquals(computed, expectedHash);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
