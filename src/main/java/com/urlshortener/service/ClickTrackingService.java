package com.urlshortener.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.urlshortener.util.IpHasher;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClickTrackingService {

    private final ClickBatchWriter batchWriter;
    private final IpHasher ipHasher;

    private final BlockingQueue<ClickTrackingEvent> eventQueue = new LinkedBlockingQueue<>(10000);
    private ScheduledExecutorService scheduler;

    private static final int BATCH_SIZE = 10;
    private static final long FLUSH_INTERVAL_MS = 1000;

    /** Starts the background scheduler for queued click events. */
    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ClickTracking-Flusher");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
            this::flushQueue,
            FLUSH_INTERVAL_MS,
            FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        log.info("Click tracking service initialized with batchSize={}, flushInterval={}ms",
            BATCH_SIZE, FLUSH_INTERVAL_MS);
    }

    /** Stops background execution and waits for pending work to drain. */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down click tracking service...");
        if (scheduler == null) return;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        while (!eventQueue.isEmpty()) {
            int before = eventQueue.size();
            flushQueue();
            if (eventQueue.size() >= before) break;
        }
    }

    /** Enqueues click metadata and a hashed IP without waiting for database persistence. */
    public void trackClick(UUID urlId, String referrer, String userAgent, String ip) {
        ClickTrackingEvent event = new ClickTrackingEvent(
            urlId,
            referrer,
            userAgent,
            ipHasher.hash(ip),
            Instant.now()
        );

        if (!eventQueue.offer(event)) {
            log.warn("Click tracking queue is full, dropping event: urlId={}", urlId);
            return;
        }

        // Flush immediately if batch is full
        if (scheduler != null && !scheduler.isShutdown() && eventQueue.size() >= BATCH_SIZE) {
            scheduler.execute(this::flushQueue);
        }
    }

    /** Writes one queued batch and requeues it if persistence fails. */
    public synchronized void flushQueue() {
        List<ClickTrackingEvent> batch = new ArrayList<>();
        eventQueue.drainTo(batch, BATCH_SIZE);

        if (batch.isEmpty()) {
            return;
        }

        try {
            batchWriter.writeBatch(batch);

            log.debug("Flushed click events: count={}", batch.size());
        } catch (Exception e) {
            log.error("Failed to flush click events", e);
            // Re-queue failed events (with limit to prevent memory issues)
            if (eventQueue.size() < 9000) {
                batch.forEach(event -> {
                    if (!eventQueue.offer(event)) {
                        log.warn("Click tracking queue is full during retry: urlId={}", event.urlId());
                    }
                });
            }
        }
    }

    /** Returns the number of click events waiting for persistence. */
    public int getQueueSize() {
        return eventQueue.size();
    }

    public record ClickTrackingEvent(
        UUID urlId,
        String referrer,
        String userAgent,
        String ipHash,
        Instant timestamp
    ) {}
}
