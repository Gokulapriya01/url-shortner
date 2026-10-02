package com.urlshortener.service;

import java.util.Optional;
import java.util.UUID;

import com.urlshortener.domain.entity.Url;
import com.urlshortener.dto.request.ShortenRequest;
import com.urlshortener.dto.response.ShortenResponse;

public interface UrlService {

    /** Create short url. */
    ShortenResponse createShortUrl(ShortenRequest request);


    /** Get url by short code. */
    Optional<Url> getUrlByShortCode(String shortCode);

    /** Get url by id. */
    Optional<Url> getUrlById(UUID id);

    record ResolveResult(String originalUrl, UUID urlId, boolean fromDatabase, java.time.Instant expiresAt) {
        /** Resolve result. */
        public ResolveResult(String originalUrl, UUID urlId, boolean fromDatabase) {
            this(originalUrl, urlId, fromDatabase, null);
        }
    }
}
