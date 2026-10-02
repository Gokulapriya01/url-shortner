package com.urlshortener.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.config.AppProperties;
import com.urlshortener.job.CleanupScheduledTask;
import com.urlshortener.job.CleanupScheduledTask.CleanupResult;
import com.urlshortener.service.ClickTrackingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final CleanupScheduledTask cleanupTask;
    private final ClickTrackingService clickTrackingService;
    private final AppProperties appProperties;

    /** Returns cleanup configuration and the current expired-URL count. */
    @GetMapping("/cleanup/status")
    public Map<String, Object> getCleanupStatus() {
        long expiredCount = cleanupTask.countExpiredUrls();
        return Map.of(
            "enabled", appProperties.getCleanup().isEnabled(),
            "intervalMs", appProperties.getCleanup().getIntervalMs(),
            "batchSize", appProperties.getCleanup().getBatchSize(),
            "expiredUrlCount", expiredCount,
            "timestamp", Instant.now()
        );
    }

    /** Requests cleanup; dry-run reports counts without deleting data. */
    @PostMapping("/cleanup/run")
    public Map<String, Object> runCleanup(@RequestBody(required = false) Map<String, Boolean> request) {
        boolean dryRun = request != null && Boolean.TRUE.equals(request.get("dryRun"));

        log.info("Manual cleanup triggered: dryRun={}", dryRun);

        CleanupResult result = cleanupTask.performCleanup(dryRun);

        return Map.of(
            "success", true,
            "dryRun", result.dryRun(),
            "deletedUrls", result.deletedUrls(),
            "deletedEvents", result.deletedEvents(),
            "deletedAggregates", result.deletedAggregates(),
            "timestamp", Instant.now()
        );
    }

    /** Returns the analytics queue size and response timestamp. */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return Map.of(
            "clickTrackingQueueSize", clickTrackingService.getQueueSize(),
            "timestamp", Instant.now()
        );
    }
}
