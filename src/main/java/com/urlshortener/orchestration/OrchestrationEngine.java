package com.urlshortener.orchestration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;
import java.util.function.BiFunction;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.entity.Gate;
import com.urlshortener.domain.entity.Task;
import com.urlshortener.domain.enums.TaskStatus;
import com.urlshortener.orchestration.DAGBuilder.TaskDAG;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.jackson.Jacksonized;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrchestrationEngine {

    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties appProperties;

    private final Map<UUID, OrchestrationSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, TaskDefinition> taskDefinitions = new ConcurrentHashMap<>();
    private final Map<String, GateDefinition> gateDefinitions = new ConcurrentHashMap<>();
    private final Map<String, BiFunction<Task, ExecutionContext, TaskHandlerResult>> handlers = new ConcurrentHashMap<>();

    private ExecutorService executor;

    private synchronized ExecutorService executor() {
        if (executor == null) executor = Executors.newFixedThreadPool(appProperties.getOrchestration().getMaxConcurrentTasks());
        return executor;
    }

    /** Stops background execution and waits for pending work to drain. */
    @PreDestroy
    public synchronized void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
        }
    }

    /** Registers task definitions for subsequently created sessions. */
    public void registerTasks(List<TaskDefinition> tasks) {
        tasks.forEach(t -> taskDefinitions.put(t.getId(), t));
        log.info("Tasks registered: count={}", tasks.size());
    }

    /** Registers gate definitions for subsequently created sessions. */
    public void registerGates(List<GateDefinition> gates) {
        gates.forEach(g -> gateDefinitions.put(g.getId(), g));
        log.info("Gates registered: count={}", gates.size());
    }

    /** Associates a handler name with executable workflow logic. */
    public void registerHandler(String name, BiFunction<Task, ExecutionContext, TaskHandlerResult> handler) {
        handlers.put(name, handler);
        log.info("Handler registered: name={}", name);
    }

    /** Creates an independent session with task instances, gates, DAG and shared context. */
    public OrchestrationSession createSession(String name) {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        // Create task instances from definitions
        List<Task> tasks = taskDefinitions.values().stream()
            .map(def -> Task.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .definitionId(def.getId())
                .name(def.getName())
                .description(def.getDescription())
                .phase(def.getPhase())
                .orderIndex(def.getOrderIndex())
                .dependencies(new ArrayList<>(def.getDependencies()))
                .acIds(new ArrayList<>(def.getAcIds()))
                .maxRetries(def.getMaxRetries() != null ? def.getMaxRetries() : appProperties.getOrchestration().getDefaultMaxRetries())
                .status(TaskStatus.PENDING)
                .build())
            .toList();

        // Create gate instances from definitions
        List<Gate> gates = gateDefinitions.values().stream()
            .map(def -> Gate.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .definitionId(def.getId())
                .name(def.getName())
                .phase(def.getPhase())
                .requiresApproval(def.isRequiresApproval())
                .approved(false)
                .blockedTasks(new ArrayList<>())
                .build())
            .toList();

        // Build DAG
        TaskDAG dag = new DAGBuilder().build(tasks);

        OrchestrationSession session = OrchestrationSession.builder()
            .id(sessionId)
            .name(name)
            .status("active")
            .currentPhase(1)
            .tasks(new ArrayList<>(tasks))
            .gates(new ArrayList<>(gates))
            .context(ExecutionContext.builder()
                .sessionId(sessionId)
                .data(new ConcurrentHashMap<>())
                .createdAt(now)
                .updatedAt(now)
                .build())
            .metrics(OrchestrationMetrics.builder()
                .totalTasks(tasks.size())
                .completedTasks(0)
                .failedTasks(0)
                .skippedTasks(0)
                .retryCount(0)
                .rollbackCount(0)
                .build())
            .dag(dag)
            .startedAt(now)
            .build();

        sessions.put(sessionId, session);
        emitEvent("session_started", sessionId, null, null, null);

        log.info("Session created: sessionId={}, name={}, taskCount={}, gateCount={}",
            sessionId, name, tasks.size(), gates.size());

        return session;
    }

    /** Returns the session when its identifier is known. */
    public Optional<OrchestrationSession> getSession(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /** Executes one ready task batch concurrently, respecting dependencies and approval gates. */
    public List<Task> executeNext(UUID sessionId) {
        OrchestrationSession session = sessions.get(sessionId);
        if (session == null) return List.of();
        synchronized (session) {
            if (session.isExecuting() || !"active".equals(session.getStatus())) return List.of();
            session.setExecuting(true);
        }
        try { return executeBatch(session); }
        finally { synchronized (session) { session.setExecuting(false); } }
    }

    private List<Task> executeBatch(OrchestrationSession session) {
        UUID sessionId = session.getId();
        // Create dependency resolver
        DependencyResolver resolver = new DependencyResolver(session.getDag());

        // Update resolver with current statuses
        session.getTasks().forEach(task -> {
            String taskId = task.getDefinitionId() != null ? task.getDefinitionId() : task.getId().toString();
            resolver.updateTaskStatus(taskId, task.getStatus());
        });

        // Check gate approval for current phase
        Optional<Gate> currentGate = session.getGates().stream()
            .filter(g -> g.getPhase() == session.getCurrentPhase())
            .findFirst();

        if (currentGate.isPresent() && !currentGate.get().isApproved()) {
            if (currentGate.get().isRequiresApproval()) {
                log.info("Waiting for gate approval: sessionId={}, phase={}, gateId={}",
                    sessionId, session.getCurrentPhase(), currentGate.get().getId());
                emitEvent("gate_reached", sessionId, null, currentGate.get().getId(), session.getCurrentPhase());
                return List.of();
            }
        }

        // Get ready tasks for current phase
        List<String> readyTaskIds = resolver.getReadyTasksForPhase(session.getCurrentPhase());

        // Limit concurrent execution
        int maxConcurrent = appProperties.getOrchestration().getMaxConcurrentTasks();
        long inProgressCount = session.getTasks().stream()
            .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
            .count();
        int availableSlots = maxConcurrent - (int) inProgressCount;

        List<String> tasksToExecute = readyTaskIds.stream()
            .limit(availableSlots)
            .toList();

        List<Task> executedTasks = new ArrayList<>();
        try {
            List<CompletableFuture<Task>> futures = tasksToExecute.stream().map(taskDefId ->
                CompletableFuture.supplyAsync(() -> {
                    Task task = session.getTasks().stream()
                        .filter(t -> taskDefId.equals(t.getDefinitionId())).findFirst().orElseThrow();
                    if (!"active".equals(session.getStatus())) return null;
                    executeTask(session, task);
                    return task;
                }, executor())).toList();
            for (var future : futures) {
                Task task = future.join();
                if (task != null) {
                    resolver.updateTaskStatus(task.getDefinitionId(), task.getStatus());
                    executedTasks.add(task);
                }
            }
        } finally {
            // Reservation is released by executeNext after completion bookkeeping.
        }
        if (executedTasks.stream().anyMatch(t -> t.getStatus() == TaskStatus.FAILED)) {
            rollbackToPhase(sessionId, 0, true);
            session.setStatus("paused");
            return executedTasks;
        }

        // Check phase completion
        if ("active".equals(session.getStatus()) && resolver.isPhaseComplete(session.getCurrentPhase())) {
            emitEvent("phase_completed", sessionId, null, null, session.getCurrentPhase());

            Integer nextPhase = resolver.getNextPhase();
            if (nextPhase != null) {
                session.setCurrentPhase(nextPhase);
                emitEvent("phase_started", sessionId, null, null, nextPhase);
            } else if (resolver.isAllComplete()) {
                session.setStatus("completed");
                session.setCompletedAt(Instant.now());
                emitEvent("session_completed", sessionId, null, null, null);
            }
        }

        return executedTasks;
    }

    private void executeTask(OrchestrationSession session, Task task) {
        TaskDefinition definition = taskDefinitions.get(task.getDefinitionId());
        TaskStateMachine.start(task);
        Instant firstFailure = null;
        while (true) {
            emitEvent("task_started", session.getId(), task.getId(), null, null);
            long start = System.nanoTime();
            try {
                String handlerName = definition != null && definition.getHandler() != null ? definition.getHandler() : "default";
                var handler = handlers.get(handlerName);
                TaskHandlerResult result = handler == null
                    ? TaskHandlerResult.builder().success(true).output(Map.of("message", "Auto-completed (no handler)")).build()
                    : handler.apply(task, session.getContext());
                if (!result.isSuccess()) throw new IllegalStateException(result.getError());
                Map<String, Object> output = result.getOutput() == null ? Map.of() : result.getOutput();
                task.setOutput(output);
                session.getContext().getData().put(task.getDefinitionId(), output);
                session.getContext().setUpdatedAt(Instant.now());
                TaskStateMachine.complete(task);
                session.getMetrics().incrementCompleted();
                if (firstFailure != null) session.getMetrics().recordRecovery(java.time.Duration.between(firstFailure, Instant.now()).toMillis());
                emitEvent("task_completed", session.getId(), task.getId(), null, null);
                return;
            } catch (Exception e) {
                if (firstFailure == null) firstFailure = Instant.now();
                TaskStateMachine.fail(task, e.getMessage());
                emitEvent("task_failed", session.getId(), task.getId(), null, null);
                if (!TaskStateMachine.canRetry(task)) {
                    session.getMetrics().incrementFailed();
                    if (definition == null || !"ROLLBACK".equalsIgnoreCase(definition.getOnExhaustion())) {
                        TaskStateMachine.skip(task, "Retries exhausted");
                        session.getMetrics().incrementSkipped();
                    }
                    return;
                }
                long delay = appProperties.getOrchestration().getRetryDelayMs() * (1L << Math.min(task.getRetryCount(), 20));
                try { Thread.sleep(delay); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                // Retry performs the FAILED -> IN_PROGRESS transition; do not start twice.
                TaskStateMachine.retry(task);
                session.getMetrics().incrementRetries();
            } finally {
                session.getMetrics().recordLatency((System.nanoTime() - start) / 1_000_000);
            }
        }
    }

    /** Replaces an upstream output and invalidates its transitive downstream results. */
    public boolean updateTaskOutput(UUID sessionId, String definitionId, Map<String, Object> output) {
        OrchestrationSession session = sessions.get(sessionId);
        if (session == null) return false;
        synchronized (session) {
            if (session.isExecuting()) throw new IllegalArgumentException("Pause and wait for active tasks before replanning");
            Task upstream = session.getTasks().stream().filter(t -> definitionId.equals(t.getDefinitionId())).findFirst().orElse(null);
            if (upstream == null) return false;
            if (upstream.getStatus() != TaskStatus.COMPLETED) throw new IllegalArgumentException("Upstream task must be completed");
            upstream.setOutput(output);
            session.getContext().getData().put(definitionId, output);
            var pending = new java.util.ArrayDeque<String>(session.getDag().getNodes().get(definitionId).getDependents());
            var seen = new java.util.HashSet<String>();
            while (!pending.isEmpty()) {
                String id = pending.remove();
                if (!seen.add(id)) continue;
                pending.addAll(session.getDag().getNodes().get(id).getDependents());
                Task downstream = session.getTasks().stream().filter(t -> id.equals(t.getDefinitionId())).findFirst().orElseThrow();
                if (downstream.getStatus() != TaskStatus.PENDING) TaskStateMachine.reset(downstream);
                session.getContext().getData().remove(id);
            }
            session.setDag(new DAGBuilder().build(session.getTasks()));
            session.setCurrentPhase(session.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.PENDING).mapToInt(Task::getPhase).min().orElse(session.getCurrentPhase()));
            session.setStatus("active");
            session.setCompletedAt(null);
            session.getContext().setUpdatedAt(Instant.now());
            emitEvent("workflow_replanned", sessionId, upstream.getId(), null, session.getCurrentPhase());
            return true;
        }
    }

    /** Records approval and the actor for a session gate. */
    public boolean approveGate(UUID sessionId, UUID gateId, String approvedBy) {
        OrchestrationSession session = sessions.get(sessionId);
        if (session == null) return false;

        return session.getGates().stream()
            .filter(g -> g.getId().equals(gateId))
            .findFirst()
            .map(gate -> {
                gate.approve(approvedBy);
                emitEvent("gate_approved", sessionId, null, gateId, gate.getPhase());
                log.info("Gate approved: sessionId={}, gateId={}, approvedBy={}", sessionId, gateId, approvedBy);
                return true;
            })
            .orElse(false);
    }

    /** Revokes gate approval and records the rejection reason. */
    public boolean rejectGate(UUID sessionId, UUID gateId, String reason) {
        OrchestrationSession session = sessions.get(sessionId);
        if (session == null) return false;

        return session.getGates().stream()
            .filter(g -> g.getId().equals(gateId))
            .findFirst()
            .map(gate -> {
                gate.reject(reason);
                emitEvent("gate_rejected", sessionId, null, gateId, gate.getPhase());
                log.info("Gate rejected: sessionId={}, gateId={}, reason={}", sessionId, gateId, reason);
                return true;
            })
            .orElse(false);
    }

    /** Prevents subsequent task starts while allowing active handlers to reach a checkpoint. */
    public boolean pauseSession(UUID sessionId) {
        OrchestrationSession session = sessions.get(sessionId);
        if (session == null || !"active".equals(session.getStatus())) return false;

        session.setStatus("paused");
        emitEvent("session_paused", sessionId, null, null, null);
        log.info("Session paused: sessionId={}", sessionId);
        return true;
    }

    /** Reactivates a paused session without discarding its context. */
    public boolean resumeSession(UUID sessionId) {
        OrchestrationSession session = sessions.get(sessionId);
        if (session == null || !"paused".equals(session.getStatus())) return false;

        session.setStatus("active");
        emitEvent("session_resumed", sessionId, null, null, null);
        log.info("Session resumed: sessionId={}", sessionId);
        return true;
    }

    /** Marks a session cancelled and prevents subsequent task starts. */
    public boolean cancelSession(UUID sessionId) {
        OrchestrationSession session = sessions.get(sessionId);
        if (session == null) return false;

        session.setStatus("cancelled");
        session.setCompletedAt(Instant.now());
        log.info("Session cancelled: sessionId={}", sessionId);
        return true;
    }

    /** Resets later task/gate state and removes invalidated outputs from shared context. */
    public boolean rollbackToPhase(UUID sessionId, int targetPhase) {
        if (targetPhase < 0) throw new IllegalArgumentException("targetPhase must be nonnegative");
        return rollbackToPhase(sessionId, targetPhase, false);
    }

    private boolean rollbackToPhase(UUID sessionId, int targetPhase, boolean internal) {
        if (!appProperties.getOrchestration().isEnableRollback()) {
            log.warn("Rollback is disabled: sessionId={}", sessionId);
            return false;
        }

        OrchestrationSession session = sessions.get(sessionId);
        if (session == null) return false;

        emitEvent("rollback_started", sessionId, null, null, targetPhase);

        if (session.isExecuting() && !internal) throw new IllegalArgumentException("Wait for active tasks before rollback");

        // Reset tasks in phases after target
        session.getTasks().forEach(task -> {
            if (task.getPhase() > targetPhase) {
                if (task.getStatus() != TaskStatus.PENDING) TaskStateMachine.reset(task);
                session.getContext().getData().remove(task.getDefinitionId());
            }
        });

        // Reset gates
        session.getGates().forEach(gate -> {
            if (gate.getPhase() > targetPhase) {
                gate.setApproved(false);
                gate.setApprovedBy(null);
                gate.setApprovedAt(null);
                gate.setRejectedReason(null);
            }
        });

        session.setDag(new DAGBuilder().build(session.getTasks()));
        session.setStatus("active");
        session.setCompletedAt(null);
        session.setCurrentPhase(Math.max(1, targetPhase));
        session.getMetrics().incrementRollbacks();

        emitEvent("rollback_completed", sessionId, null, null, targetPhase);
        log.info("Rollback completed: sessionId={}, targetPhase={}", sessionId, targetPhase);
        return true;
    }

    /** Stores a shared context value for later workflow stages. */
    public boolean updateContext(UUID sessionId, String key, Object value) {
        OrchestrationSession session = sessions.get(sessionId);
        if (session == null) return false;

        session.getContext().getData().put(key, value);
        session.getContext().setUpdatedAt(Instant.now());
        emitEvent("context_updated", sessionId, null, null, null);
        return true;
    }

    /** Returns a shared context value from the session. */
    public Object getContextValue(UUID sessionId, String key) {
        OrchestrationSession session = sessions.get(sessionId);
        return session != null ? session.getContext().getData().get(key) : null;
    }

    /** Returns current task counts and cumulative retry, rollback, latency and recovery telemetry. */
    public Optional<OrchestrationMetrics> getMetrics(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId)).map(session -> {
            var metrics = session.getMetrics();
            metrics.setCompletedTasks((int) session.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count());
            metrics.setFailedTasks((int) session.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.FAILED || (t.getStatus() == TaskStatus.SKIPPED && t.getError() != null)).count());
            metrics.setSkippedTasks((int) session.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.SKIPPED).count());
            return metrics;
        });
    }

    private void emitEvent(String type, UUID sessionId, UUID taskId, UUID gateId, Integer phase) {
        OrchestrationEvent event = OrchestrationEvent.builder()
            .id(UUID.randomUUID())
            .type(type)
            .sessionId(sessionId)
            .taskId(taskId)
            .gateId(gateId)
            .phase(phase)
            .timestamp(Instant.now())
            .build();

        eventPublisher.publishEvent(event);
        log.debug("Orchestration event emitted: type={}, sessionId={}", type, sessionId);
    }

    // Inner classes for data structures

    @Data
    @Builder
    @AllArgsConstructor
    @Jacksonized
    public static class TaskDefinition {
        private String id;
        private String name;
        private String description;
        private int phase;
        private int orderIndex;
        @Builder.Default
        private List<String> dependencies = new ArrayList<>();
        @Builder.Default
        private List<String> acIds = new ArrayList<>();
        private String handler;
        private Integer maxRetries;
        private Long timeoutMs;
        @Builder.Default
        private String onExhaustion = "SKIP";
    }

    @Data
    @Builder
    @AllArgsConstructor
    @Jacksonized
    public static class GateDefinition {
        private String id;
        private String name;
        private int phase;
        private boolean requiresApproval;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class OrchestrationSession {
        private UUID id;
        private String name;
        private volatile String status;
        private volatile boolean executing;
        private int currentPhase;
        private UUID currentGateId;
        private List<Task> tasks;
        private List<Gate> gates;
        private ExecutionContext context;
        private OrchestrationMetrics metrics;
        private TaskDAG dag;
        private Instant startedAt;
        private Instant completedAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ExecutionContext {
        private UUID sessionId;
        private Map<String, Object> data;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class OrchestrationMetrics {
        private int totalTasks;
        private int completedTasks;
        private int failedTasks;
        private int skippedTasks;
        private int retryCount;
        private int rollbackCount;
        private long latencyMs;
        private long recoveryTimeMs;
        private int recoveryCount;
        /** Record latency. */
        public synchronized void recordLatency(long ms) { latencyMs += ms; }
        /** Records recovery. */
        public synchronized void recordRecovery(long ms) { recoveryTimeMs += ms; recoveryCount++; }


        /** Increments completed. */
        public synchronized void incrementCompleted() { completedTasks++; }
        /** Increments failed. */
        public synchronized void incrementFailed() { failedTasks++; }
        /** Increments skipped. */
        public synchronized void incrementSkipped() { skippedTasks++; }
        /** Increments retries. */
        public synchronized void incrementRetries() { retryCount++; }
        /** Increment rollbacks. */
        public synchronized void incrementRollbacks() { rollbackCount++; }
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class TaskHandlerResult {
        private boolean success;
        private Map<String, Object> output;
        private String error;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class OrchestrationEvent {
        private UUID id;
        private String type;
        private UUID sessionId;
        private UUID taskId;
        private UUID gateId;
        private Integer phase;
        private Instant timestamp;
    }
}
