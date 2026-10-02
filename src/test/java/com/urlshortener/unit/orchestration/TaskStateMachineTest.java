package com.urlshortener.unit.orchestration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.urlshortener.domain.entity.Task;
import com.urlshortener.domain.enums.TaskStatus;
import com.urlshortener.orchestration.TaskStateMachine;

@DisplayName("TaskStateMachine Tests")
class TaskStateMachineTest {

    private Task task;

    @BeforeEach
    void setUp() {
        task = Task.builder()
            .id(UUID.randomUUID())
            .name("Test Task")
            .status(TaskStatus.PENDING)
            .phase(1)
            .orderIndex(1)
            .maxRetries(3)
            .build();
    }

    @Nested
    @DisplayName("canTransition()")
    class CanTransition {

        @Test
        @DisplayName("PENDING can transition to IN_PROGRESS")
        void pendingCanTransitionToInProgress() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.IN_PROGRESS));
        }

        @Test
        @DisplayName("PENDING can transition to BLOCKED")
        void pendingCanTransitionToBlocked() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.BLOCKED));
        }

        @Test
        @DisplayName("PENDING can transition to SKIPPED")
        void pendingCanTransitionToSkipped() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.SKIPPED));
        }

        @Test
        @DisplayName("PENDING cannot transition to COMPLETED directly")
        void pendingCannotTransitionToCompleted() {
            assertFalse(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.COMPLETED));
        }

        @Test
        @DisplayName("PENDING cannot transition to FAILED directly")
        void pendingCannotTransitionToFailed() {
            assertFalse(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.FAILED));
        }

        @Test
        @DisplayName("IN_PROGRESS can transition to COMPLETED")
        void inProgressCanTransitionToCompleted() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED));
        }

        @Test
        @DisplayName("IN_PROGRESS can transition to FAILED")
        void inProgressCanTransitionToFailed() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.IN_PROGRESS, TaskStatus.FAILED));
        }

        @Test
        @DisplayName("IN_PROGRESS cannot transition to PENDING")
        void inProgressCannotTransitionToPending() {
            assertFalse(TaskStateMachine.canTransition(TaskStatus.IN_PROGRESS, TaskStatus.PENDING));
        }

        @Test
        @DisplayName("FAILED can transition to IN_PROGRESS (retry)")
        void failedCanTransitionToInProgress() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.FAILED, TaskStatus.IN_PROGRESS));
        }

        @Test
        @DisplayName("FAILED can transition to SKIPPED")
        void failedCanTransitionToSkipped() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.FAILED, TaskStatus.SKIPPED));
        }

        @Test
        @DisplayName("FAILED can transition to PENDING (reset)")
        void failedCanTransitionToPending() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.FAILED, TaskStatus.PENDING));
        }

        @Test
        @DisplayName("BLOCKED can transition to PENDING (unblock)")
        void blockedCanTransitionToPending() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.BLOCKED, TaskStatus.PENDING));
        }

        @Test
        @DisplayName("BLOCKED can transition to SKIPPED")
        void blockedCanTransitionToSkipped() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.BLOCKED, TaskStatus.SKIPPED));
        }

        @Test
        @DisplayName("COMPLETED can transition to PENDING (rollback)")
        void completedCanTransitionToPending() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.COMPLETED, TaskStatus.PENDING));
        }

        @Test
        @DisplayName("SKIPPED can transition to PENDING (retry)")
        void skippedCanTransitionToPending() {
            assertTrue(TaskStateMachine.canTransition(TaskStatus.SKIPPED, TaskStatus.PENDING));
        }
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("should transition task from PENDING to IN_PROGRESS")
        void shouldTransitionToInProgress() {
            // Given
            assertEquals(TaskStatus.PENDING, task.getStatus());

            // When
            TaskStateMachine.start(task);

            // Then
            assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
            assertNotNull(task.getStartedAt());
        }

        @Test
        @DisplayName("should record transition with actor")
        void shouldRecordTransitionWithActor() {
            // When
            TaskStateMachine.start(task, "test-user");

            // Then
            assertFalse(task.getTransitions().isEmpty());
            assertEquals("test-user", task.getTransitions().get(0).getActor());
            assertEquals(TaskStatus.PENDING, task.getTransitions().get(0).getFromStatus());
            assertEquals(TaskStatus.IN_PROGRESS, task.getTransitions().get(0).getToStatus());
        }

        @Test
        @DisplayName("should throw exception for invalid transition")
        void shouldThrowExceptionForInvalidTransition() {
            // Given
            task.setStatus(TaskStatus.COMPLETED);

            // When/Then
            assertThrows(IllegalStateException.class, () -> TaskStateMachine.start(task));
        }
    }

    @Nested
    @DisplayName("complete()")
    class Complete {

        @Test
        @DisplayName("should transition task from IN_PROGRESS to COMPLETED")
        void shouldTransitionToCompleted() {
            // Given
            task.setStatus(TaskStatus.IN_PROGRESS);

            // When
            TaskStateMachine.complete(task);

            // Then
            assertEquals(TaskStatus.COMPLETED, task.getStatus());
            assertNotNull(task.getCompletedAt());
        }

        @Test
        @DisplayName("should throw exception when not in progress")
        void shouldThrowExceptionWhenNotInProgress() {
            // Given
            assertEquals(TaskStatus.PENDING, task.getStatus());

            // When/Then
            assertThrows(IllegalStateException.class, () -> TaskStateMachine.complete(task));
        }
    }

    @Nested
    @DisplayName("fail()")
    class Fail {

        @Test
        @DisplayName("should transition task from IN_PROGRESS to FAILED")
        void shouldTransitionToFailed() {
            // Given
            task.setStatus(TaskStatus.IN_PROGRESS);
            String error = "Connection timeout";

            // When
            TaskStateMachine.fail(task, error);

            // Then
            assertEquals(TaskStatus.FAILED, task.getStatus());
            assertEquals(error, task.getError());
            assertNotNull(task.getCompletedAt());
        }

        @Test
        @DisplayName("should record error message")
        void shouldRecordErrorMessage() {
            // Given
            task.setStatus(TaskStatus.IN_PROGRESS);

            // When
            TaskStateMachine.fail(task, "Database error");

            // Then
            assertEquals("Database error", task.getError());
        }
    }

    @Nested
    @DisplayName("retry()")
    class Retry {

        @Test
        @DisplayName("should transition from FAILED to IN_PROGRESS")
        void shouldTransitionFromFailedToInProgress() {
            // Given
            task.setStatus(TaskStatus.FAILED);
            task.setError("Previous error");

            // When
            TaskStateMachine.retry(task);

            // Then
            assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
            assertNull(task.getError());
        }

        @Test
        @DisplayName("should increment retry count")
        void shouldIncrementRetryCount() {
            // Given
            task.setStatus(TaskStatus.FAILED);
            int initialRetryCount = task.getRetryCount();

            // When
            TaskStateMachine.retry(task);

            // Then
            assertEquals(initialRetryCount + 1, task.getRetryCount());
        }

        @Test
        @DisplayName("should clear timestamps")
        void shouldClearTimestamps() {
            // Given
            task.setStatus(TaskStatus.FAILED);

            // When
            TaskStateMachine.retry(task);

            // Then
            assertNull(task.getStartedAt());
            assertNull(task.getCompletedAt());
        }
    }

    @Nested
    @DisplayName("skip()")
    class Skip {

        @Test
        @DisplayName("should transition to SKIPPED with reason")
        void shouldTransitionToSkipped() {
            // Given - task is PENDING

            // When
            TaskStateMachine.skip(task, "Dependencies not met");

            // Then
            assertEquals(TaskStatus.SKIPPED, task.getStatus());
            assertNotNull(task.getCompletedAt());
        }

        @Test
        @DisplayName("should record skip reason in transition")
        void shouldRecordSkipReason() {
            // When
            TaskStateMachine.skip(task, "Manually skipped by user");

            // Then
            assertFalse(task.getTransitions().isEmpty());
            assertEquals("Manually skipped by user", task.getTransitions().get(0).getReason());
        }
    }

    @Nested
    @DisplayName("block()")
    class Block {

        @Test
        @DisplayName("should transition to BLOCKED with reason")
        void shouldTransitionToBlocked() {
            // Given - task is PENDING

            // When
            TaskStateMachine.block(task, "Waiting for gate approval");

            // Then
            assertEquals(TaskStatus.BLOCKED, task.getStatus());
        }
    }

    @Nested
    @DisplayName("unblock()")
    class Unblock {

        @Test
        @DisplayName("should transition from BLOCKED to PENDING")
        void shouldTransitionToPending() {
            // Given
            task.setStatus(TaskStatus.BLOCKED);

            // When
            TaskStateMachine.unblock(task);

            // Then
            assertEquals(TaskStatus.PENDING, task.getStatus());
        }
    }

    @Nested
    @DisplayName("reset()")
    class Reset {

        @Test
        @DisplayName("should transition to PENDING and clear all fields")
        void shouldResetTask() {
            // Given
            task.setStatus(TaskStatus.COMPLETED);
            task.setRetryCount(2);
            task.setError("Some error");
            task.setOutput(java.util.Map.of("key", "value"));

            // When
            TaskStateMachine.reset(task);

            // Then
            assertEquals(TaskStatus.PENDING, task.getStatus());
            assertEquals(0, task.getRetryCount());
            assertNull(task.getError());
            assertNull(task.getOutput());
            assertNull(task.getStartedAt());
            assertNull(task.getCompletedAt());
        }
    }

    @Nested
    @DisplayName("canRetry()")
    class CanRetry {

        @Test
        @DisplayName("should return true when FAILED and under max retries")
        void shouldReturnTrueWhenCanRetry() {
            // Given
            task.setStatus(TaskStatus.FAILED);
            task.setRetryCount(1);
            task.setMaxRetries(3);

            // When/Then
            assertTrue(TaskStateMachine.canRetry(task));
        }

        @Test
        @DisplayName("should return false when at max retries")
        void shouldReturnFalseWhenAtMaxRetries() {
            // Given
            task.setStatus(TaskStatus.FAILED);
            task.setRetryCount(3);
            task.setMaxRetries(3);

            // When/Then
            assertFalse(TaskStateMachine.canRetry(task));
        }

        @Test
        @DisplayName("should return false when not FAILED")
        void shouldReturnFalseWhenNotFailed() {
            // Given
            task.setStatus(TaskStatus.COMPLETED);

            // When/Then
            assertFalse(TaskStateMachine.canRetry(task));
        }
    }

    @Nested
    @DisplayName("isTerminalState()")
    class IsTerminalState {

        @ParameterizedTest
        @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "FAILED", "SKIPPED"})
        @DisplayName("should return true for terminal states")
        void shouldReturnTrueForTerminalStates(TaskStatus status) {
            assertTrue(TaskStateMachine.isTerminalState(status));
        }

        @ParameterizedTest
        @EnumSource(value = TaskStatus.class, names = {"PENDING", "IN_PROGRESS", "BLOCKED"})
        @DisplayName("should return false for non-terminal states")
        void shouldReturnFalseForNonTerminalStates(TaskStatus status) {
            assertFalse(TaskStateMachine.isTerminalState(status));
        }
    }

    @Nested
    @DisplayName("Transition Audit Trail")
    class TransitionAuditTrail {

        @Test
        @DisplayName("should record all transitions")
        void shouldRecordAllTransitions() {
            // When
            TaskStateMachine.start(task);
            TaskStateMachine.complete(task);

            // Then
            assertEquals(2, task.getTransitions().size());
        }

        @Test
        @DisplayName("transitions should have timestamps")
        void transitionsShouldHaveTimestamps() {
            // When
            TaskStateMachine.start(task);

            // Then
            assertNotNull(task.getTransitions().get(0).getTimestamp());
        }

        @Test
        @DisplayName("transitions should record from and to states")
        void transitionsShouldRecordStates() {
            // When
            TaskStateMachine.start(task);

            // Then
            assertEquals(TaskStatus.PENDING, task.getTransitions().get(0).getFromStatus());
            assertEquals(TaskStatus.IN_PROGRESS, task.getTransitions().get(0).getToStatus());
        }
    }
}
