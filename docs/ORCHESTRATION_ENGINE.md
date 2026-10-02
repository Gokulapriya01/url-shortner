# Orchestration Engine Documentation

**Version:** 1.0
**Date:** 2026-10-01
**Phase:** 7 - Orchestration Engine Implementation

---

## Overview

The Orchestration Engine is the core component of the Agentic URL Shortener's SDLC workflow system. It manages task execution, dependency resolution, approval gates, and provides full audit trails for all decisions.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Orchestration Engine                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐        │
│  │  DAG Builder │   │    State     │   │  Dependency  │        │
│  │              │──▶│   Machine    │──▶│   Resolver   │        │
│  └──────────────┘   └──────────────┘   └──────────────┘        │
│         │                  │                  │                 │
│         ▼                  ▼                  ▼                 │
│  ┌─────────────────────────────────────────────────────┐       │
│  │              Session Manager                          │       │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐ │       │
│  │  │  Tasks  │  │  Gates  │  │ Context │  │ Metrics │ │       │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘ │       │
│  └─────────────────────────────────────────────────────┘       │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────────────────────────────────────────┐       │
│  │                Event Emitter                          │       │
│  │   session_* | task_* | gate_* | phase_* events       │       │
│  └─────────────────────────────────────────────────────┘       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Components

### 1. Task DAG Builder

Builds a Directed Acyclic Graph from task definitions for dependency management.

```typescript
import { DAGBuilder, createDAGBuilder } from './orchestration/dagBuilder';

const dagBuilder = createDAGBuilder();
const dag = dagBuilder.build(taskDefinitions);

// dag.nodes: Map<string, DAGNode>
// dag.phases: Map<number, string[]>
// dag.executionOrder: string[]
```

