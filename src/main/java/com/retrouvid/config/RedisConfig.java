package com.retrouvid.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Création manuelle du client Redis, uniquement si spring.data.redis.host est renseigné.
 * Permet de ne pas instancier Redis en dev/tests (fallback InMemoryOtpStore).
 */
@Configuration
@Conditional(RedisConfig.RedisHostPresent.class)
public class RedisConfig {

    /** Vrai uniquement si spring.data.redis.host est renseigné et non vide. */
    public static class RedisHostPresent implements org.springframework.context.annotation.Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
            String host = ctx.getEnvironment().getProperty("spring.data.redis.host");
            return host != null && !host.isBlank();
        }
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        config.setDatabase(database);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
