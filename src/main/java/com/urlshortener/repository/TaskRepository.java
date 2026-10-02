package com.urlshortener.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.urlshortener.domain.entity.Task;
import com.urlshortener.domain.enums.TaskStatus;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Returns all task instances belonging to the session. */
    List<Task> findBySessionId(UUID sessionId);

    /** Returns session tasks ordered by phase and then execution order. */
    List<Task> findBySessionIdOrderByPhaseAscOrderIndexAsc(UUID sessionId);

    /** Returns the session tasks belonging to the requested phase. */
    List<Task> findBySessionIdAndPhase(UUID sessionId, int phase);

    /** Returns the phase tasks in execution order. */
    List<Task> findBySessionIdAndPhaseOrderByOrderIndex(UUID sessionId, int phase);

    /** Returns session tasks with the requested status. */
    List<Task> findBySessionIdAndStatus(UUID sessionId, TaskStatus status);

    /** Returns session tasks whose status is one of the supplied values. */
    List<Task> findBySessionIdAndStatusIn(UUID sessionId, List<TaskStatus> statuses);

    /** Returns pending session tasks ordered by phase and execution order. */
    @Query("SELECT t FROM Task t WHERE t.sessionId = :sessionId AND t.status = 'PENDING' ORDER BY t.phase, t.orderIndex")
    List<Task> findPendingTasksBySessionId(@Param("sessionId") UUID sessionId);

    /** Counts session tasks with the requested status. */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.sessionId = :sessionId AND t.status = :status")
    long countBySessionIdAndStatus(@Param("sessionId") UUID sessionId, @Param("status") TaskStatus status);

    /** Returns distinct session phase numbers in ascending order. */
    @Query("SELECT DISTINCT t.phase FROM Task t WHERE t.sessionId = :sessionId ORDER BY t.phase")
    List<Integer> findDistinctPhasesBySessionId(@Param("sessionId") UUID sessionId);

    /** Returns the task instance matching the session and definition identifier, or null when absent. */
    @Query("SELECT t FROM Task t WHERE t.sessionId = :sessionId AND t.definitionId = :definitionId")
    Task findBySessionIdAndDefinitionId(@Param("sessionId") UUID sessionId, @Param("definitionId") String definitionId);
}
