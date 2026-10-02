package com.urlshortener.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import com.urlshortener.domain.enums.PeriodType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.dto.response.AnalyticsResponse;
import com.urlshortener.service.AnalyticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** Returns lifetime click totals and metadata/time buckets within an inclusive UTC date range. */
    @GetMapping("/{code}")
    public AnalyticsResponse getAnalytics(@PathVariable String code,
            @RequestParam(defaultValue = "hour") String granularity,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        log.debug("Getting analytics for: code={}", code);
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(7, ChronoUnit.DAYS);
        PeriodType period;
        try {
            period = PeriodType.valueOf(granularity.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("granularity must be hour, day, week, or month");
        }
        return analyticsService.getAnalytics(code, period, start, end);
    }
}
