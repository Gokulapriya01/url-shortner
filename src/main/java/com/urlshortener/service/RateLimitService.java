package com.urlshortener.service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.urlshortener.config.AppProperties;
import com.urlshortener.exception.RateLimitExceededException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RateLimitService {

    private final AppProperties appProperties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> shortenWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> redirectWindows = new ConcurrentHashMap<>();

    /** Creates a rate limiter using the configured limits and the UTC system clock. */
    @Autowired
    public RateLimitService(AppProperties appProperties) {
        this(appProperties, Clock.systemUTC());
    }

    /** Creates a rate limiter using the supplied clock for rolling-window expiration. */
    public RateLimitService(AppProperties appProperties, Clock clock) {
        this.appProperties = appProperties;
        this.clock = clock;
    }

    /** Records a shortening request or throws when the client's rolling window is full. */
    public void checkShortenLimit(String clientIp) {
        checkLimit(shortenWindows, clientIp, appProperties.getRateLimit().getMaxShorten(), "Shorten");
    }

    /** Records a redirect request or throws when the client's rolling window is full. */
    public void checkRedirectLimit(String clientIp) {
        checkLimit(redirectWindows, clientIp, appProperties.getRateLimit().getMaxRedirect(), "Redirect");
    }

    /** Returns the client's remaining shortening capacity in the rolling window. */
    public long getRemainingShortenTokens(String clientIp) {
        return remaining(shortenWindows, clientIp, appProperties.getRateLimit().getMaxShorten());
    }

    /** Returns the client's remaining redirect capacity in the rolling window. */
    public long getRemainingRedirectTokens(String clientIp) {
        return remaining(redirectWindows, clientIp, appProperties.getRateLimit().getMaxRedirect());
    }

    private void checkLimit(ConcurrentHashMap<String, Window> windows, String ip, long limit, String operation) {
        Window window = windows.computeIfAbsent(ip, key -> new Window());
        synchronized (window) {
            long now = clock.millis();
            expire(window, now);
            if (window.requests.size() >= limit) {
                log.warn("{} rate limit exceeded for IP: {}", operation, maskIp(ip));
                throw new RateLimitExceededException(operation + " rate limit exceeded");
            }
            window.requests.addLast(now);
        }
    }

    private long remaining(ConcurrentHashMap<String, Window> windows, String ip, long limit) {
        Window window = windows.computeIfAbsent(ip, key -> new Window());
        synchronized (window) {
            expire(window, clock.millis());
            return Math.max(0, limit - window.requests.size());
        }
    }

    private void expire(Window window, long now) {
        long cutoff = now - appProperties.getRateLimit().getWindowMs();
        while (!window.requests.isEmpty() && window.requests.peekFirst() <= cutoff) {
            window.requests.removeFirst();
        }
    }

    private static class Window {
        private final ArrayDeque<Long> requests = new ArrayDeque<>();
    }

    private String maskIp(String ip) {
        if (ip == null) return "unknown";
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot) + ".xxx";
        }
        return ip;
    }
}
