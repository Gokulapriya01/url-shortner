package com.urlshortener.orchestration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.urlshortener.domain.enums.TaskStatus;
import com.urlshortener.orchestration.DAGBuilder.DAGNode;
import com.urlshortener.orchestration.DAGBuilder.TaskDAG;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

public class DependencyResolver {

    private final TaskDAG dag;
    private final Map<String, TaskStatus> taskStatuses = new ConcurrentHashMap<>();

    /** Dependency resolver. */
    public DependencyResolver(TaskDAG dag) {
        this.dag = dag;
        dag.getNodes().keySet().forEach(id -> taskStatuses.put(id, TaskStatus.PENDING));
    }

    /** Updates task status. */
    public void updateTaskStatus(String taskId, TaskStatus status) {
        taskStatuses.put(taskId, status);
        DAGNode node = dag.getNodes().get(taskId);
        if (node != null) {
            node.setStatus(status);
        }
    }

    /** Get ready tasks. */
    public List<String> getReadyTasks() {
        return dag.getNodes().entrySet().stream()
            .filter(e -> taskStatuses.get(e.getKey()) == TaskStatus.PENDING)
            .filter(e -> e.getValue().getDependencies().stream()
                .allMatch(depId -> taskStatuses.get(depId) == TaskStatus.COMPLETED || taskStatuses.get(depId) == TaskStatus.SKIPPED))
            .map(Map.Entry::getKey)
            .toList();
    }

    /** Get ready tasks for phase. */
    public List<String> getReadyTasksForPhase(int phase) {
        List<String> phaseTasks = dag.getPhases().getOrDefault(phase, List.of());
        return getReadyTasks().stream()
            .filter(phaseTasks::contains)
            .toList();
    }

    /** Returns blocked tasks. */
    public List<String> getBlockedTasks() {
        return dag.getNodes().entrySet().stream()
            .filter(e -> taskStatuses.get(e.getKey()) == TaskStatus.PENDING)
            .filter(e -> e.getValue().getDependencies().stream()
                .anyMatch(depId -> {
                    TaskStatus status = taskStatuses.get(depId);
                    return status == TaskStatus.FAILED || status == TaskStatus.BLOCKED;
                }))
            .map(Map.Entry::getKey)
            .toList();
    }

    /** Is phase complete. */
    public boolean isPhaseComplete(int phase) {
        List<String> phaseTasks = dag.getPhases().getOrDefault(phase, List.of());
        return phaseTasks.stream()
            .allMatch(taskId -> {
                TaskStatus status = taskStatuses.get(taskId);
                return status == TaskStatus.COMPLETED || status == TaskStatus.SKIPPED;
            });
    }

    /** Is all complete. */
    public boolean isAllComplete() {
        return taskStatuses.values().stream()
            .allMatch(status -> status == TaskStatus.COMPLETED || status == TaskStatus.SKIPPED);
    }

    /** Returns next phase. */
    public Integer getNextPhase() {
        return dag.getPhases().keySet().stream()
            .sorted()
            .filter(phase -> !isPhaseComplete(phase))
            .findFirst()
            .orElse(null);
    }

    /** Get downstream tasks. */
    public List<String> getDownstreamTasks(String taskId) {
        DAGNode node = dag.getNodes().get(taskId);
        return node != null ? List.copyOf(node.getDependents()) : List.of();
    }

    /** Returns the analytics queue size and response timestamp. */
    public ResolverStats getStats() {
        long completed = taskStatuses.values().stream().filter(s -> s == TaskStatus.COMPLETED).count();
        long failed = taskStatuses.values().stream().filter(s -> s == TaskStatus.FAILED).count();
        long pending = taskStatuses.values().stream().filter(s -> s == TaskStatus.PENDING).count();
        long blocked = taskStatuses.values().stream().filter(s -> s == TaskStatus.BLOCKED).count();
        long inProgress = taskStatuses.values().stream().filter(s -> s == TaskStatus.IN_PROGRESS).count();
        long skipped = taskStatuses.values().stream().filter(s -> s == TaskStatus.SKIPPED).count();

        return ResolverStats.builder()
            .total(taskStatuses.size())
            .completed(completed)
            .failed(failed)
            .pending(pending)
            .blocked(blocked)
            .inProgress(inProgress)
            .skipped(skipped)
            .build();
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ResolverStats {
        private int total;
        private long completed;
        private long failed;
        private long pending;
        private long blocked;
        private long inProgress;
        private long skipped;
    }
}
