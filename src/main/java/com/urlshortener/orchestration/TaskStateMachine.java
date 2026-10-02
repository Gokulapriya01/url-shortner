package com.urlshortener.orchestration;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import com.urlshortener.domain.entity.Task;
import com.urlshortener.domain.entity.TaskTransition;
import com.urlshortener.domain.enums.TaskStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> VALID_TRANSITIONS = Map.of(
        TaskStatus.PENDING, Set.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.SKIPPED),
        TaskStatus.IN_PROGRESS, Set.of(TaskStatus.COMPLETED, TaskStatus.FAILED),
        TaskStatus.FAILED, Set.of(TaskStatus.IN_PROGRESS, TaskStatus.SKIPPED, TaskStatus.PENDING),
        TaskStatus.BLOCKED, Set.of(TaskStatus.PENDING, TaskStatus.SKIPPED),
        TaskStatus.COMPLETED, Set.of(TaskStatus.PENDING),
        TaskStatus.SKIPPED, Set.of(TaskStatus.PENDING)
    );

    /** Can transition. */
    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        Set<TaskStatus> validTargets = VALID_TRANSITIONS.get(from);
        return validTargets != null && validTargets.contains(to);
    }

    /** Start. */
    public static void start(Task task) {
        start(task, "system");
    }

    /** Start. */
    public static void start(Task task, String actor) {
        transition(task, TaskStatus.IN_PROGRESS, actor, "Task execution started");
        task.setStartedAt(Instant.now());
    }

    /** Complete. */
    public static void complete(Task task) {
        complete(task, "system");
    }

    /** Complete. */
    public static void complete(Task task, String actor) {
        transition(task, TaskStatus.COMPLETED, actor, "Task completed successfully");
        task.setCompletedAt(Instant.now());
    }

    /** Fail. */
    public static void fail(Task task, String error) {
        fail(task, error, "system");
    }

    /** Fail. */
    public static void fail(Task task, String error, String actor) {
        task.setError(error);
        transition(task, TaskStatus.FAILED, actor, "Task failed: " + error);
        task.setCompletedAt(Instant.now());
    }

    /** Retry. */
    public static void retry(Task task) {
        retry(task, "system");
    }

    /** Retry. */
    public static void retry(Task task, String actor) {
        task.incrementRetryCount();
        task.setError(null);
        task.setStartedAt(null);
        task.setCompletedAt(null);
        transition(task, TaskStatus.IN_PROGRESS, actor, "Retry attempt " + task.getRetryCount());
    }

    /** Skip. */
    public static void skip(Task task, String reason) {
        skip(task, reason, "system");
    }

    /** Skip. */
    public static void skip(Task task, String reason, String actor) {
        transition(task, TaskStatus.SKIPPED, actor, reason);
        task.setCompletedAt(Instant.now());
    }

    /** Block. */
    public static void block(Task task, String reason) {
        block(task, reason, "system");
    }

    /** Block. */
    public static void block(Task task, String reason, String actor) {
        transition(task, TaskStatus.BLOCKED, actor, reason);
    }

    /** Unblock. */
    public static void unblock(Task task) {
        unblock(task, "system");
    }

    /** Unblock. */
    public static void unblock(Task task, String actor) {
        transition(task, TaskStatus.PENDING, actor, "Dependencies satisfied");
    }

    /** Reset. */
    public static void reset(Task task) {
        reset(task, "system");
    }

    /** Reset. */
    public static void reset(Task task, String actor) {
        task.setRetryCount(0);
        task.setError(null);
        task.setOutput(null);
        task.setStartedAt(null);
        task.setCompletedAt(null);
        transition(task, TaskStatus.PENDING, actor, "Task reset for rollback");
    }

    /** Can retry. */
    public static boolean canRetry(Task task) {
        return task.getStatus() == TaskStatus.FAILED && task.canRetry();
    }

    /** Checks terminal state. */
    public static boolean isTerminalState(TaskStatus status) {
        return status.isTerminal();
    }

    private static void transition(Task task, TaskStatus newStatus, String actor, String reason) {
        TaskStatus oldStatus = task.getStatus();

        if (!canTransition(oldStatus, newStatus)) {
            throw new IllegalStateException(String.format(
                "Invalid transition: %s -> %s for task %s", oldStatus, newStatus, task.getId()));
        }

        // Record transition
        TaskTransition transition = TaskTransition.builder()
            .task(task)
            .fromStatus(oldStatus)
            .toStatus(newStatus)
            .actor(actor)
            .reason(reason)
            .timestamp(Instant.now())
            .build();

        task.getTransitions().add(transition);
        task.setStatus(newStatus);

        log.info("Task state transition: taskId={}, from={}, to={}, actor={}, reason={}",
            task.getId(), oldStatus, newStatus, actor, reason);
    }
}
