package com.retrouvid.modules.auth.service;

import com.retrouvid.modules.auth.entity.PasswordResetToken;
import com.retrouvid.modules.auth.repository.PasswordResetTokenRepository;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final long TTL_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    /**
     * @return the raw token to communicate to the user (email link, SMS).
     *         Returns null if the email is unknown (silent — no enumeration).
     */
    @Transactional
    public String requestReset(String email) {
        if (email == null || email.isBlank()) return null;
        return userRepository.findByEmail(email).map(user -> {
            byte[] raw = new byte[TOKEN_BYTES];
            random.nextBytes(raw);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            String hash = sha256(token);
            tokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(hash)
                    .expiresAt(Instant.now().plus(TTL_MINUTES, ChronoUnit.MINUTES))
                    .used(false)
                    .build());
            log.info("Password reset token issued for {} (TTL {} min)", email, TTL_MINUTES);
            return token;
        }).orElse(null);
    }

    @Transactional
    public void reset(String token, String newPassword) {
        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 8) {
            throw ApiException.badRequest("Token ou mot de passe invalide");
        }
        PasswordResetToken prt = tokenRepository.findByTokenHash(sha256(token))
                .orElseThrow(() -> ApiException.badRequest("Token invalide"));
        if (prt.isUsed()) throw ApiException.badRequest("Token déjà utilisé");
        if (prt.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Token expiré");
        }
        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        prt.setUsed(true);
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
