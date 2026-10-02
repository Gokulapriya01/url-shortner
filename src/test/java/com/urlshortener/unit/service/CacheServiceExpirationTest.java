package com.urlshortener.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.config.AppProperties;
import com.urlshortener.config.RedisConfig;
import com.urlshortener.service.CacheService;
import com.urlshortener.service.CacheServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CacheServiceExpirationTest {
    RedisTemplate<String, Object> redis;
    ValueOperations<String, Object> values;
    CacheServiceImpl service;
    ObjectMapper mapper;
    UUID id = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        mapper = new RedisConfig().objectMapper();
        service = new CacheServiceImpl(redis, mapper, new AppProperties());
    }

    @Test
    void subsecondExpiryCapsTtlAndStoresExpiry() throws Exception {
        Instant expiry = Instant.now().plusMillis(750);
        Instant before = Instant.now();
        service.cacheUrl("code", "https://example.com", id, expiry);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(values).set(eq("url:code"), payload.capture(), ttl.capture());
        assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.between(before, expiry));
        assertThat(ttl.getValue()).isLessThan(Duration.ofSeconds(1));
        assertThat(mapper.readValue((String) payload.getValue(), CacheService.CachedUrl.class).expiresAt())
            .isEqualTo(expiry);
    }

    @Test
    void distantExpiryUsesConfiguredTtl() {
        service.cacheUrl("code", "https://example.com", id, Instant.now().plusSeconds(7200));
        verify(values).set(eq("url:code"), any(), eq(Duration.ofSeconds(3600)));
    }

    @Test
    void permanentUrlUsesConfiguredTtlAndExplicitNullExpiry() throws Exception {
        service.cacheUrl("code", "https://example.com", id, null);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(values).set(eq("url:code"), payload.capture(), eq(Duration.ofSeconds(3600)));
        assertThat(mapper.readTree((String) payload.getValue()).has("expiresAt")).isTrue();
        when(values.get("url:code")).thenReturn(payload.getValue());
        assertThat(service.getUrl("code")).contains(new CacheService.CachedUrl("https://example.com", id, null));
    }

    @Test
    void expiredUrlIsNotCached() {
        service.cacheUrl("code", "https://example.com", id, Instant.now().minusSeconds(1));
        verifyNoInteractions(values);
    }

    @Test
    void missingAndCorruptEntriesAreMissesAndDeletionUsesCorrectKey() {
        assertThat(service.getUrl("code")).isEmpty();
        when(values.get("url:code")).thenReturn("not-json");
        assertThat(service.getUrl("code")).isEmpty();
        service.deleteUrl("code");
        verify(redis).delete("url:code");
    }

    @Test
    void legacyCacheEntryWithoutExpiryIsAMiss() throws Exception {
        when(values.get("url:code")).thenReturn(mapper.writeValueAsString(
            java.util.Map.of("originalUrl", "https://example.com", "urlId", id)));
        assertThat(service.getUrl("code")).isEmpty();
    }
}
