package com.retrouvid.modules.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback mémoire quand Redis n'est pas configuré (dev/tests).
 * Pas de coordination multi-instance — à NE PAS utiliser en production.
 */
@Component
@ConditionalOnProperty(name = "spring.data.redis.host", havingValue = "", matchIfMissing = true)
@ConditionalOnMissingBean(RedisOtpStore.class)
public class InMemoryOtpStore implements OtpStore {

    private record Entry(String codeHash, int attempts, Instant expiresAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private String key(UUID userId, Purpose purpose) {
        return purpose.name() + ":" + userId;
    }

    private void purgeIfExpired(String k) {
        Entry e = store.get(k);
        if (e != null && e.expiresAt.isBefore(Instant.now())) store.remove(k);
    }

    @Override
    public void save(UUID userId, Purpose purpose, String codeHash, Duration ttl) {
        store.put(key(userId, purpose), new Entry(codeHash, 0, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<StoredOtp> get(UUID userId, Purpose purpose) {
        String k = key(userId, purpose);
        purgeIfExpired(k);
        Entry e = store.get(k);
        if (e == null) return Optional.empty();
        return Optional.of(new StoredOtp(e.codeHash, e.attempts));
    }

    @Override
    public int incrementAttempts(UUID userId, Purpose purpose) {
        String k = key(userId, purpose);
        Entry updated = store.computeIfPresent(k, (kk, e) ->
                new Entry(e.codeHash, e.attempts + 1, e.expiresAt));
        return updated == null ? 0 : updated.attempts;
    }

    @Override
    public void invalidate(UUID userId, Purpose purpose) {
        store.remove(key(userId, purpose));
    }
}