**Features:**
- Cycle detection
- Topological sort (Kahn's algorithm)
- Phase grouping

### 2. Task State Machine

Manages task lifecycle with strict state transitions.

```
          ┌─────────┐
          │ PENDING │
          └────┬────┘
     ┌─────────┼─────────┐
     ▼         ▼         ▼
┌─────────┐ ┌───────┐ ┌─────────┐
│IN_PROGRESS│ │BLOCKED│ │ SKIPPED │
└────┬────┘ └───┬───┘ └─────────┘
     │          │
     │    ┌─────┘
     ▼    ▼
┌─────────┐
│COMPLETED│◀───────┐
└─────────┘        │
     ▲             │
     │      ┌──────┴──────┐
     │      │   FAILED    │
     │      └──────┬──────┘
     │             │
     └─────────────┘ (retry)
```

**Valid Transitions:**

| From | To | Event |
|------|----|-------|
| PENDING | IN_PROGRESS | START |
| PENDING | BLOCKED | BLOCK |
| PENDING | SKIPPED | SKIP |
| IN_PROGRESS | COMPLETED | COMPLETE |
| IN_PROGRESS | FAILED | FAIL |
| FAILED | IN_PROGRESS | RETRY |
| FAILED | SKIPPED | SKIP |
| BLOCKED | PENDING | UNBLOCK |
| BLOCKED | SKIPPED | SKIP |
| * | PENDING | RESET |

### 3. Dependency Resolver

Determines which tasks are ready for execution based on dependency status.

```typescript
import { DependencyResolver, createDependencyResolver } from './orchestration/dagBuilder';

const resolver = createDependencyResolver(dag);
resolver.updateTaskStatus('task-1', 'COMPLETED');

const readyTasks = resolver.getReadyTasks();
const phaseComplete = resolver.isPhaseComplete(1);
const stats = resolver.getStats();
```

### 4. Orchestration Engine

Core engine that ties all components together.

```typescript
import { createOrchestrationEngine } from './orchestration/engine';

const engine = createOrchestrationEngine({
  maxConcurrentTasks: 5,
  defaultMaxRetries: 3,
  enableRollback: true,
});

// Register tasks and gates
engine.registerTasks(taskDefinitions);
engine.registerGates(gateDefinitions);

// Create session
const session = engine.createSession('My Workflow');

// Execute
await engine.executeNext(session.id);
```

---

## API Reference

### Session Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orchestration/sessions` | POST | Create session |
| `/api/orchestration/sessions/:id` | GET | Get session |
| `/api/orchestration/sessions/:id/execute` | POST | Execute next tasks |
| `/api/orchestration/sessions/:id/pause` | POST | Pause session |
| `/api/orchestration/sessions/:id/resume` | POST | Resume session |
| `/api/orchestration/sessions/:id/cancel` | POST | Cancel session |
| `/api/orchestration/sessions/:id/rollback` | POST | Rollback to phase |

### Gate Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orchestration/sessions/:sid/gates/:gid/approve` | POST | Approve gate |
| `/api/orchestration/sessions/:sid/gates/:gid/reject` | POST | Reject gate |

### Context Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orchestration/sessions/:id/context` | PUT | Update context |
| `/api/orchestration/sessions/:id/context/:key` | GET | Get context value |

### Metrics

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orchestration/sessions/:id/metrics` | GET | Get session metrics |

---

## Events

The engine emits events for all significant actions:

| Event | Description |
|-------|-------------|
| `session_started` | New session created |
| `session_completed` | All tasks complete |
| `session_failed` | Session failed |
| `session_paused` | Session paused |
| `session_resumed` | Session resumed |
| `task_started` | Task execution began |
| `task_completed` | Task completed successfully |
| `task_failed` | Task failed |
| `task_retrying` | Task retry initiated |
| `task_skipped` | Task skipped |
| `gate_reached` | Phase complete, gate pending |
| `gate_approved` | Gate approved |
| `gate_rejected` | Gate rejected |
| `phase_started` | New phase began |
| `phase_completed` | Phase completed |
| `rollback_completed` | Rollback finished |

**Usage:**

```typescript
engine.on('task_completed', (event) => {
  console.log(`Task ${event.taskId} completed`);
});

engine.on('gate_approved', (event) => {
  console.log(`Gate ${event.gateId} approved`);
});
```

---

## Configuration

```typescript
interface OrchestrationConfig {
  maxConcurrentTasks: number;    // Default: 5
  defaultTaskTimeout: number;     // Default: 300000 (5 min)
  defaultMaxRetries: number;      // Default: 3
  retryDelayMs: number;           // Default: 1000
  enableRollback: boolean;        // Default: true
  autoApproveGates: boolean;      // Default: false
}
```

---

## Metrics

The engine tracks comprehensive metrics:

```typescript
interface OrchestrationMetrics {
  totalTasks: number;
  completedTasks: number;
  failedTasks: number;
  skippedTasks: number;
  retryCount: number;
  rollbackCount: number;
  averageTaskDurationMs: number;
  totalDurationMs: number;
}
```

---

## Example Workflow

```typescript
// 1. Define tasks
const tasks = [
  { id: 'setup', name: 'Setup', phase: 1, dependencies: [], acIds: ['AC-1'] },
  { id: 'build', name: 'Build', phase: 1, dependencies: ['setup'], acIds: ['AC-2'] },
  { id: 'test', name: 'Test', phase: 2, dependencies: ['build'], acIds: ['AC-3'] },
];

// 2. Define gates
const gates = [
  { id: 'gate-1', name: 'Phase 1 Gate', phase: 1, requiresApproval: true },
];

// 3. Create engine and register
const engine = createOrchestrationEngine();
engine.registerTasks(tasks);
engine.registerGates(gates);

// 4. Create session
const session = engine.createSession('My Project');

// 5. Approve gate and execute
engine.approveGate(session.id, session.gates[0].id, 'admin');
await engine.executeNext(session.id);

// 6. Check metrics
const metrics = engine.getMetrics(session.id);
console.log(`Progress: ${metrics.completedTasks}/${metrics.totalTasks}`);
```

---

## Files

```
src/
├── types/
│   └── orchestration.ts          # Type definitions
├── orchestration/
│   ├── engine.ts                 # Core engine
│   ├── stateMachine.ts           # Task state machine
│   ├── dagBuilder.ts             # DAG builder & dependency resolver
│   └── __tests__/
│       ├── engine.test.ts
│       └── stateMachine.test.ts
└── routes/
    └── orchestration.routes.ts   # API endpoints
```

---

## Integration with Specs Studio

The Orchestration Engine powers the Specs Studio workflow:

1. **Task Registration**: All 84 tasks registered from TASK_TRACEABILITY.md
2. **Gate Configuration**: 8 gates (one per phase) require human approval
3. **AC Traceability**: Each task linked to Acceptance Criteria
4. **Progress Tracking**: Real-time metrics and status updates
5. **Audit Trail**: Full history of all transitions and decisions
