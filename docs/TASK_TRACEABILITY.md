# Task Traceability Matrix

**Document ID:** TRACE-001
**Version:** 2.0
**Generated:** 2026-10-01
**Updated:** 2026-10-01 (Java/Spring Boot Migration)
**Status:** `PLANNED`

---

## AC → Task Mapping

This document traces each Acceptance Criteria to its implementing task(s).

---

### Phase 1: Foundation & Architecture

| Task ID | Task | Implements AC | Dependencies | Status |
|---------|------|---------------|--------------|--------|
| T-1.1 | Initialize Java 21/Spring Boot 3.2 project | AC-10.4, AC-10.5 | - | `completed` |
| T-1.2 | Configure Maven build + Checkstyle | AC-10.4 | T-1.1 | `completed` |
| T-1.3 | Setup project structure (layers) | AC-10.1 | T-1.1 | `completed` |
| T-1.4 | Create architecture design document | AC-9.1 | - | `completed` |
| T-1.5 | Design database schema | AC-1.*, AC-2.1 | T-1.4 | `completed` |
| T-1.6 | Design API specifications (OpenAPI) | AC-6.4, AC-9.3 | T-1.4 | `completed` |
| T-1.7 | Design orchestration engine architecture | AC-5.1, AC-5.2 | T-1.4 | `completed` |

**Gate 1:** Architecture approval required before Phase 2 ✅

---

### Phase 2: Core URL Shortener (Greenfield - AC-6)

| Task ID | Task | Implements AC | Dependencies | Status |
|---------|------|---------------|--------------|--------|
| T-2.1 | Setup PostgreSQL + Spring Data JPA | AC-1.* | T-1.5 | `completed` |
| T-2.2 | Create Url entity + Flyway migrations | AC-1.1, AC-1.2 | T-2.1 | `completed` |
| T-2.3 | Implement ShortCodeGenerator | AC-1.5, AC-1.6 | T-2.2 | `completed` |
| T-2.4 | Implement UrlService | AC-1.1, AC-1.3 | T-2.3 | `completed` |
| T-2.5 | Implement redirect logic | AC-1.2, AC-1.4 | T-2.2 | `completed` |
| T-2.6 | Create UrlController (POST /api/shorten) | AC-1.1, AC-1.3 | T-2.4 | `completed` |
| T-2.7 | Create RedirectController (GET /{code}) | AC-1.2, AC-1.4 | T-2.5 | `completed` |
| T-2.8 | Add Jakarta Bean Validation | AC-1.3, AC-4.3, AC-4.4 | T-2.6 | `completed` |
| T-2.9 | Write unit tests - UrlService | AC-1.5, AC-1.6, AC-10.2 | T-2.4 | `completed` |
| T-2.10 | Write unit tests - ShortCodeGenerator | AC-10.2 | T-2.3 | `completed` |
| T-2.11 | Write unit tests - UrlValidator | AC-6.3, AC-10.3 | T-2.8 | `completed` |

**Gate 2:** Core functionality approval required before Phase 3 ✅

---

### Phase 3: Analytics & Observability (AC-2)

| Task ID | Task | Implements AC | Dependencies | Status |
|---------|------|---------------|--------------|--------|
| T-3.1 | Create ClickEvent and ClickAggregate entities | AC-2.1 | T-2.2 | `completed` |
| T-3.2 | Implement ClickTrackingService | AC-2.1 | T-3.1 | `completed` |
| T-3.3 | Implement async event queue | AC-2.3 | T-3.2 | `completed` |
| T-3.4 | Implement AnalyticsService aggregation | AC-2.5 | T-3.3 | `completed` |
| T-3.5 | Create AnalyticsController | AC-2.2, AC-2.4 | T-3.4 | `completed` |
| T-3.6 | Add structured logging (SLF4J/Logback) | AC-5.6 | T-2.6, T-2.7 | `completed` |
| T-3.7 | Implement metrics collection | AC-5.9 | T-3.6 | `completed` |
| T-3.8 | Write analytics unit tests | AC-10.2 | T-3.4 | `completed` |
| T-3.9 | Write analytics integration tests | AC-10.3 | T-3.5 | `completed` |

**Gate 3:** Analytics approval required before Phase 4 ✅

---

