package com.urlshortener.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.entity.Url;
import com.urlshortener.dto.request.ShortenRequest;
import com.urlshortener.dto.response.ShortenResponse;
import com.urlshortener.exception.AliasExistsException;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.service.CacheService;
import com.urlshortener.service.UrlService.ResolveResult;
import com.urlshortener.service.UrlServiceImpl;
import com.urlshortener.util.ShortCodeGenerator;
import com.urlshortener.util.UrlValidator;
import com.urlshortener.util.UrlValidator.ValidationResult;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UrlService Tests")
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private CacheService cacheService;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @Mock
    private UrlValidator urlValidator;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private UrlServiceImpl urlService;

    private static final String BASE_URL = "http://localhost:3000";
    private static final String VALID_URL = "https://example.com/path";
    private static final String SHORT_CODE = "abc1234";

    @BeforeEach
    void setUp() {
        when(appProperties.getBaseUrl()).thenReturn(BASE_URL);
    }

    @Nested
    @DisplayName("createShortUrl")
    class CreateShortUrl {

        @Test
        @DisplayName("should create short URL with generated code")
        void shouldCreateShortUrlWithGeneratedCode() {
            // Given
            ShortenRequest request = new ShortenRequest();
            request.setUrl(VALID_URL);

            when(urlValidator.validate(VALID_URL))
                .thenReturn(ValidationResult.valid(VALID_URL));
            when(shortCodeGenerator.generate()).thenReturn(SHORT_CODE);
            when(urlRepository.existsByShortCode(SHORT_CODE)).thenReturn(false);
            when(urlRepository.existsByCustomAlias(SHORT_CODE)).thenReturn(false);
            when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> {
                Url url = invocation.getArgument(0);
                url.setId(UUID.randomUUID());
                url.setCreatedAt(Instant.now());
                return url;
            });

            // When
            ShortenResponse response = urlService.createShortUrl(request);

            // Then
            assertNotNull(response);
            assertEquals(SHORT_CODE, response.getShortCode());
            assertEquals(VALID_URL, response.getOriginalUrl());
            assertEquals(BASE_URL + "/" + SHORT_CODE, response.getShortUrl());
            assertNull(response.getExpiresAt());

            verify(urlRepository).save(any(Url.class));
            verify(cacheService).cacheUrl(eq(SHORT_CODE), eq(VALID_URL), any(UUID.class), isNull());
        }

        @Test
        @DisplayName("should create short URL with custom alias")
        void shouldCreateShortUrlWithCustomAlias() {
            // Given
            String customAlias = "my-custom-link";
            ShortenRequest request = new ShortenRequest();
            request.setUrl(VALID_URL);
            request.setCustomAlias(customAlias);

            when(urlValidator.validate(VALID_URL))
                .thenReturn(ValidationResult.valid(VALID_URL));
            when(urlValidator.isValidCustomAlias(customAlias)).thenReturn(true);
            when(urlRepository.existsByCustomAlias(customAlias)).thenReturn(false);
            when(urlRepository.existsByShortCode(customAlias)).thenReturn(false);
            when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> {
                Url url = invocation.getArgument(0);
                url.setId(UUID.randomUUID());
                url.setCreatedAt(Instant.now());
                return url;
            });

            // When
            ShortenResponse response = urlService.createShortUrl(request);

            // Then
            assertNotNull(response);
            assertEquals(customAlias, response.getShortCode());
            verify(shortCodeGenerator, never()).generate();
        }

        @Test
        @DisplayName("should create short URL with expiration")
        void shouldCreateShortUrlWithExpiration() {
            // Given
            ShortenRequest request = new ShortenRequest();
            request.setUrl(VALID_URL);
            request.setExpiresIn(3600L); // 1 hour

            when(urlValidator.validate(VALID_URL))
                .thenReturn(ValidationResult.valid(VALID_URL));
            when(shortCodeGenerator.generate()).thenReturn(SHORT_CODE);
            when(urlRepository.existsByShortCode(SHORT_CODE)).thenReturn(false);
            when(urlRepository.existsByCustomAlias(SHORT_CODE)).thenReturn(false);
            when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> {
                Url url = invocation.getArgument(0);
                url.setId(UUID.randomUUID());
                url.setCreatedAt(Instant.now());
                return url;
            });

            // When
            ShortenResponse response = urlService.createShortUrl(request);

            // Then
            assertNotNull(response);
            assertNotNull(response.getExpiresAt());
            assertTrue(response.getExpiresAt().isAfter(Instant.now()));
        }

        @Test
        @DisplayName("should throw InvalidUrlException for invalid URL")
        void shouldThrowInvalidUrlExceptionForInvalidUrl() {
            // Given
            String invalidUrl = "not-a-url";
            ShortenRequest request = new ShortenRequest();
            request.setUrl(invalidUrl);

            when(urlValidator.validate(invalidUrl))
                .thenReturn(ValidationResult.invalid("Invalid URL format"));

            // When/Then
            InvalidUrlException exception = assertThrows(
                InvalidUrlException.class,
                () -> urlService.createShortUrl(request)
            );

            assertTrue(exception.getMessage().contains("Invalid URL format"));
            verify(urlRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw AliasExistsException when custom alias exists")
        void shouldThrowAliasExistsExceptionWhenAliasExists() {
            // Given
            String existingAlias = "existing-alias";
            ShortenRequest request = new ShortenRequest();
            request.setUrl(VALID_URL);
            request.setCustomAlias(existingAlias);

            when(urlValidator.validate(VALID_URL))
                .thenReturn(ValidationResult.valid(VALID_URL));
            when(urlValidator.isValidCustomAlias(existingAlias)).thenReturn(true);
            when(urlRepository.existsByCustomAlias(existingAlias)).thenReturn(true);

            // When/Then
            assertThrows(
                AliasExistsException.class,
                () -> urlService.createShortUrl(request)
            );

            verify(urlRepository, never()).save(any());
        }

        @Test
        @DisplayName("should retry on short code collision")
        void shouldRetryOnShortCodeCollision() {
            // Given
            String collidingCode = "collide";
            String uniqueCode = "unique1";
            ShortenRequest request = new ShortenRequest();
            request.setUrl(VALID_URL);

            when(urlValidator.validate(VALID_URL))
                .thenReturn(ValidationResult.valid(VALID_URL));
            when(shortCodeGenerator.generate())
                .thenReturn(collidingCode)
                .thenReturn(uniqueCode);
            when(urlRepository.existsByShortCode(collidingCode)).thenReturn(true);
            when(urlRepository.existsByShortCode(uniqueCode)).thenReturn(false);
            when(urlRepository.existsByCustomAlias(anyString())).thenReturn(false);
            when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> {
                Url url = invocation.getArgument(0);
                url.setId(UUID.randomUUID());
                url.setCreatedAt(Instant.now());
                return url;
            });

            // When
            ShortenResponse response = urlService.createShortUrl(request);

            // Then
            assertEquals(uniqueCode, response.getShortCode());
            verify(shortCodeGenerator, times(2)).generate();
        }

        @Test
        @DisplayName("should throw exception after max collision retries")
        void shouldThrowExceptionAfterMaxCollisionRetries() {
            // Given
            ShortenRequest request = new ShortenRequest();
            request.setUrl(VALID_URL);

            when(urlValidator.validate(VALID_URL))
                .thenReturn(ValidationResult.valid(VALID_URL));
            when(shortCodeGenerator.generate()).thenReturn("collide");
            when(urlRepository.existsByShortCode("collide")).thenReturn(true);
            when(urlRepository.existsByCustomAlias("collide")).thenReturn(false);

            // When/Then
            assertThrows(
                RuntimeException.class,
                () -> urlService.createShortUrl(request)
            );

            verify(shortCodeGenerator, times(3)).generate();
        }
    }

    @Nested
    @DisplayName("resolveShortCode")
    class ResolveShortCode {

        @Test
        void shouldResolveUnexpiredCachedUrl() {
            UUID urlId = UUID.randomUUID();
            when(cacheService.getUrl(SHORT_CODE)).thenReturn(Optional.of(
                new CacheService.CachedUrl(VALID_URL, urlId, Instant.now().plusSeconds(60))));
            assertEquals(VALID_URL, urlService.resolveShortCode(SHORT_CODE).originalUrl());
            verifyNoInteractions(urlRepository);
            verify(cacheService, never()).deleteUrl(anyString());
        }

        @Test
        void shouldRejectAndInvalidateExpiredCachedUrl() {
            when(cacheService.getUrl(SHORT_CODE)).thenReturn(Optional.of(
                new CacheService.CachedUrl(VALID_URL, UUID.randomUUID(), Instant.now().minusSeconds(1))));
            assertThrows(UrlExpiredException.class, () -> urlService.resolveShortCode(SHORT_CODE));
            verify(cacheService).deleteUrl(SHORT_CODE);
            verifyNoInteractions(urlRepository);
        }

        @Test
        void shouldPreserveExpirationErrorWhenCacheInvalidationFails() {
            when(cacheService.getUrl(SHORT_CODE)).thenReturn(Optional.of(
                new CacheService.CachedUrl(VALID_URL, UUID.randomUUID(), Instant.now().minusSeconds(1))));
            doThrow(new RuntimeException("Redis unavailable")).when(cacheService).deleteUrl(SHORT_CODE);
            assertThrows(UrlExpiredException.class, () -> urlService.resolveShortCode(SHORT_CODE));
            verifyNoInteractions(urlRepository);
        }

        @Test
        @DisplayName("should resolve from cache when available")
        void shouldResolveFromCacheWhenAvailable() {
            // Given
            UUID urlId = UUID.randomUUID();
            when(cacheService.getUrl(SHORT_CODE))
                .thenReturn(Optional.of(new CacheService.CachedUrl(VALID_URL, urlId, null)));

            // When
            ResolveResult result = urlService.resolveShortCode(SHORT_CODE);

            // Then
            assertNotNull(result);
            assertEquals(VALID_URL, result.originalUrl());
            assertEquals(urlId, result.urlId());
            assertFalse(result.fromDatabase());

            verify(urlRepository, never()).findByShortCodeOrCustomAlias(anyString());
        }

        @Test
        @DisplayName("should resolve from database when not in cache")
        void shouldResolveFromDatabaseWhenNotInCache() {
            // Given
            UUID urlId = UUID.randomUUID();
            Url url = Url.builder()
                .id(urlId)
                .shortCode(SHORT_CODE)
                .originalUrl(VALID_URL)
                .build();

            when(cacheService.getUrl(SHORT_CODE)).thenReturn(Optional.empty());
            when(urlRepository.findByShortCodeOrCustomAlias(SHORT_CODE))
                .thenReturn(Optional.of(url));

            // When
            ResolveResult result = urlService.resolveShortCode(SHORT_CODE);

            // Then
            assertNotNull(result);
            assertEquals(VALID_URL, result.originalUrl());
            assertEquals(urlId, result.urlId());
            assertTrue(result.fromDatabase());

            verify(cacheService).cacheUrl(eq(SHORT_CODE), eq(VALID_URL), eq(urlId), isNull());
        }

        @Test
        @DisplayName("should throw UrlNotFoundException when URL not found")
        void shouldThrowUrlNotFoundExceptionWhenNotFound() {
            // Given
            when(cacheService.getUrl(SHORT_CODE)).thenReturn(Optional.empty());
            when(urlRepository.findByShortCodeOrCustomAlias(SHORT_CODE))
                .thenReturn(Optional.empty());

            // When/Then
            assertThrows(
                UrlNotFoundException.class,
                () -> urlService.resolveShortCode(SHORT_CODE)
            );
        }

        @Test
        @DisplayName("should throw UrlExpiredException when URL is expired")
        void shouldThrowUrlExpiredExceptionWhenExpired() {
            // Given
            Url expiredUrl = Url.builder()
                .id(UUID.randomUUID())
                .shortCode(SHORT_CODE)
                .originalUrl(VALID_URL)
                .expiresAt(Instant.now().minusSeconds(3600)) // Expired 1 hour ago
                .build();

            when(cacheService.getUrl(SHORT_CODE)).thenReturn(Optional.empty());
            when(urlRepository.findByShortCodeOrCustomAlias(SHORT_CODE))
                .thenReturn(Optional.of(expiredUrl));

            // When/Then
            assertThrows(
                UrlExpiredException.class,
                () -> urlService.resolveShortCode(SHORT_CODE)
            );
        }
    }

    @Nested
    @DisplayName("getUrlByShortCode")
    class GetUrlByShortCode {

        @Test
        @DisplayName("should return URL when found")
        void shouldReturnUrlWhenFound() {
            // Given
            Url url = Url.builder()
                .id(UUID.randomUUID())
                .shortCode(SHORT_CODE)
                .originalUrl(VALID_URL)
                .build();

            when(urlRepository.findByShortCodeOrCustomAlias(SHORT_CODE))
                .thenReturn(Optional.of(url));

            // When
            Optional<Url> result = urlService.getUrlByShortCode(SHORT_CODE);

            // Then
            assertTrue(result.isPresent());
            assertEquals(SHORT_CODE, result.get().getShortCode());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            // Given
            when(urlRepository.findByShortCodeOrCustomAlias(SHORT_CODE))
                .thenReturn(Optional.empty());

            // When
            Optional<Url> result = urlService.getUrlByShortCode(SHORT_CODE);

            // Then
            assertTrue(result.isEmpty());
        }
    }
}
