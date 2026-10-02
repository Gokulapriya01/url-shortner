package com.urlshortener.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.urlshortener.domain.entity.ClickEvent;
import com.urlshortener.domain.enums.PeriodType;
import com.urlshortener.repository.ClickAggregateRepository;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.service.ClickTrackingService.ClickTrackingEvent;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClickBatchWriter {
    private final ClickEventRepository events;
    private final UrlRepository urls;
    private final ClickAggregateRepository aggregates;

    // Called through Spring's proxy; failure rolls back every part of the batch.
    /** Atomically persists click events, lifetime counters and UTC aggregate buckets. */
    @Transactional
    public void writeBatch(List<ClickTrackingEvent> batch) {
        events.saveAllAndFlush(batch.stream().map(event -> ClickEvent.builder()
            .url(urls.getReferenceById(event.urlId()))
            .timestamp(event.timestamp()).referrer(event.referrer())
            .userAgent(event.userAgent()).ipHash(event.ipHash()).build()).toList());

        batch.stream().collect(Collectors.groupingBy(ClickTrackingEvent::urlId, Collectors.counting()))
            .forEach((id, count) -> urls.addClickCount(id, count.intValue()));

        for (PeriodType period : PeriodType.values()) {
            batch.stream().collect(Collectors.groupingBy(
                event -> new Bucket(event.urlId(), periodStart(event.timestamp(), period)),
                Collectors.counting())).forEach((bucket, count) ->
                    aggregates.addClicks(bucket.urlId(), period.name(), bucket.start(), count.intValue()));
        }
    }

    static Instant periodStart(Instant timestamp, PeriodType period) {
        var utc = timestamp.atZone(ZoneOffset.UTC);
        return switch (period) {
            case HOUR -> timestamp.truncatedTo(ChronoUnit.HOURS);
            case DAY -> utc.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            case WEEK -> utc.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneOffset.UTC).toInstant();
            case MONTH -> utc.toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        };
    }

    private record Bucket(java.util.UUID urlId, Instant start) {}
}