### Phase 4: Reliability & Security (AC-3, AC-4)

| Task ID | Task | Implements AC | Dependencies | Status |
|---------|------|---------------|--------------|--------|
| T-4.1 | Setup Spring Data Redis | AC-3.2 | T-2.1 | `completed` |
| T-4.2 | Implement CacheService (cache-aside) | AC-3.1, AC-3.2 | T-4.1 | `completed` |
| T-4.3 | Add RateLimitService (Bucket4j) | AC-4.1, AC-4.2 | T-2.6, T-2.7 | `completed` |
| T-4.4 | Implement UrlValidator (security) | AC-4.3 | T-2.8 | `completed` |
| T-4.5 | Add SecurityConfig (Spring Security) | AC-4.5 | T-2.6 | `completed` |
| T-4.6 | Implement Resilience4j retry | AC-3.3 | T-2.1 | `completed` |
| T-4.7 | Implement Resilience4j circuit breaker | AC-3.4 | T-4.6 | `completed` |
| T-4.8 | Create HealthController | AC-3.5 | T-2.1, T-4.1 | `completed` |
| T-4.9 | Implement graceful shutdown | AC-3.6 | T-4.8 | `completed` |
| T-4.10 | Write reliability tests | AC-3.3, AC-3.4 | T-4.6, T-4.7 | `completed` |
| T-4.11 | Write security tests | AC-4.3, AC-4.4 | T-4.4 | `completed` |
| T-4.12 | Write load tests | AC-3.1, AC-4.1, AC-4.2 | T-4.2, T-4.3 | `completed` |

**Gate 4:** Reliability & Security approval required before Phase 5 ✅

---

### Phase 5: Brownfield Scenario (AC-7)

| Task ID | Task | Implements AC | Dependencies | Status |
|---------|------|---------------|--------------|--------|
| T-5.1 | Document impact analysis | AC-7.2 | Phase 4 | `completed` |
| T-5.2 | Design custom alias feature | AC-7.4 | T-5.1 | `completed` |
| T-5.3 | Modify Url entity for aliases | AC-7.4 | T-5.2 | `completed` |
| T-5.4 | Update UrlService for aliases | AC-7.4 | T-5.3 | `completed` |
| T-5.5 | Update POST /api/shorten for alias param | AC-7.4 | T-5.4 | `completed` |
| T-5.6 | Design URL expiration feature | AC-7.5 | T-5.1 | `completed` |
| T-5.7 | Add expiresAt field to Url entity | AC-7.5 | T-5.6 | `completed` |
| T-5.8 | Implement expiration check | AC-7.5 | T-5.7 | `completed` |
| T-5.9 | Update API for TTL param | AC-7.5 | T-5.8 | `completed` |
| T-5.10 | Run regression tests | AC-7.3 | T-5.5, T-5.9 | `completed` |
| T-5.11 | Write new feature tests | AC-10.3 | T-5.5, T-5.9 | `completed` |

**Gate 5:** Brownfield changes approval required before Phase 6 ✅

---

### Phase 6: Ambiguous Requirement Scenario (AC-8)

| Task ID | Task | Implements AC | Dependencies | Status |
|---------|------|---------------|--------------|--------|
| T-6.1 | Present ambiguous requirement | AC-8.1 | Phase 4 | `completed` |
| T-6.2 | Document identified ambiguities | AC-8.1 | T-6.1 | `completed` |
| T-6.3 | Generate clarifying questions | AC-8.2 | T-6.2 | `completed` |
| T-6.4 | **[HUMAN INPUT REQUIRED]** | AC-8.2 | T-6.3 | `completed` |
| T-6.5 | Document assumptions | AC-8.3 | T-6.4 | `completed` |
| T-6.6 | Create normalized spec | AC-8.4 | T-6.5 | `completed` |
| T-6.7 | Implement clarified feature | AC-8.5 | T-6.6 | `completed` |
| T-6.8 | Write acceptance tests | AC-8.5 | T-6.7 | `completed` |

**Gate 6:** Ambiguous requirement resolution approval required ✅

---

### Phase 7: Orchestration Engine (AC-5)

