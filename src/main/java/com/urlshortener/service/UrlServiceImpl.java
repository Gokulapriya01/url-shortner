package com.urlshortener.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.entity.Url;
import com.urlshortener.exception.AliasExistsException;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.dto.request.ShortenRequest;
import com.urlshortener.dto.response.ShortenResponse;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.ShortCodeGenerator;
import com.urlshortener.util.UrlValidator;
import com.urlshortener.util.UrlValidator.ValidationResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlServiceImpl implements UrlService {

    private final UrlRepository urlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlValidator urlValidator;
    private final AppProperties appProperties;

    private static final int MAX_COLLISION_RETRIES = 3;

    /** Validates and persists a URL, then returns its short link and expiration metadata. */
    @Override
    @Transactional
    public ShortenResponse createShortUrl(ShortenRequest request) {
        // Validate URL
        ValidationResult validation = urlValidator.validate(request.getUrl());
        if (!validation.isValid()) {
            throw new InvalidUrlException(validation.getError());
        }

        String shortCode;
        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            // Validate custom alias
            if (!urlValidator.isValidCustomAlias(request.getCustomAlias())) {
                throw new InvalidUrlException("Invalid custom alias format");
            }

            if (urlRepository.existsByCustomAlias(request.getCustomAlias())) {
                throw new AliasExistsException(request.getCustomAlias());
            }

            if (urlRepository.existsByShortCode(request.getCustomAlias())) {
                throw new AliasExistsException(request.getCustomAlias());
            }

            shortCode = request.getCustomAlias();
        } else {
            shortCode = generateUniqueShortCode();
        }

        Instant expiresAt = null;

        Url url = Url.builder()
            .shortCode(shortCode)
            .originalUrl(validation.getNormalizedUrl())
            .customAlias(request.getCustomAlias())
            .expiresAt(expiresAt)
            .build();

        url = urlRepository.save(url);


        log.info("Short URL created: shortCode={}, hasExpiry={}", shortCode, expiresAt != null);

        return ShortenResponse.builder()
            .shortCode(url.getShortCode())
            .shortUrl(appProperties.getBaseUrl() + "/" + url.getShortCode())
            .originalUrl(url.getOriginalUrl())
            .expiresAt(expiresAt)
            .createdAt(url.getCreatedAt())
            .build();
    }



    /** Looks up a persisted URL by short code or custom alias. */
    @Override
    public Optional<Url> getUrlByShortCode(String shortCode) {
        return urlRepository.findByShortCodeOrCustomAlias(shortCode);
    }

    /** Looks up a persisted URL by its database identifier. */
    @Override
    public Optional<Url> getUrlById(UUID id) {
        return urlRepository.findById(id);
    }

    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            String shortCode = shortCodeGenerator.generate();
            if (!urlRepository.existsByShortCode(shortCode) && !urlRepository.existsByCustomAlias(shortCode)) {
                return shortCode;
            }
            log.warn("Short code collision, retrying: shortCode={}, attempt={}", shortCode, attempt + 1);
        }
        throw new RuntimeException("Failed to generate unique short code after " + MAX_COLLISION_RETRIES + " attempts");
    }

}
