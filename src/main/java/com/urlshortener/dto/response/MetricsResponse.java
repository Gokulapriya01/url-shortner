package com.urlshortener.dto.response;

import com.urlshortener.orchestration.OrchestrationEngine.OrchestrationMetrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsResponse {

    private int totalTasks;
    private int completedTasks;
    private int failedTasks;
    private int skippedTasks;
    private int retryCount;
    private int rollbackCount;
    private double completionPercentage;
    private double successRate;
    private double mttrMs;
    private long latencyMs;

    /** From. */
    public static MetricsResponse from(OrchestrationMetrics metrics) {
        double percentage = metrics.getTotalTasks() > 0
            ? (double) metrics.getCompletedTasks() / metrics.getTotalTasks() * 100
            : 0;

        return MetricsResponse.builder()
            .successRate(metrics.getCompletedTasks() + metrics.getFailedTasks() == 0 ? 0 : (double) metrics.getCompletedTasks() / (metrics.getCompletedTasks() + metrics.getFailedTasks()))
            .mttrMs(metrics.getRecoveryCount() == 0 ? 0 : (double) metrics.getRecoveryTimeMs() / metrics.getRecoveryCount())
            .latencyMs(metrics.getLatencyMs())
            .totalTasks(metrics.getTotalTasks())
            .completedTasks(metrics.getCompletedTasks())
            .failedTasks(metrics.getFailedTasks())
            .skippedTasks(metrics.getSkippedTasks())
            .retryCount(metrics.getRetryCount())
            .rollbackCount(metrics.getRollbackCount())
            .completionPercentage(Math.round(percentage * 100.0) / 100.0)
            .build();
    }
}
