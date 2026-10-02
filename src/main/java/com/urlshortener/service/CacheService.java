package com.urlshortener.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CacheService {

    /** Get url. */
    Optional<CachedUrl> getUrl(String shortCode);

    /** Cache url. */
    void cacheUrl(String shortCode, String originalUrl, UUID urlId, Instant expiresAt);

    /** Delete url. */
    void deleteUrl(String shortCode);

    record CachedUrl(String originalUrl, UUID urlId, Instant expiresAt) {}
}
