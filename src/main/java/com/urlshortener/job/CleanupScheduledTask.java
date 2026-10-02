package com.urlshortener.job;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.urlshortener.config.AppProperties;
import com.urlshortener.repository.ClickAggregateRepository;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.service.CacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupScheduledTask {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final ClickAggregateRepository clickAggregateRepository;
    private final CacheService cacheService;
    private final AppProperties appProperties;
    private final CleanupWorker cleanupWorker;

    /** Runs scheduled expiration cleanup through a transactional worker. */
    @Scheduled(fixedRateString = "${app.cleanup.interval-ms:3600000}", initialDelayString = "${app.cleanup.interval-ms:3600000}")
    public void cleanupExpiredUrls() {
        if (!appProperties.getCleanup().isEnabled()) {
            log.debug("Cleanup task is disabled");
            return;
        }

        log.info("Starting expired URL cleanup task");
        Instant startTime = Instant.now();

        try {
            CleanupResult result = performCleanup(false);
            log.info("Cleanup task completed: deletedUrls={}, deletedEvents={}, deletedAggregates={}, duration={}ms",
                result.deletedUrls, result.deletedEvents, result.deletedAggregates,
                Instant.now().toEpochMilli() - startTime.toEpochMilli());
        } catch (Exception e) {
            log.error("Cleanup task failed", e);
        }
    }

    /** Counts expired URLs in dry-run mode or transactionally removes expired data. */
    public CleanupResult performCleanup(boolean dryRun) {
        return cleanupWorker.performCleanup(dryRun);
    }

    /** Counts persisted URLs whose expiration precedes the current instant. */
    public long countExpiredUrls() {
        return urlRepository.countByExpiresAtBefore(Instant.now());
    }

    public record CleanupResult(
        int deletedUrls,
        int deletedEvents,
        int deletedAggregates,
        boolean dryRun
    ) {}
}
