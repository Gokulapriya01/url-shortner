package com.urlshortener.unit.service;

import com.urlshortener.config.AppProperties;
import com.urlshortener.service.RateLimitService;
import com.urlshortener.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitServiceTest {
    @Test
    void limitsBothOperationsAndIsolatesClients() {
        var service = new RateLimitService(new AppProperties());
        for (int i = 0; i < 100; i++) service.checkShortenLimit("127.0.0.1");
        assertEquals(0, service.getRemainingShortenTokens("127.0.0.1"));
        assertThrows(RateLimitExceededException.class, () -> service.checkShortenLimit("127.0.0.1"));
        for (int i = 0; i < 1000; i++) service.checkRedirectLimit("client");
        assertEquals(0, service.getRemainingRedirectTokens("client"));
        assertThrows(RateLimitExceededException.class, () -> service.checkRedirectLimit("client"));
        service.checkShortenLimit("another");
        service.checkRedirectLimit("another");
    }

    @Test
    void elapsedTimeWithinMinuteDoesNotReplenishEitherLimit() {
        Clock clock = mock(Clock.class);
        AtomicLong now = new AtomicLong(100_000);
        when(clock.millis()).thenAnswer(invocation -> now.get());
        var service = new RateLimitService(new AppProperties(), clock);
        for (int i = 0; i < 100; i++) service.checkShortenLimit("client");
        for (int i = 0; i < 1000; i++) service.checkRedirectLimit("client");
        now.addAndGet(59_999);
        assertEquals(0, service.getRemainingShortenTokens("client"));
        assertEquals(0, service.getRemainingRedirectTokens("client"));
        assertThrows(RateLimitExceededException.class, () -> service.checkShortenLimit("client"));
        assertThrows(RateLimitExceededException.class, () -> service.checkRedirectLimit("client"));
        now.incrementAndGet();
        assertEquals(100, service.getRemainingShortenTokens("client"));
        assertEquals(1000, service.getRemainingRedirectTokens("client"));
        service.checkShortenLimit("client");
        service.checkRedirectLimit("client");
    }

    @Test
    void rollingWindowExpiresOnlyRequestsThatAreAtLeastOneMinuteOld() {
        Clock clock = mock(Clock.class);
        AtomicLong now = new AtomicLong(100_000);
        when(clock.millis()).thenAnswer(invocation -> now.get());
        var properties = new AppProperties();
        properties.getRateLimit().setMaxShorten(2);
        var service = new RateLimitService(properties, clock);
        service.checkShortenLimit("client");
        now.addAndGet(30_000);
        service.checkShortenLimit("client");
        now.addAndGet(30_000);
        assertEquals(1, service.getRemainingShortenTokens("client"));
        service.checkShortenLimit("client");
        assertThrows(RateLimitExceededException.class, () -> service.checkShortenLimit("client"));
        now.addAndGet(30_000);
        assertEquals(1, service.getRemainingShortenTokens("client"));
    }

    @Test
    void concurrentRequestsCannotExceedEitherLimit() {
        var service = new RateLimitService(new AppProperties(), Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
        long shortenAllowed = IntStream.range(0, 150).parallel().filter(i -> {
            try { service.checkShortenLimit("client"); return true; }
            catch (RateLimitExceededException ex) { return false; }
        }).count();
        long redirectAllowed = IntStream.range(0, 1100).parallel().filter(i -> {
            try { service.checkRedirectLimit("client"); return true; }
            catch (RateLimitExceededException ex) { return false; }
        }).count();
        assertEquals(100, shortenAllowed);
        assertEquals(1000, redirectAllowed);
    }
}
