# Acceptance verification

```sh
mvn clean verify
python3 scripts/verify_artifacts.py
```

Maven runs unit tests and MockMvc integration tests with JaCoCo. The verify phase enforces >80% line
coverage for the whole `com.urlshortener.service` package, including cache/tracking/rate-limit/analytics services.
Generated accessors are not the target of acceptance tests. Coverage is a measured gate, not a test-count claim.
Report: `target/site/jacoco/index.html`; machine-readable report: `target/site/jacoco/jacoco.xml`.
JUnit evidence: `target/surefire-reports`; retained run log: `logs/acceptance-tests.log`.

Unit tests use deterministic handlers and repositories to prove DAG topology, parallel overlap,
retry/exhaustion, context, replanning, pause checkpoints, UTC bucketing, exact date filtering and queue shutdown.
MockMvc exercises every authored controller operation, security headers and error handling.
The graceful shutdown integration test starts a temporary server and closes its context during an active HTTP request.

`python3 scripts/endpoint_regression.py` runs local endpoint checks and creates only uniquely named links/workflows.
It performs admin cleanup only as dry-run. Do not use it against production.

`VERIFY_BASE_URL=http://localhost:3001 python3 scripts/verify_runtime.py` uses an isolated PostgreSQL schema,
unique link IDs, Docker database reads and Redis inspection. It proves exactly five click events and metadata,
then runs 500 cached redirects scheduled at 100 RPS with a p95 gate of <50ms.
This is a short local benchmark, not proof of sustained production capacity, browser behavior or multi-region performance.
Set VERIFY_SCHEMA to the matching isolated schema. Save results under logs/acceptance-runtime.json.

The artifact verifier compares all authored controller routes with OpenAPI and scans current src/docs/scripts
for selected credential patterns. This repository has no Git history available; no history scan is claimed.
No test stops shared PostgreSQL/Redis or deletes existing application data.
