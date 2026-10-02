package com.urlshortener.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.urlshortener.config.AppProperties;
import com.urlshortener.controller.RedirectController;
import com.urlshortener.controller.UrlController;
import com.urlshortener.domain.entity.Url;
import com.urlshortener.exception.GlobalExceptionHandler;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.service.*;
import com.urlshortener.util.ShortCodeGenerator;
import com.urlshortener.util.UrlValidator;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = UrlClientErrorsTest.TestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class UrlClientErrorsTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
        RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
    @EnableConfigurationProperties(AppProperties.class)
    @Import({UrlServiceImpl.class, UrlController.class, RedirectController.class,
        GlobalExceptionHandler.class, UrlValidator.class, ShortCodeGenerator.class, RateLimitService.class})
    static class TestApplication {}

    @Autowired MockMvc mvc;
    @Autowired CircuitBreakerRegistry registry;
    @MockBean UrlRepository repository;
    @MockBean CacheService cache;
    @MockBean ClickTrackingService tracking;

    private CircuitBreaker breaker;

    @BeforeEach
    void setup() {
        breaker = registry.circuitBreaker("database");
        breaker.reset();
        when(repository.save(any(Url.class))).thenAnswer(invocation -> {
            Url url = invocation.getArgument(0);
            url.setId(UUID.randomUUID());
            url.setCreatedAt(Instant.now());
            return url;
        });
    }

    @Test
    void duplicateAliasesReturnConflictWithoutOpeningBreaker() throws Exception {
        when(repository.existsByCustomAlias("duplicate")).thenReturn(true);
        for (int i = 0; i < 12; i++) {
            mvc.perform(post("/api/shorten").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customAlias\":\"duplicate\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALIAS_EXISTS"));
        }
        verify(repository, never()).save(any());
        assertIgnoredErrors();
        assertValidRequestStillWorks();
    }

    @Test
    void invalidUrlsReturnBadRequestBeforeRepositoryAccess() throws Exception {
        for (String url : new String[]{"javascript:alert(1)", "data:text/html,<script>alert(1)</script>", "https:///missing-host"}) {
            for (int i = 0; i < 6; i++) {
                mvc.perform(post("/api/shorten").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"" + url + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_URL"));
            }
        }
        verifyNoInteractions(repository);
        assertIgnoredErrors();
        assertValidRequestStillWorks();
    }

    @Test
    void missingLinksReturnNotFoundWithoutOpeningBreaker() throws Exception {
        when(cache.getUrl("missing-test")).thenReturn(Optional.empty());
        when(repository.findByShortCodeOrCustomAlias("missing-test")).thenReturn(Optional.empty());
        for (int i = 0; i < 12; i++) {
            mvc.perform(get("/missing-test")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("URL_NOT_FOUND"));
        }
        verifyNoInteractions(tracking);
        assertIgnoredErrors();
        assertValidRequestStillWorks();
    }

    @Test
    void expiredLinksPreserveGoneResponse() throws Exception {
        when(cache.getUrl("expired-test")).thenReturn(Optional.empty());
        when(repository.findByShortCodeOrCustomAlias("expired-test"))
            .thenReturn(Optional.of(Url.builder().expiresAt(Instant.now().minusSeconds(1)).build()));
        mvc.perform(get("/expired-test")).andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("URL_EXPIRED"));
        assertIgnoredErrors();
    }

    @Test
    void aliasesBeyondContractAreRejectedBeforeRepositoryAccess() throws Exception {
        mvc.perform(post("/api/shorten").contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\",\"customAlias\":\"" + "a".repeat(51) + "\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(repository);
        assertIgnoredErrors();
    }

    @Test
    void realDatabaseFailuresAreRecordedAndOpenBreakerReturnsUnavailable() throws Exception {
        when(repository.existsByCustomAlias("db-error"))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("database unavailable"));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/shorten").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customAlias\":\"db-error\"}"))
                .andExpect(status().isInternalServerError());
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(5);
        mvc.perform(post("/api/shorten").contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void transientDatabaseFailureRetriesWithExponentialBackoff() throws Exception {
        long start = System.nanoTime();
        when(repository.existsByCustomAlias("retry-test"))
            .thenThrow(new org.springframework.dao.TransientDataAccessResourceException("first"))
            .thenThrow(new org.springframework.dao.TransientDataAccessResourceException("second"))
            .thenReturn(false);
        mvc.perform(post("/api/shorten").contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\",\"customAlias\":\"retry-test\"}"))
            .andExpect(status().isCreated());
        verify(repository, times(3)).existsByCustomAlias("retry-test");
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(290);
    }

    @Test
    void connectionFailureRetriesWithBackoffAndRecovers() throws Exception {
        java.util.List<Long> attempts = new java.util.ArrayList<>();
        when(repository.existsByCustomAlias("connection-recovery")).thenAnswer(invocation -> {
            attempts.add(System.nanoTime());
            if (attempts.size() < 3) {
                throw new org.springframework.jdbc.CannotGetJdbcConnectionException(
                    "connection refused", new java.sql.SQLException("connection refused"));
            }
            return false;
        });
        mvc.perform(post("/api/shorten").contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\",\"customAlias\":\"connection-recovery\"}"))
            .andExpect(status().isCreated());
        verify(repository, times(3)).existsByCustomAlias("connection-recovery");
        assertThat((attempts.get(1) - attempts.get(0)) / 1_000_000).isGreaterThanOrEqualTo(90);
        assertThat((attempts.get(2) - attempts.get(1)) / 1_000_000).isGreaterThanOrEqualTo(190);
        verify(repository).save(any(Url.class));
    }

    @Test
    void persistentConnectionFailureStopsAfterThreeAttempts() throws Exception {
        when(repository.existsByCustomAlias("connection-exhausted"))
            .thenThrow(new org.springframework.jdbc.CannotGetJdbcConnectionException(
                "connection refused", new java.sql.SQLException("connection refused")));
        mvc.perform(post("/api/shorten").contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\",\"customAlias\":\"connection-exhausted\"}"))
            .andExpect(status().isInternalServerError());
        verify(repository, times(3)).existsByCustomAlias("connection-exhausted");
        verify(repository, never()).save(any());
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
    }

    private void assertIgnoredErrors() {
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    private void assertValidRequestStillWorks() throws Exception {
        mvc.perform(post("/api/shorten").contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\"}"))
            .andExpect(status().isCreated());
    }
}
