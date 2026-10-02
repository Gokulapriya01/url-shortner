package com.urlshortener.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {

    private String shortCode;
    private String originalUrl;
    private long totalClicks;
    private Instant createdAt;
    private Instant expiresAt;
    private List<TimeSeriesData> timeSeries;
    private TopReferrers topReferrers;
    private TopUserAgents topUserAgents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesData {
        private Instant timestamp;
        private long clicks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopReferrers {
        private List<ReferrerCount> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferrerCount {
        private String referrer;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopUserAgents {
        private List<UserAgentCount> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAgentCount {
        private String userAgent;
        private long count;
    }
}