| Task ID | Task | Implements AC | Dependencies | Status |
|---------|------|---------------|--------------|--------|
| T-7.1 | Implement Task entity | AC-5.2 | T-1.7 | `completed` |
| T-7.2 | Implement DAGBuilder | AC-5.1 | T-7.1 | `completed` |
| T-7.3 | Implement TaskStateMachine | AC-5.2 | T-7.1 | `completed` |
| T-7.4 | Implement DependencyResolver | AC-5.1, AC-5.4 | T-7.2 | `completed` |
| T-7.5 | Implement parallel executor | AC-5.4 | T-7.4 | `completed` |
| T-7.6 | Implement Gate entity and approval | AC-5.3 | T-7.3 | `completed` |
| T-7.7 | Implement retry mechanism | AC-5.5 | T-7.3 | `completed` |
| T-7.8 | Implement rollback mechanism | AC-5.5 | T-7.7 | `completed` |
| T-7.9 | Implement TaskTransition audit | AC-5.6 | T-7.3 | `completed` |
| T-7.10 | Implement ExecutionContext | AC-5.7 | T-7.5 | `completed` |
| T-7.11 | Implement pause/resume | AC-5.8 | T-7.6 | `completed` |
| T-7.12 | Implement OrchestrationMetrics | AC-5.9 | T-7.9 | `completed` |
| T-7.13 | Implement dynamic re-planner | AC-5.10 | T-7.4 | `completed` |
| T-7.14 | Create OrchestrationController | AC-5.* | T-7.1-T-7.13 | `completed` |
| T-7.15 | Write TaskStateMachine unit tests | AC-10.2 | T-7.3 | `completed` |
| T-7.16 | Write DAGBuilder unit tests | AC-10.3 | T-7.2 | `completed` |

**Gate 7:** Orchestration engine approval required before Phase 8 ✅

---

### Phase 8: Documentation & Delivery (AC-9)

| Task ID | Task | Implements AC | Dependencies | Status |
|---------|------|---------------|--------------|--------|
| T-8.1 | Write architecture overview | AC-9.1 | All | `completed` |
| T-8.2 | Create system diagrams | AC-9.1 | T-8.1 | `completed` |
| T-8.3 | Write README with setup instructions | AC-9.2 | All | `completed` |
| T-8.4 | Generate final OpenAPI spec | AC-9.3 | All | `completed` |
| T-8.5 | Write testing documentation | AC-9.4 | All | `completed` |
| T-8.6 | Document limitations & trade-offs | AC-9.5 | All | `completed` |
| T-8.7 | Create final engineering summary | AC-9.6 | All | `completed` |
| T-8.8 | Run full test suite | AC-10.2, AC-10.3 | All | `completed` |
| T-8.9 | Generate coverage report | AC-10.2 | T-8.8 | `completed` |
| T-8.10 | Final validation & packaging | AC-6.1, AC-7.1, AC-8.5 | All | `completed` |

**Gate 8:** Final delivery approval ✅

---

## Task Summary

| Phase | Task Count | AC Coverage | Status |
|-------|------------|-------------|--------|
| Phase 1: Foundation | 7 | AC-5.1, AC-5.2, AC-9.1, AC-9.3, AC-10.1, AC-10.4, AC-10.5 | ✅ |
| Phase 2: Core | 11 | AC-1.*, AC-4.3, AC-4.4, AC-6.3, AC-10.2, AC-10.3 | ✅ |
| Phase 3: Analytics | 9 | AC-2.*, AC-5.6, AC-5.9, AC-10.2, AC-10.3 | ✅ |
| Phase 4: Reliability | 12 | AC-3.*, AC-4.*, AC-10.3 | ✅ |
| Phase 5: Brownfield | 11 | AC-7.* | ✅ |
| Phase 6: Ambiguous | 8 | AC-8.* | ✅ |
| Phase 7: Orchestration | 16 | AC-5.*, AC-10.2, AC-10.3 | ✅ |
| Phase 8: Documentation | 10 | AC-6.1, AC-7.1, AC-8.5, AC-9.*, AC-10.2, AC-10.3 | ✅ |
| **Total** | **84** | **58 ACs (100%)** | **✅** |

