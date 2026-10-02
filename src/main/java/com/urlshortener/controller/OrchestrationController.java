package com.urlshortener.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.domain.entity.Task;
import com.urlshortener.dto.request.ContextUpdateRequest;
import com.urlshortener.dto.request.GateApprovalRequest;
import com.urlshortener.dto.request.GateRegistrationRequest;
import com.urlshortener.dto.request.SessionCreateRequest;
import com.urlshortener.dto.request.TaskRegistrationRequest;
import com.urlshortener.dto.response.MetricsResponse;
import com.urlshortener.dto.response.SessionResponse;
import com.urlshortener.orchestration.OrchestrationEngine;
import com.urlshortener.orchestration.OrchestrationEngine.OrchestrationSession;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/orchestration")
@RequiredArgsConstructor
@Slf4j
public class OrchestrationController {

    private final OrchestrationEngine orchestrationEngine;

    /** Registers task definitions for subsequently created sessions. */
    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> registerTasks(
            @Valid @RequestBody TaskRegistrationRequest request) {
        orchestrationEngine.registerTasks(request.getTasks());
        return ResponseEntity.ok(Map.of(
            "success", true,
            "registeredCount", request.getTasks().size()
        ));
    }

    /** Registers gate definitions for subsequently created sessions. */
    @PostMapping("/gates")
    public ResponseEntity<Map<String, Object>> registerGates(
            @Valid @RequestBody GateRegistrationRequest request) {
        orchestrationEngine.registerGates(request.getGates());
        return ResponseEntity.ok(Map.of(
            "success", true,
            "registeredCount", request.getGates().size()
        ));
    }

    /** Creates an independent session with task instances, gates, DAG and shared context. */
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse createSession(@Valid @RequestBody SessionCreateRequest request) {
        OrchestrationSession session = orchestrationEngine.createSession(request.getName());
        return SessionResponse.from(session);
    }

    /** Returns the session when its identifier is known. */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable UUID id) {
        return orchestrationEngine.getSession(id)
            .map(SessionResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Executes one ready task batch concurrently, respecting dependencies and approval gates. */
    @PostMapping("/sessions/{id}/execute")
    public ResponseEntity<Map<String, Object>> executeNext(@PathVariable UUID id) {
        List<Task> executedTasks = orchestrationEngine.executeNext(id);
        return ResponseEntity.ok(Map.of(
            "executedCount", executedTasks.size(),
            "tasks", executedTasks.stream()
                .map(t -> Map.of(
                    "id", t.getId(),
                    "name", t.getName(),
                    "status", t.getStatus()
                ))
                .toList()
        ));
    }

    /** Prevents subsequent task starts while allowing active handlers to reach a checkpoint. */
    @PostMapping("/sessions/{id}/pause")
    public ResponseEntity<Map<String, Object>> pauseSession(@PathVariable UUID id) {
        boolean success = orchestrationEngine.pauseSession(id);
        if (!success) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Cannot pause session"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Reactivates a paused session without discarding its context. */
    @PostMapping("/sessions/{id}/resume")
    public ResponseEntity<Map<String, Object>> resumeSession(@PathVariable UUID id) {
        boolean success = orchestrationEngine.resumeSession(id);
        if (!success) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Cannot resume session"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Marks a session cancelled and prevents subsequent task starts. */
    @PostMapping("/sessions/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelSession(@PathVariable UUID id) {
        boolean success = orchestrationEngine.cancelSession(id);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Rollback. */
    @PostMapping("/sessions/{id}/rollback")
    public ResponseEntity<Map<String, Object>> rollback(
            @PathVariable UUID id,
            @RequestBody Map<String, Integer> request) {
        Integer targetPhase = request.get("targetPhase");
        if (targetPhase == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "targetPhase is required"));
        }

        boolean success = orchestrationEngine.rollbackToPhase(id, targetPhase);
        if (!success) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Rollback failed"));
        }
        return ResponseEntity.ok(Map.of("success", true, "rolledBackToPhase", targetPhase));
    }

    /** Records approval and the actor for a session gate. */
    @PostMapping("/sessions/{sessionId}/gates/{gateId}/approve")
    public ResponseEntity<Map<String, Object>> approveGate(
            @PathVariable UUID sessionId,
            @PathVariable UUID gateId,
            @Valid @RequestBody GateApprovalRequest request) {
        boolean success = orchestrationEngine.approveGate(sessionId, gateId, request.getApprovedBy());
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Revokes gate approval and records the rejection reason. */
    @PostMapping("/sessions/{sessionId}/gates/{gateId}/reject")
    public ResponseEntity<Map<String, Object>> rejectGate(
            @PathVariable UUID sessionId,
            @PathVariable UUID gateId,
            @RequestBody Map<String, String> request) {
        String reason = request.get("reason");
        if (reason == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "reason is required"));
        }

        boolean success = orchestrationEngine.rejectGate(sessionId, gateId, reason);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Replaces completed upstream output and replans its downstream tasks. */
    @PutMapping("/sessions/{id}/tasks/{definitionId}/output")
    public ResponseEntity<Map<String, Object>> updateOutput(@PathVariable UUID id,
            @PathVariable String definitionId, @RequestBody Map<String, Object> output) {
        return orchestrationEngine.updateTaskOutput(id, definitionId, output)
            ? ResponseEntity.ok(Map.of("success", true)) : ResponseEntity.notFound().build();
    }

    /** Returns task transition timestamps, states, actors and reasons for a session. */
    @GetMapping("/sessions/{id}/audit")
    public ResponseEntity<?> audit(@PathVariable UUID id) {
        return orchestrationEngine.getSession(id).map(session -> ResponseEntity.ok(session.getTasks().stream()
            .flatMap(task -> task.getTransitions().stream()).map(t -> Map.of("timestamp", t.getTimestamp(),
                "taskId", t.getTask().getId(), "from", t.getFromStatus(), "to", t.getToStatus(),
                "actor", t.getActor(), "reason", t.getReason())).toList()))
            .orElse(ResponseEntity.notFound().build());
    }

    /** Returns current task counts and cumulative retry, rollback, latency and recovery telemetry. */
    @GetMapping("/sessions/{id}/metrics")
    public ResponseEntity<MetricsResponse> getMetrics(@PathVariable UUID id) {
        return orchestrationEngine.getMetrics(id)
            .map(MetricsResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Stores a shared context value for later workflow stages. */
    @PutMapping("/sessions/{id}/context")
    public ResponseEntity<Map<String, Object>> updateContext(
            @PathVariable UUID id,
            @Valid @RequestBody ContextUpdateRequest request) {
        boolean success = orchestrationEngine.updateContext(id, request.getKey(), request.getValue());
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Returns a shared context value from the session. */
    @GetMapping("/sessions/{id}/context/{key}")
    public ResponseEntity<Map<String, Object>> getContextValue(
            @PathVariable UUID id,
            @PathVariable String key) {
        Object value = orchestrationEngine.getContextValue(id, key);
        return ResponseEntity.ok(Map.of("key", key, "value", value != null ? value : ""));
    }
}
