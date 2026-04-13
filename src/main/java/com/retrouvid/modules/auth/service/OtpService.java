package com.retrouvid.modules.auth.service;

import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final OtpStore store;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.otp.expose-code:false}")
    private boolean exposeCode;

    /**
     * Génère et envoie un OTP à 6 chiffres par email.
     * @return le code en clair si app.otp.expose-code=true (dev/tests), sinon null.
     */
    @Transactional
    public String sendEmailVerification(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw ApiException.badRequest("Aucune adresse email pour cet utilisateur");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        store.save(user.getId(), OtpStore.Purpose.EMAIL_VERIFICATION, sha256(code), TTL);

        String subject = "RetrouvID — Code de vérification";
        String body = """
                <p>Bonjour,</p>
                <p>Voici votre code de vérification RetrouvID :</p>
                <p style="font-size:24px;font-weight:bold;letter-spacing:4px;">%s</p>
                <p>Ce code expire dans %d minutes.</p>
                <p>Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.</p>
                """.formatted(code, TTL.toMinutes());
        emailService.send(user.getEmail(), subject, body);

        return exposeCode ? code : null;
    }

    @Transactional
    public User verifyEmail(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw ApiException.badRequest("Email et code requis");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.badRequest("Code invalide"));

        OtpStore.StoredOtp stored = store.get(user.getId(), OtpStore.Purpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> ApiException.badRequest("Code invalide ou expiré"));

        if (stored.attempts() >= MAX_ATTEMPTS) {
            store.invalidate(user.getId(), OtpStore.Purpose.EMAIL_VERIFICATION);
            throw ApiException.badRequest("Trop de tentatives, demandez un nouveau code");
        }
        if (!stored.codeHash().equals(sha256(code))) {
            store.incrementAttempts(user.getId(), OtpStore.Purpose.EMAIL_VERIFICATION);
            throw ApiException.badRequest("Code invalide");
        }
        store.invalidate(user.getId(), OtpStore.Purpose.EMAIL_VERIFICATION);
        user.setVerified(true);
        log.info("Email vérifié pour {}", email);
        return user;
    }

    private String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
