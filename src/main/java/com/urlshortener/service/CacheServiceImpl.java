package com.urlshortener.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.config.AppProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheServiceImpl implements CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    private static final String URL_CACHE_PREFIX = "url:";

    /** Reads a cached URL; missing, legacy or malformed entries are cache misses. */
    @Override
    @CircuitBreaker(name = "redis", fallbackMethod = "getUrlFallback")
    public Optional<CachedUrl> getUrl(String shortCode) {
        String key = URL_CACHE_PREFIX + shortCode;
        Object value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return Optional.empty();
        }

        try {
            String json = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            // Legacy entries cannot distinguish permanent URLs from expiring URLs.
            if (!objectMapper.readTree(json).has("expiresAt")) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CachedUrl.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached URL: key={}", key, e);
            return Optional.empty();
        }
    }

    /** Caches a URL for no longer than its remaining lifetime. */
    @Override
    @CircuitBreaker(name = "redis", fallbackMethod = "cacheUrlFallback")
    public void cacheUrl(String shortCode, String originalUrl, UUID urlId, Instant expiresAt) {
        String key = URL_CACHE_PREFIX + shortCode;

        try {
            CachedUrl cachedUrl = new CachedUrl(originalUrl, urlId, expiresAt);
            String value = objectMapper.writeValueAsString(cachedUrl);

            Duration ttl = Duration.ofSeconds(appProperties.getCacheTtlSeconds());
            if (expiresAt != null) {
                Duration remaining = Duration.between(Instant.now(), expiresAt);
                ttl = remaining.compareTo(ttl) < 0 ? remaining : ttl;
            }

            // Redis TTL precision is milliseconds; never round a short lifetime up.
            if (ttl.toMillis() > 0) {
                redisTemplate.opsForValue().set(key, value, Duration.ofMillis(ttl.toMillis()));
                log.debug("URL cached: shortCode={}, ttl={}ms", shortCode, ttl.toMillis());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize URL for caching: key={}", key, e);
        }
    }

    /** Invalidates the cache entry for a short code. */
    @Override
    public void deleteUrl(String shortCode) {
        String key = URL_CACHE_PREFIX + shortCode;
        redisTemplate.delete(key);
        log.debug("URL cache deleted: shortCode={}", shortCode);
    }

    // Fallback methods
    @SuppressWarnings("unused")
    private Optional<CachedUrl> getUrlFallback(String shortCode, Exception e) {
        log.warn("Redis fallback triggered for getUrl: shortCode={}, error={}", shortCode, e.getMessage());
        return Optional.empty();
    }

    @SuppressWarnings("unused")
    private void cacheUrlFallback(String shortCode, String originalUrl, UUID urlId, Instant expiresAt, Exception e) {
        log.warn("Redis fallback triggered for cacheUrl: shortCode={}, error={}", shortCode, e.getMessage());
    }
}
