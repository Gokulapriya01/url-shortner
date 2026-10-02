# Orchestration Engine Design

**Document ID:** ORCH-001
**Version:** 2.0
**Date:** 2026-10-01
**Updated:** 2026-10-01 (Java/Spring Boot Migration)
**Status:** Approved

---

## 1. Overview

The Orchestration Engine is the core differentiator of this agentic system. It coordinates the full SDLC lifecycle with:

- **Task DAG:** Directed Acyclic Graph for dependencies
- **State Machine:** Lifecycle management for each task
- **Human Gates:** Approval checkpoints for controlled autonomy
- **Fault Handling:** Retry, rollback, and recovery
- **Audit Trail:** Full traceability of all decisions

---

## 2. Core Components

### 2.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ORCHESTRATION ENGINE                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐         │
│  │   Task Store    │    │   DAG Manager   │    │  State Machine  │         │
│  │                 │◄───│                 │◄───│                 │         │
│  │ - CRUD tasks    │    │ - Build graph   │    │ - Transitions   │         │
│  │ - Persistence   │    │ - Topo sort     │    │ - Validation    │         │
│  │ - Queries       │    │ - Dependencies  │    │ - Events        │         │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘         │
│           │                      │                      │                   │
│           └──────────────────────┼──────────────────────┘                   │
│                                  │                                          │
│                                  ▼                                          │
│                    ┌─────────────────────────┐                             │
│                    │       Executor          │                             │
│                    │                         │                             │
│                    │ - Sequential execution  │                             │
│                    │ - Parallel execution    │                             │
│                    │ - Gate handling         │                             │
│                    └───────────┬─────────────┘                             │
│                                │                                            │
│           ┌────────────────────┼────────────────────┐                      │
│           ▼                    ▼                    ▼                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐            │
│  │  Retry Handler  │  │ Rollback Handler│  │  Audit Logger   │            │
│  │                 │  │                 │  │                 │            │
│  │ - Exp backoff   │  │ - Undo actions  │  │ - Transitions   │            │
│  │ - Max attempts  │  │ - Compensate    │  │ - Decisions     │            │
│  │ - Fallback      │  │ - Safe state    │  │ - Timestamps    │            │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘            │
│                                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐            │
│  │  Gate Manager   │  │ Context Store   │  │ Metrics Tracker │            │
│  │                 │  │                 │  │                 │            │
│  │ - Approval flow │  │ - Cross-stage   │  │ - Success rate  │            │
│  │ - Notifications │  │ - Decision data │  │ - MTTR          │            │
│  │ - Timeout       │  │ - Artifacts     │  │ - Latency       │            │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Task Model

### 3.1 Task Definition

```java
@Entity
@Table(name = "tasks")
public class Task {
    @Id
    private UUID id;
    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private Integer phase;
    private Integer orderIndex;

    @ElementCollection
    private List<String> dependencies;  // Task definition IDs

    @ElementCollection
    private List<String> acIds;         // Acceptance Criteria IDs

    // Execution state
    private Integer retryCount = 0;
    private Integer maxRetries = 3;
    private String error;

    @Type(JsonType.class)
    private Map<String, Object> output;

    // Timestamps
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    // Transitions
    @OneToMany(cascade = CascadeType.ALL)
    private List<TaskTransition> transitions;
}

public enum TaskStatus {
    PENDING,      // Not yet started
    IN_PROGRESS,  // Currently executing
    COMPLETED,    // Successfully finished
    FAILED,       // Failed after retries
    BLOCKED,      // Waiting for dependency or gate
    SKIPPED;      // Skipped with justification
}
```

### 3.2 Task State Machine

