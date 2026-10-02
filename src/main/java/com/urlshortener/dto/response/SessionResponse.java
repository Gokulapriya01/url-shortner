package com.urlshortener.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.urlshortener.orchestration.OrchestrationEngine.OrchestrationSession;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private UUID id;
    private String name;
    private String status;
    private int currentPhase;
    private int taskCount;
    private int gateCount;
    private int completedTasks;
    private int failedTasks;
    private List<TaskSummary> tasks;
    private List<GateSummary> gates;
    private Instant startedAt;
    private Instant completedAt;

    /** From. */
    public static SessionResponse from(OrchestrationSession session) {
        return SessionResponse.builder()
            .id(session.getId())
            .name(session.getName())
            .status(session.getStatus())
            .currentPhase(session.getCurrentPhase())
            .taskCount(session.getTasks().size())
            .gateCount(session.getGates().size())
            .completedTasks(session.getMetrics().getCompletedTasks())
            .failedTasks(session.getMetrics().getFailedTasks())
            .tasks(session.getTasks().stream()
                .map(t -> TaskSummary.builder()
                    .id(t.getId())
                    .name(t.getName())
                    .phase(t.getPhase())
                    .status(t.getStatus().name())
                    .build())
                .toList())
            .gates(session.getGates().stream()
                .map(g -> GateSummary.builder()
                    .id(g.getId())
                    .name(g.getName())
                    .phase(g.getPhase())
                    .approved(g.isApproved())
                    .approvedBy(g.getApprovedBy())
                    .build())
                .toList())
            .startedAt(session.getStartedAt())
            .completedAt(session.getCompletedAt())
            .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskSummary {
        private UUID id;
        private String name;
        private int phase;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GateSummary {
        private UUID id;
        private String name;
        private int phase;
        private boolean approved;
        private String approvedBy;
    }
}
