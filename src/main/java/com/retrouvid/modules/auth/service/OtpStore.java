package com.retrouvid.modules.auth.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface OtpStore {

    enum Purpose {
        EMAIL_VERIFICATION
    }

    record StoredOtp(String codeHash, int attempts) {}

    void save(UUID userId, Purpose purpose, String codeHash, Duration ttl);

    Optional<StoredOtp> get(UUID userId, Purpose purpose);

    int incrementAttempts(UUID userId, Purpose purpose);

    void invalidate(UUID userId, Purpose purpose);
}
