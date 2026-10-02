package com.urlshortener.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.urlshortener.domain.entity.ClickAggregate;
import com.urlshortener.domain.entity.ClickEvent;
import com.urlshortener.domain.entity.Url;
import com.urlshortener.domain.enums.PeriodType;
import com.urlshortener.dto.response.AnalyticsResponse;
import com.urlshortener.dto.response.AnalyticsResponse.ReferrerCount;
import com.urlshortener.dto.response.AnalyticsResponse.TimeSeriesData;
import com.urlshortener.dto.response.AnalyticsResponse.TopReferrers;
import com.urlshortener.dto.response.AnalyticsResponse.TopUserAgents;
import com.urlshortener.dto.response.AnalyticsResponse.UserAgentCount;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickAggregateRepository;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final ClickAggregateRepository clickAggregateRepository;

    private static final int TOP_ITEMS_LIMIT = 10;

    /** Returns lifetime click totals and metadata/time buckets within an inclusive UTC date range. */
    public AnalyticsResponse getAnalytics(String shortCode) {
        Instant now = Instant.now();
        return getAnalytics(shortCode, PeriodType.HOUR, now.minus(7, ChronoUnit.DAYS), now);
    }

    /** Returns lifetime click totals and metadata/time buckets within an inclusive UTC date range. */
    public AnalyticsResponse getAnalytics(String shortCode, PeriodType periodType, Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        Url url = urlRepository.findByShortCodeOrCustomAlias(shortCode)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));

        log.debug("Fetching analytics for: shortCode={}, urlId={}", shortCode, url.getId());

        // Filter raw events before bucketing so partial date boundaries cannot leak clicks.
        List<ClickEvent> recentEvents = clickEventRepository
            .findByUrlIdAndTimestampBetween(url.getId(), from, to);
        Map<Instant, Long> counts = recentEvents.stream().collect(Collectors.groupingBy(
            event -> ClickBatchWriter.periodStart(event.getTimestamp(), periodType), Collectors.counting()));
        List<TimeSeriesData> timeSeries = counts.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> TimeSeriesData.builder().timestamp(entry.getKey()).clicks(entry.getValue()).build()).toList();

        TopReferrers topReferrers = calculateTopReferrers(recentEvents);
        TopUserAgents topUserAgents = calculateTopUserAgents(recentEvents);

        return AnalyticsResponse.builder()
            .shortCode(url.getShortCode())
            .originalUrl(url.getOriginalUrl())
            .totalClicks(url.getClickCount())
            .createdAt(url.getCreatedAt())
            .expiresAt(url.getExpiresAt())
            .timeSeries(timeSeries)
            .topReferrers(topReferrers)
            .topUserAgents(topUserAgents)
            .build();
    }

    /** Returns date-filtered analytics using the requested aggregation period. */
    public AnalyticsResponse getAnalyticsByPeriod(String shortCode, PeriodType periodType, Instant from, Instant to) {
        return getAnalytics(shortCode, periodType, from, to);
    }

    private TopReferrers calculateTopReferrers(List<ClickEvent> events) {
        Map<String, Long> referrerCounts = events.stream()
            .filter(e -> e.getReferrer() != null && !e.getReferrer().isBlank())
            .collect(Collectors.groupingBy(
                ClickEvent::getReferrer,
                Collectors.counting()
            ));

        List<ReferrerCount> items = referrerCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(TOP_ITEMS_LIMIT)
            .map(entry -> ReferrerCount.builder()
                .referrer(entry.getKey())
                .count(entry.getValue())
                .build())
            .toList();

        return TopReferrers.builder().items(items).build();
    }

    private TopUserAgents calculateTopUserAgents(List<ClickEvent> events) {
        Map<String, Long> userAgentCounts = events.stream()
            .filter(e -> e.getUserAgent() != null && !e.getUserAgent().isBlank())
            .collect(Collectors.groupingBy(
                e -> normalizeUserAgent(e.getUserAgent()),
                Collectors.counting()
            ));

        List<UserAgentCount> items = userAgentCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(TOP_ITEMS_LIMIT)
            .map(entry -> UserAgentCount.builder()
                .userAgent(entry.getKey())
                .count(entry.getValue())
                .build())
            .toList();

        return TopUserAgents.builder().items(items).build();
    }

    private String normalizeUserAgent(String userAgent) {
        // Simplify user agent to just browser/platform
        if (userAgent.length() > 100) {
            return userAgent.substring(0, 100) + "...";
        }
        return userAgent;
    }
}