---

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL (Spring Data JPA) |
| Cache | Redis (Spring Data Redis) |
| Migrations | Flyway |
| Validation | Jakarta Bean Validation |
| Resilience | Resilience4j |
| Rate Limiting | Bucket4j |
| Logging | SLF4J/Logback |
| Testing | JUnit 5 + Mockito + Testcontainers |

---

## Coverage Validation

| AC ID | Implementing Tasks | Covered |
|-------|-------------------|---------|
| AC-1.1 | T-2.4, T-2.6 | ✓ |
| AC-1.2 | T-2.5, T-2.7 | ✓ |
| AC-1.3 | T-2.4, T-2.6, T-2.8 | ✓ |
| AC-1.4 | T-2.5, T-2.7 | ✓ |
| AC-1.5 | T-2.3, T-2.10 | ✓ |
| AC-1.6 | T-2.3, T-2.10 | ✓ |
| AC-2.1 | T-3.1, T-3.2 | ✓ |
| AC-2.2 | T-3.5 | ✓ |
| AC-2.3 | T-3.3 | ✓ |
| AC-2.4 | T-3.5 | ✓ |
| AC-2.5 | T-3.4 | ✓ |
| AC-3.1 | T-4.2, T-4.12 | ✓ |
| AC-3.2 | T-4.1, T-4.2 | ✓ |
| AC-3.3 | T-4.6, T-4.10 | ✓ |
| AC-3.4 | T-4.7, T-4.10 | ✓ |
| AC-3.5 | T-4.8 | ✓ |
| AC-3.6 | T-4.9 | ✓ |
| AC-4.1 | T-4.3, T-4.12 | ✓ |
| AC-4.2 | T-4.3, T-4.12 | ✓ |
| AC-4.3 | T-2.8, T-4.4, T-4.11 | ✓ |
| AC-4.4 | T-2.8, T-4.11 | ✓ |
| AC-4.5 | T-4.5 | ✓ |
| AC-5.1 | T-1.7, T-7.2, T-7.4 | ✓ |
| AC-5.2 | T-1.7, T-7.1, T-7.3 | ✓ |
| AC-5.3 | T-7.6 | ✓ |
| AC-5.4 | T-7.4, T-7.5 | ✓ |
| AC-5.5 | T-7.7, T-7.8 | ✓ |
| AC-5.6 | T-3.6, T-7.9 | ✓ |
| AC-5.7 | T-7.10 | ✓ |
| AC-5.8 | T-7.11 | ✓ |
| AC-5.9 | T-3.7, T-7.12 | ✓ |
| AC-5.10 | T-7.13 | ✓ |
| AC-6.1 | T-8.10 | ✓ |
| AC-6.2 | Phase 1-2 | ✓ |
| AC-6.3 | T-2.11 | ✓ |
| AC-6.4 | T-1.6, T-8.4 | ✓ |
| AC-7.1 | T-8.10 | ✓ |
| AC-7.2 | T-5.1 | ✓ |
| AC-7.3 | T-5.10 | ✓ |
| AC-7.4 | T-5.2 - T-5.5 | ✓ |
| AC-7.5 | T-5.6 - T-5.9 | ✓ |
| AC-8.1 | T-6.1, T-6.2 | ✓ |
| AC-8.2 | T-6.3, T-6.4 | ✓ |
| AC-8.3 | T-6.5 | ✓ |
| AC-8.4 | T-6.6 | ✓ |
| AC-8.5 | T-6.7, T-6.8 | ✓ |
| AC-9.1 | T-1.4, T-8.1, T-8.2 | ✓ |
| AC-9.2 | T-8.3 | ✓ |
| AC-9.3 | T-1.6, T-8.4 | ✓ |
| AC-9.4 | T-8.5 | ✓ |
| AC-9.5 | T-8.6 | ✓ |
| AC-9.6 | T-8.7 | ✓ |
| AC-10.1 | T-1.3 | ✓ |
| AC-10.2 | T-2.9, T-2.10, T-3.8, T-7.15, T-8.8, T-8.9 | ✓ |
| AC-10.3 | T-2.11, T-3.9, T-5.11, T-7.16, T-8.8 | ✓ |
| AC-10.4 | T-1.1, T-1.2 | ✓ |
| AC-10.5 | T-1.1 | ✓ |
| AC-10.6 | T-8.1 | ✓ |

**Coverage: 58/58 ACs = 100%**
