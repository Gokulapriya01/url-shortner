package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "app")
@Validated
@Getter
@Setter
public class AppProperties {

    @NotBlank
    private String baseUrl = "http://localhost:3000";

    @Min(1)
    private int shortCodeLength = 7;

    @Min(1)
    private long cacheTtlSeconds = 3600;

    private RateLimit rateLimit = new RateLimit();

    private Cleanup cleanup = new Cleanup();

    private Orchestration orchestration = new Orchestration();

    @Getter
    @Setter
    public static class RateLimit {
        private long windowMs = 60000;
        private int maxShorten = 100;
        private int maxRedirect = 1000;
        private int maxAnalytics = 100;
    }

    @Getter
    @Setter
    public static class Cleanup {
        private boolean enabled = true;
        private long intervalMs = 3600000;
        private int batchSize = 1000;
    }

    @Getter
    @Setter
    public static class Orchestration {
        private int maxConcurrentTasks = 5;
        private long defaultTaskTimeoutMs = 300000;
        private int defaultMaxRetries = 3;
        private long retryDelayMs = 1000;
        private boolean enableRollback = true;
        private boolean autoApproveGates = false;
    }
}