```
                        ┌──────────────────────────────────────┐
                        │            PENDING                    │
                        │  (Initial state for all tasks)       │
                        └────────────────┬─────────────────────┘
                                         │
                    ┌────────────────────┴─────────────────────┐
                    │                                          │
                    ▼                                          ▼
        ┌───────────────────┐                      ┌───────────────────┐
        │   IN_PROGRESS     │                      │     BLOCKED       │
        │                   │                      │                   │
        │ Entry: startedAt  │                      │ - Dependency wait │
        │ Do: execute task  │                      │ - Gate wait       │
        └─────────┬─────────┘                      └─────────┬─────────┘
                  │                                          │
     ┌────────────┼────────────┐                            │
     │            │            │                             │
     ▼            ▼            ▼                             │
┌─────────┐ ┌─────────┐ ┌─────────┐                         │
│COMPLETED│ │ FAILED  │ │ BLOCKED │◄────────────────────────┘
│         │ │         │ │         │
│Exit:    │ │Can retry│ │Wait for │
│complete │ │or skip  │ │unblock  │
└─────────┘ └────┬────┘ └─────────┘
                 │
                 │ retry (if count < max)
                 │
                 ▼
        ┌───────────────────┐
        │    IN_PROGRESS    │
        │   (retry attempt) │
        └───────────────────┘
                 │
                 │ max retries exceeded
                 ▼
        ┌───────────────────┐
        │     SKIPPED       │
        │ (with justification)│
        └───────────────────┘
```

### 3.3 Valid State Transitions

| From | To | Trigger | Validation |
|------|----|---------|------------|
| pending | in_progress | start() | Dependencies completed |
| pending | blocked | block() | Missing dependency or gate |
| in_progress | completed | complete() | Task succeeded |
| in_progress | failed | fail() | Task threw error |
| in_progress | blocked | block() | Runtime dependency |
| failed | in_progress | retry() | retryCount < maxRetries |
| failed | skipped | skip() | Justification required |
| blocked | in_progress | unblock() | Dependencies/gate satisfied |
| blocked | skipped | skip() | Justification required |

---

## 4. DAG Manager

### 4.1 Graph Representation

```typescript
interface DAG {
  nodes: Map<string, Task>;
  edges: Map<string, Set<string>>;  // taskId -> dependent taskIds

  addTask(task: Task): void;
  addDependency(fromId: string, toId: string): void;
  getReadyTasks(): Task[];  // Tasks with all deps completed
  topologicalSort(): Task[];
  detectCycles(): string[][] | null;
}
```

### 4.2 Dependency Resolution

```
Algorithm: Get Ready Tasks
─────────────────────────────────
Input: Current task states
Output: Tasks ready to execute

1. For each task T in PENDING state:
   a. Get all dependencies D of T
   b. If ALL tasks in D are COMPLETED:
      - Add T to ready queue
   c. If ANY task in D is FAILED/SKIPPED:
      - Mark T as BLOCKED

2. Return ready queue sorted by (phase, orderIndex)
```

### 4.3 Parallel Execution Groups

```
Phase 1:
├── Group 1 (parallel): T-1.1, T-1.4
├── Group 2 (sequential after T-1.1): T-1.2, T-1.3
└── Group 3 (sequential after T-1.4): T-1.5, T-1.6, T-1.7

Phase 2:
├── T-2.1 (after T-1.5)
├── Group 1 (parallel after T-2.1): T-2.2
├── Group 2 (parallel after T-2.2): T-2.3, T-2.5
└── ...
```

---

## 5. Executor

### 5.1 Execution Loop

```typescript
class Executor {
  async execute(session: Session): Promise<ExecutionResult> {
    while (true) {
      // 1. Check for gates
      const gate = await this.gateManager.getBlockingGate();
      if (gate && !gate.approved) {
        await this.waitForApproval(gate);
        continue;
      }

      // 2. Get ready tasks
      const readyTasks = await this.dag.getReadyTasks();
      if (readyTasks.length === 0) {
        if (this.allTasksComplete()) {
          return { status: 'completed' };
        }
        if (this.hasBlockedTasks()) {
          return { status: 'blocked' };
        }
        return { status: 'failed' };
      }

      // 3. Execute in parallel groups
      const groups = this.groupByParallelism(readyTasks);
      for (const group of groups) {
        await Promise.all(
          group.map(task => this.executeTask(task))
        );
      }
    }
  }

  private async executeTask(task: Task): Promise<void> {
    try {
      await this.stateMachine.transition(task, 'in_progress');
      const result = await this.runTask(task);
      await this.contextStore.set(task.id, result);
      await this.stateMachine.transition(task, 'completed');
    } catch (error) {
      await this.handleFailure(task, error);
    }
  }
}
```

