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
public class CleanupWorker {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final ClickAggregateRepository clickAggregateRepository;
    private final CacheService cacheService;
    private final AppProperties appProperties;

    /** Counts expired URLs in dry-run mode or transactionally removes expired data. */
    @Transactional
    public CleanupScheduledTask.CleanupResult performCleanup(boolean dryRun) {
        Instant now = Instant.now();
        int batchSize = appProperties.getCleanup().getBatchSize();

        int totalDeletedUrls = 0;
        int totalDeletedEvents = 0;
        int totalDeletedAggregates = 0;

        if (dryRun) return new CleanupScheduledTask.CleanupResult(
            Math.toIntExact(urlRepository.countByExpiresAtBefore(now)), 0, 0, true);
        List<UUID> expiredUrlIds;
        do {
            expiredUrlIds = urlRepository.findExpiredUrlIds(now, PageRequest.of(0, batchSize));

            if (expiredUrlIds.isEmpty()) {
                break;
            }

            if (!dryRun) {
                // Delete associated click events
                totalDeletedEvents += clickEventRepository.deleteByUrlIdIn(expiredUrlIds);

                // Delete associated aggregates
                totalDeletedAggregates += clickAggregateRepository.deleteByUrlIdIn(expiredUrlIds);

                // Invalidate cache
                for (UUID urlId : expiredUrlIds) {
                    urlRepository.findById(urlId).ifPresent(url -> {
                        cacheService.deleteUrl(url.getShortCode());
                        if (url.getCustomAlias() != null) {
                            cacheService.deleteUrl(url.getCustomAlias());
                        }
                    });
                }

                // Delete URLs
                urlRepository.deleteAllById(expiredUrlIds);
                totalDeletedUrls += expiredUrlIds.size();

                log.debug("Cleanup batch completed: deletedUrls={}", expiredUrlIds.size());
            } else {
                totalDeletedUrls += expiredUrlIds.size();
                log.info("Dry run: would delete {} URLs", expiredUrlIds.size());
            }
        } while (expiredUrlIds.size() == batchSize);

        return new CleanupScheduledTask.CleanupResult(totalDeletedUrls, totalDeletedEvents, totalDeletedAggregates, dryRun);
    }

}
