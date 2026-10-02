package com.urlshortener.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.dto.response.HealthResponse;
import com.urlshortener.dto.response.HealthResponse.ComponentHealth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String VERSION = "1.0.0";

    /** Reports availability and latency for database and Redis components. */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        ComponentHealth dbHealth = checkDatabase();
        ComponentHealth redisHealth = checkRedis();

        String status;
        if (dbHealth.isUp() && redisHealth.isUp()) {
            status = "healthy";
        } else if (!dbHealth.isUp() && !redisHealth.isUp()) {
            status = "unhealthy";
        } else {
            status = "degraded";
        }

        HealthResponse response = HealthResponse.builder()
            .status(status)
            .timestamp(Instant.now())
            .components(Map.of(
                "database", dbHealth,
                "cache", redisHealth
            ))
            .version(VERSION)
            .build();

        HttpStatus httpStatus = "unhealthy".equals(status)
            ? HttpStatus.SERVICE_UNAVAILABLE
            : HttpStatus.OK;

        return ResponseEntity.status(httpStatus).body(response);
    }

    /** Reports readiness only when both database and Redis are available. */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean dbReady = checkDatabase().isUp();
        boolean redisReady = checkRedis().isUp();
        boolean ready = dbReady && redisReady;

        HttpStatus status = ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
            .body(Map.of(
                "ready", ready,
                "timestamp", Instant.now()
            ));
    }

    /** Reports that the application process is responsive. */
    @GetMapping("/live")
    public Map<String, Object> live() {
        return Map.of(
            "alive", true,
            "timestamp", Instant.now()
        );
    }

    private ComponentHealth checkDatabase() {
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(5);
            long latency = System.currentTimeMillis() - start;
            return ComponentHealth.builder()
                .up(valid)
                .latencyMs(latency)
                .build();
        } catch (SQLException e) {
            log.warn("Database health check failed", e);
            return ComponentHealth.builder()
                .up(false)
                .error(e.getMessage())
                .build();
        }
    }

    private ComponentHealth checkRedis() {
        long start = System.currentTimeMillis();
        try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
            String result = connection.ping();
            long latency = System.currentTimeMillis() - start;
            return ComponentHealth.builder()
                .up("PONG".equals(result))
                .latencyMs(latency)
                .build();
        } catch (Exception e) {
            log.warn("Redis health check failed", e);
            return ComponentHealth.builder()
                .up(false)
                .error(e.getMessage())
                .build();
        }
    }
}
