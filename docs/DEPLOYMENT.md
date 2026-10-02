# Java application deployment guide

This guide describes deployment preparation. No deployment has been performed.
Use Java 21+, Maven 3.9+, PostgreSQL 16 and Redis 7. The application binds port 3000 by default.

## Build and verify

```sh
mvn clean verify
java -jar target/url-shortener-1.0.0.jar
```

Configure the process environment before starting. Maven and Java do not automatically load `.env`.
For local development only:

```sh
set -a
source .env
set +a
java -jar target/url-shortener-1.0.0.jar > logs/application.log 2>&1
```

Required production settings: `DATABASE_URL=jdbc:postgresql://HOST:5432/DB`,
`DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`,
`BASE_URL=https://your-domain.example`. Set `PORT` if different from 3000.
Supply credentials through the deployment platform's secret store. Do not commit `.env`.
Use dedicated database users and authenticated Redis; development defaults are not production credentials.
Flyway applies versioned migrations on startup. Review migrations and take a database backup before an approved rollout.

## Containers

The root Dockerfile builds the Java executable and runs it as a non-root user.

```sh
docker build -t url-shortener:reviewed .
docker run --env-file /path/to/production.env -p 3000:3000 url-shortener:reviewed
```

Use service DNS names for database/Redis when running in containers; localhost refers to that container.
Terminate HTTPS at a trusted ingress. Only trust forwarded client IP headers from that ingress.
Protect `/admin/**` and `/api/orchestration/**` at the ingress: application access is currently unauthenticated.
Rate limits and orchestration sessions are process-local; use a single application replica until distributed state is designed.

## Health and shutdown

Use `/live` for liveness and `/ready` for readiness. `/health` reports component details.
Send SIGTERM and allow at least 30 seconds for request draining plus analytics flushing.
Application logs are JSON with timestamp, level, logger, message and exception fields.
Run cleanup as dry-run first: POST `/admin/cleanup/run` with `{"dryRun":true}`.
A real cleanup deletes expired links and dependent events; run it only against an approved dataset.

## Release gate

Review `docs/FINAL_ACCEPTANCE.md`, Maven coverage and local runtime evidence before approval.
Validate OpenAPI against the running version. Test database backup/restore separately.
Deploy an immutable reviewed image only after explicit deployment approval.
Rollback uses the previously approved image; do not automatically reverse data migrations.