### 5.2 Failure Handling

```typescript
private async handleFailure(task: Task, error: Error): Promise<void> {
  task.error = error.message;
  task.retryCount++;

  this.auditLogger.log({
    taskId: task.id,
    event: 'failure',
    error: error.message,
    retryCount: task.retryCount
  });

  if (task.retryCount < task.maxRetries) {
    // Exponential backoff
    const delay = Math.pow(2, task.retryCount) * 100;
    await sleep(delay);

    await this.stateMachine.transition(task, 'pending');
    // Will be picked up in next iteration
  } else {
    await this.stateMachine.transition(task, 'failed');

    // Check for rollback
    if (this.shouldRollback(task)) {
      await this.rollbackHandler.rollback(task);
    }
  }
}
```

---

## 6. Gate Manager

### 6.1 Gate Model

```typescript
interface Gate {
  id: string;
  name: string;
  phase: number;
  requiresApproval: boolean;
  approved: boolean;
  approvedBy?: string;
  approvedAt?: Date;
  rejectedReason?: string;
}
```

### 6.2 Approval Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                     GATE APPROVAL FLOW                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. Phase completes all pre-gate tasks                          │
│                                                                  │
│  2. Executor encounters gate                                    │
│     ┌─────────────────────────────────────────────┐             │
│     │  GATE: Phase 1 Architecture Approval        │             │
│     │                                             │             │
│     │  Completed Tasks: T-1.1 through T-1.7      │             │
│     │  Artifacts: ARCHITECTURE.md, schema.prisma │             │
│     │                                             │             │
│     │  Status: AWAITING_APPROVAL                 │             │
│     └─────────────────────────────────────────────┘             │
│                                                                  │
│  3. Human reviews artifacts                                     │
│                                                                  │
│  4. Human action:                                               │
│     ├── APPROVE → Gate opens, Phase 2 begins                   │
│     ├── REJECT  → Gate closes, provide reason                  │
│     └── REQUEST_CHANGES → Specific feedback, re-work           │
│                                                                  │
│  5. If approved:                                                │
│     - Log approval with timestamp and actor                    │
│     - Unblock dependent tasks                                  │
│     - Continue execution                                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. Context Store

### 7.1 Cross-Stage Context

```typescript
interface ContextStore {
  // Store task outputs
  set(key: string, value: unknown): Promise<void>;

  // Retrieve for downstream tasks
  get<T>(key: string): Promise<T | undefined>;

  // Get all context for a session
  getAll(): Promise<Record<string, unknown>>;

  // Decision lineage
  recordDecision(decision: Decision): Promise<void>;
  getDecisionHistory(): Promise<Decision[]>;
}

interface Decision {
  id: string;
  taskId: string;
  timestamp: Date;
  type: 'automated' | 'human';
  description: string;
  rationale?: string;
  alternatives?: string[];
  selectedOption: string;
}
```

### 7.2 Context Flow Example

```
T-1.5 (Design Schema)
    │
    └── Output: { schema: "prisma/schema.prisma", tables: ["urls", "click_events"] }
                         │
                         ▼
T-2.1 (Setup Database)
    │
    └── Input: context.get("T-1.5").schema
    └── Output: { migrated: true, connection: "postgres://..." }
                         │
                         ▼
T-2.2 (Create Models)
    │
    └── Input: context.get("T-2.1").connection
    └── Output: { models: ["Url", "ClickEvent"] }
```

---

## 8. Audit Logger

### 8.1 Audit Events

```typescript
type AuditEvent =
  | TaskTransitionEvent
  | GateApprovalEvent
  | RetryEvent
  | RollbackEvent
  | DecisionEvent;

interface TaskTransitionEvent {
  type: 'task_transition';
  taskId: string;
  fromStatus: TaskStatus;
  toStatus: TaskStatus;
  timestamp: Date;
  actor: 'system' | 'human';
  reason?: string;
}

interface GateApprovalEvent {
  type: 'gate_approval';
  gateId: string;
  approved: boolean;
  approvedBy: string;
  timestamp: Date;
  comment?: string;
}
```

