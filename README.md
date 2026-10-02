# Agentic URL Shortener System

A URL shortening service with an integrated Agentic SDLC Orchestration Engine. Built as part of the Schwab Agentic-Proficient Software Engineer assessment.

## Features

### Core URL Shortener
- **URL Shortening**: Generate short, unique codes for long URLs
- **Custom Aliases**: User-defined short codes (3-50 characters)
- **URL Expiration**: TTL support with automatic cleanup
- **Analytics**: Click tracking with configurable granularity
- **Caching**: Redis-based cache-aside pattern

### Reliability & Security
- **Rate Limiting**: Configurable per-endpoint limits (Bucket4j)
- **Circuit Breaker**: Fault tolerance for external dependencies (Resilience4j)
- **Retry with Backoff**: Exponential backoff
- **Security Headers**: Spring Security (CSP, HSTS, etc.)
- **URL Validation**: Block malicious schemes (javascript:, data:, etc.)
- **Graceful Shutdown**: Connection draining and cleanup

### Agentic Orchestration
- **Task DAG**: Directed Acyclic Graph for task dependencies (Kahn's algorithm)
- **State Machine**: 6-state task lifecycle management
- **Approval Gates**: Human-in-the-loop checkpoints
- **Rollback Support**: Revert to previous phases
- **Audit Trail**: Full history of all decisions
- **Event System**: Spring ApplicationEvent integration

## Tech Stack

| Component | Technology |
|-----------|------------|
| Runtime | Java 21 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL (Spring Data JPA) |
| Cache | Redis (Spring Data Redis) |
| Validation | Jakarta Bean Validation |
| Resilience | Resilience4j |
| Rate Limiting | Bucket4j |
| Migrations | Flyway |
| Testing | JUnit 5 + Mockito + Testcontainers |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 14+
- Redis 7+

### Installation

```bash
# Clone the repository
git clone <repository-url>
cd url-shortener

# Build the project
mvn clean package

# Run database migrations (automatic on startup)
# Configure environment variables first

# Start the application
mvn spring-boot:run
```

### Docker Compose (Recommended)

```bash
# Start PostgreSQL and Redis
docker-compose up -d postgres redis

# Run the application
mvn spring-boot:run
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PORT` | Server port | 3000 |
| `DATABASE_URL` | PostgreSQL JDBC URL | jdbc:postgresql://localhost:5432/urlshortener |
| `DATABASE_USERNAME` | PostgreSQL username | postgres |
| `DATABASE_PASSWORD` | PostgreSQL password | postgres |
| `REDIS_HOST` | Redis host | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `BASE_URL` | Public base URL for short links | http://localhost:3000 |
| `SHORT_CODE_LENGTH` | Length of generated codes | 7 |
| `CACHE_TTL_SECONDS` | Cache TTL in seconds | 3600 |
| `RATE_LIMIT_MAX_SHORTEN` | Rate limit for /api/shorten | 100 |
| `RATE_LIMIT_MAX_REDIRECT` | Rate limit for redirects | 1000 |

## API Reference

### URL Shortening

#### Create Short URL
```http
POST /api/shorten
Content-Type: application/json

{
  "url": "https://example.com/very/long/path",
  "customAlias": "my-link",     // Optional
  "expiresIn": 86400            // Optional: seconds
}
```

**Response:**
```json
{
  "shortCode": "my-link",
  "shortUrl": "http://localhost:3000/my-link",
  "originalUrl": "https://example.com/very/long/path",
  "expiresAt": "2026-10-02T12:00:00.000Z",
  "createdAt": "2026-10-01T12:00:00.000Z"
}
```

#### Redirect
```http
GET /:code
```
Returns 301 (permanent) or 302 (expiring URLs).

### Analytics

#### Get Analytics
```http
GET /api/analytics/:code
```

### Health Checks

| Endpoint | Description |
|----------|-------------|
| `GET /health` | Overall health status |
| `GET /ready` | Readiness probe (DB + Redis) |
| `GET /live` | Liveness probe |

### Orchestration

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orchestration/tasks` | POST | Register task definitions |
| `/api/orchestration/gates` | POST | Register gate definitions |
| `/api/orchestration/sessions` | POST | Create orchestration session |
| `/api/orchestration/sessions/{id}` | GET | Get session details |
| `/api/orchestration/sessions/{id}/execute` | POST | Execute next tasks |
| `/api/orchestration/sessions/{id}/pause` | POST | Pause session |
| `/api/orchestration/sessions/{id}/resume` | POST | Resume session |
| `/api/orchestration/sessions/{id}/cancel` | POST | Cancel session |
| `/api/orchestration/sessions/{id}/rollback` | POST | Rollback to phase |
| `/api/orchestration/sessions/{sid}/gates/{gid}/approve` | POST | Approve gate |
| `/api/orchestration/sessions/{sid}/gates/{gid}/reject` | POST | Reject gate |
| `/api/orchestration/sessions/{id}/metrics` | GET | Get session metrics |

## Project Structure

```
url-shortener/
├── pom.xml
├── src/main/java/com/urlshortener/
│   ├── UrlShortenerApplication.java
│   ├── config/
│   │   ├── AppProperties.java
│   │   ├── RedisConfig.java
│   │   ├── SecurityConfig.java
│   │   └── AsyncConfig.java
│   ├── domain/
│   │   ├── entity/
│   │   │   ├── Url.java
│   │   │   ├── ClickEvent.java
│   │   │   ├── ClickAggregate.java
│   │   │   ├── Task.java
│   │   │   ├── TaskTransition.java
│   │   │   └── Gate.java
│   │   └── enums/
│   │       ├── PeriodType.java
│   │       └── TaskStatus.java
│   ├── repository/
│   │   ├── UrlRepository.java
│   │   ├── ClickEventRepository.java
│   │   ├── ClickAggregateRepository.java
│   │   ├── TaskRepository.java
│   │   └── GateRepository.java
│   ├── service/
│   │   ├── UrlService.java
│   │   ├── CacheService.java
│   │   ├── AnalyticsService.java
│   │   ├── ClickTrackingService.java
│   │   └── RateLimitService.java
│   ├── orchestration/
│   │   ├── OrchestrationEngine.java
│   │   ├── TaskStateMachine.java
│   │   ├── DAGBuilder.java
│   │   └── DependencyResolver.java
│   ├── controller/
│   │   ├── UrlController.java
│   │   ├── RedirectController.java
│   │   ├── AnalyticsController.java
│   │   ├── HealthController.java
│   │   ├── AdminController.java
│   │   └── OrchestrationController.java
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── util/
│   │   ├── ShortCodeGenerator.java
│   │   ├── UrlValidator.java
│   │   └── IpHasher.java
│   ├── exception/
│   │   └── GlobalExceptionHandler.java
│   └── job/
│       └── CleanupScheduledTask.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__initial_schema.sql
└── src/test/java/com/urlshortener/
    ├── unit/
    │   ├── service/
    │   ├── util/
    │   └── orchestration/
    └── integration/
```

## Testing

```bash
# Run all tests
mvn test

# Run all tests and enforce >80% core-service line coverage
mvn clean verify
python3 scripts/verify_artifacts.py

# Run specific test class
mvn test -Dtest=UrlServiceTest
```

## Development

```bash
# Start in development mode
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Build for production
mvn clean package -DskipTests

# Run the JAR
java -jar target/url-shortener-1.0.0.jar
```

## Deployment

### Docker

```bash
# Build image
docker build -t url-shortener .

# Run container
docker run -p 3000:3000 \
  -e DATABASE_URL=jdbc:postgresql://host:5432/urlshortener \
  -e REDIS_HOST=redis-host \
  url-shortener
```

### Kubernetes

See [Deployment Guide](docs/DEPLOYMENT.md) for Kubernetes manifests.

## Architecture

The system follows a layered architecture:

```
┌─────────────────────────────────────────────┐
│               Controllers                    │
│  (URL, Analytics, Health, Orchestration)    │
├─────────────────────────────────────────────┤
│                Services                      │
│  (URL, Cache, Analytics, RateLimit)         │
├─────────────────────────────────────────────┤
│              Repositories                    │
│  (Spring Data JPA)                          │
├─────────────────────────────────────────────┤
│              Data Stores                     │
│  (PostgreSQL, Redis)                        │
└─────────────────────────────────────────────┘
```

## Reliability Patterns

### Circuit Breaker (Resilience4j)
```yaml
resilience4j:
  circuitbreaker:
    instances:
      database:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
```

### Rate Limiting (Bucket4j)
- `/api/shorten`: 100 requests/minute per IP
- `/{code}` redirect: 1000 requests/minute per IP

### Retry with Exponential Backoff
```yaml
resilience4j:
  retry:
    instances:
      database:
        max-attempts: 3
        wait-duration: 100ms
        exponential-backoff-multiplier: 2
```

## License

MIT

## Author

Built for Schwab Agentic-Proficient Software Engineer Assessment

## Acceptance evidence and limitations

See [Testing](docs/TESTING.md), [Architecture](docs/ARCHITECTURE.md) and [Final acceptance](docs/FINAL_ACCEPTANCE.md).
Logs are JSON. Maven does not automatically load `.env`; export it before local startup.
Admin and orchestration access is unauthenticated and must be protected by a trusted ingress.
Orchestration/rate limits are process-local; queued analytics is not durable across abrupt termination.
No deployment has been performed. Use the Java [deployment guide](docs/DEPLOYMENT.md).
