# Brownfield impact analysis: aliases, expiration and acceptance fixes

Existing clients continue to POST `/api/shorten` and GET `/{code}`.
`customAlias` and `expiresIn` are optional. Permanent URLs redirect with 301; expiring URLs with 302; expired URLs return 410.

| Files | API/data-flow impact |
|---|---|
| `dto/request/ShortenRequest.java`, `util/UrlValidator.java` | Validate URLs and aliases before persistence; reject unsupported schemes and aliases outside 3–50 characters. |
| `service/UrlServiceImpl.java`, `domain/entity/Url.java` | Check alias uniqueness and expiration; return resolution metadata including expiry. |
| `resources/db/migration/V2__widen_short_code_for_custom_aliases.sql` | Widens short_code to 50 characters without deleting existing records. |
| `service/CacheServiceImpl.java` | Cache payload includes expiration; TTL is capped to remaining lifetime; legacy entries become misses. |
| `controller/RedirectController.java` | Uses resolution metadata for status; cached redirects no longer perform a second database lookup. |
| `service/ClickBatchWriter.java`, repositories | Transactionally insert events and update totals plus all four UTC aggregate buckets. |
| `service/AnalyticsService.java` | Filters raw events before grouping to respect exact date boundaries. |
| `job/CleanupWorker.java`, `job/CleanupScheduledTask.java` | Separate transactional cleanup bean avoids self-invocation; dry-run counts without deletion or looping. |
| `orchestration/OrchestrationEngine.java` | Concurrent independent handlers, bounded retries, exhaustion policy, context propagation and explicit downstream replanning. |

Request flow: controller validation → URL service → Redis lookup → database on cache miss → expiry validation → redirect and queued analytics.
Regression evidence: Maven tests, `logs/endpoint-test-results.json`, and acceptance runtime report.
Risks: changing task scheduling exposes handler concurrency; context values must be thread-safe.
Aliases remain case-sensitive. Expiration uses UTC Instant and timestamp-with-time-zone persistence.
Cleanup is destructive and must be tested only on isolated data. Orchestration state and rate limits are in memory.