### 8.2 Audit Log Format

```json
{
  "timestamp": "2026-10-01T10:30:00Z",
  "sessionId": "sess-001",
  "event": {
    "type": "task_transition",
    "taskId": "T-1.1",
    "fromStatus": "pending",
    "toStatus": "in_progress",
    "actor": "system"
  },
  "context": {
    "phase": 1,
    "dependencies": [],
    "retryCount": 0
  }
}
```

---

## 9. Metrics Tracker

### 9.1 Tracked Metrics

| Metric | Description | Calculation |
|--------|-------------|-------------|
| **Success Rate** | % of tasks completed successfully | completed / (completed + failed) |
| **Retry Frequency** | Average retries per task | total_retries / total_tasks |
| **MTTR** | Mean Time To Recovery | avg(recovery_time - failure_time) |
| **End-to-End Latency** | Total execution time | end_time - start_time |
| **Gate Wait Time** | Time waiting for approvals | sum(approval_time - gate_reached) |

### 9.2 Metrics API

```typescript
interface OrchestrationMetrics {
  sessionId: string;
  totalTasks: number;
  completedTasks: number;
  failedTasks: number;
  skippedTasks: number;
  retryCount: number;
  rollbackCount: number;
  startTime: Date;
  endTime?: Date;
  latencyMs?: number;

  // Derived
  successRate: number;
  averageTaskDuration: number;
  gateWaitTime: number;
}
```

---

## 10. Dynamic Re-Planning

### 10.1 Trigger Conditions

Re-planning is triggered when:
1. Upstream task output differs from expected
2. Human rejects a gate with change requests
3. Task fails and cannot be retried
4. External dependency changes

### 10.2 Re-Plan Algorithm

```
Algorithm: Dynamic Re-Plan
──────────────────────────────────
Input: Changed task T, New output O
Output: Updated DAG

1. Identify affected tasks A = getAllDependents(T)

2. For each task D in A:
   a. If D.status == 'completed':
      - Check if output still valid
      - If invalid, mark for re-execution
   b. If D.status == 'in_progress':
      - Abort and mark for re-execution
   c. If D.status == 'pending':
      - Update expected inputs

3. Rebuild dependency graph
4. Validate new DAG (check cycles)
5. Resume execution from earliest affected task
```

---

## 11. API Endpoints

### 11.1 Orchestration REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/orchestration/tasks | List all tasks |
| GET | /api/orchestration/tasks/:id | Get task details |
| POST | /api/orchestration/tasks/:id/retry | Retry failed task |
| POST | /api/orchestration/tasks/:id/skip | Skip task |
| GET | /api/orchestration/gates | List all gates |
| POST | /api/orchestration/gates/:id/approve | Approve gate |
| POST | /api/orchestration/gates/:id/reject | Reject gate |
| GET | /api/orchestration/metrics | Get metrics |
| GET | /api/orchestration/audit | Get audit log |
| POST | /api/orchestration/pause | Pause execution |
| POST | /api/orchestration/resume | Resume execution |

---

## 12. Implementation Plan

### 12.1 File Structure

```
src/orchestration/
├── index.ts              # Public exports
├── types.ts              # Type definitions
├── task.ts               # Task model
├── dag.ts                # DAG manager
├── stateMachine.ts       # State machine
├── executor.ts           # Execution engine
├── gateManager.ts        # Gate handling
├── contextStore.ts       # Cross-stage context
├── auditLogger.ts        # Audit logging
├── metricsTracker.ts     # Metrics collection
├── retryHandler.ts       # Retry logic
├── rollbackHandler.ts    # Rollback logic
├── rePlanner.ts          # Dynamic re-planning
└── routes.ts             # REST API routes
```

### 12.2 Dependencies

```json
{
  "dependencies": {
    "eventemitter3": "^5.0.0",  // Event handling
    "graphlib": "^2.1.8"         // Graph operations (optional)
  }
}
```

---

**Document Approval:**

| Role | Approved | Date |
|------|----------|------|
| Architect | Yes | 2026-10-01 |
