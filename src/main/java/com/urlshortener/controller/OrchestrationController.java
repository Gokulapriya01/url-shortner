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





    /** Marks a session cancelled and prevents subsequent task starts. */
    @PostMapping("/sessions/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelSession(@PathVariable UUID id) {
        boolean success = orchestrationEngine.cancelSession(id);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true));
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
