package com.retrouvid.modules.auth.service;

import lombok.RequiredArgsConstructor;
import com.retrouvid.config.RedisConfig;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stockage OTP dans Redis avec TTL natif.
 * Actif uniquement si spring.data.redis.host est configuré (prod, pansy).
 * Clé : otp:{purpose}:{userId}, hash { code_hash, attempts }.
 */
@Component
@Conditional(RedisConfig.RedisHostPresent.class)
@RequiredArgsConstructor
public class RedisOtpStore implements OtpStore {

    private static final String HK_CODE = "code_hash";
    private static final String HK_ATTEMPTS = "attempts";

    private final StringRedisTemplate redis;

    private String key(UUID userId, Purpose purpose) {
        return "otp:" + purpose.name() + ":" + userId;
    }

    @Override
    public void save(UUID userId, Purpose purpose, String codeHash, Duration ttl) {
        String k = key(userId, purpose);
        HashOperations<String, Object, Object> ops = redis.opsForHash();
        ops.putAll(k, Map.of(HK_CODE, codeHash, HK_ATTEMPTS, "0"));
        redis.expire(k, ttl);
    }

    @Override
    public Optional<StoredOtp> get(UUID userId, Purpose purpose) {
        String k = key(userId, purpose);
        HashOperations<String, Object, Object> ops = redis.opsForHash();
        Object code = ops.get(k, HK_CODE);
        if (code == null) return Optional.empty();
        Object att = ops.get(k, HK_ATTEMPTS);
        int attempts = att == null ? 0 : Integer.parseInt(att.toString());
        return Optional.of(new StoredOtp(code.toString(), attempts));
    }

    @Override
    public int incrementAttempts(UUID userId, Purpose purpose) {
        return Math.toIntExact(redis.opsForHash().increment(key(userId, purpose), HK_ATTEMPTS, 1L));
    }

    @Override
    public void invalidate(UUID userId, Purpose purpose) {
        redis.delete(key(userId, purpose));
    }
}
