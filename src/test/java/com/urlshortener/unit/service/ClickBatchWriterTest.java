package com.urlshortener.unit.service;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.urlshortener.service.*;
import com.urlshortener.repository.*;
import com.urlshortener.util.IpHasher;

class ClickBatchWriterTest {
    @Test
    void writesEventsCounterAndAllFourUtcBuckets() {
        var events = mock(ClickEventRepository.class);
        var urls = mock(UrlRepository.class);
        var aggregates = mock(ClickAggregateRepository.class);
        var id = UUID.randomUUID();
        var event = new ClickTrackingService.ClickTrackingEvent(id, "ref", "agent", "hash",
            Instant.parse("2026-10-02T02:29:03Z"));
        new ClickBatchWriter(events, urls, aggregates).writeBatch(List.of(event, event));
        verify(events).saveAllAndFlush(argThat(list -> ((List<?>) list).size() == 2));
        verify(urls).addClickCount(id, 2);
        verify(aggregates).addClicks(id, "HOUR", Instant.parse("2026-10-02T02:00:00Z"), 2);
        verify(aggregates).addClicks(id, "DAY", Instant.parse("2026-10-02T00:00:00Z"), 2);
        verify(aggregates).addClicks(id, "WEEK", Instant.parse("2026-09-28T00:00:00Z"), 2);
        verify(aggregates).addClicks(id, "MONTH", Instant.parse("2026-10-01T00:00:00Z"), 2);
    }

    @Test
    void shutdownDrainsMoreThanOneBatchAndHashesMetadata() {
        var writer = mock(ClickBatchWriter.class);
        var tracker = new ClickTrackingService(writer, new IpHasher());
        for (int i=0;i<25;i++) tracker.trackClick(UUID.randomUUID(), "https://ref.example", "agent", "127.0.0.1");
        tracker.init(); tracker.shutdown();
        org.junit.jupiter.api.Assertions.assertEquals(0, tracker.getQueueSize());
        var batches = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(writer, times(3)).writeBatch(batches.capture());
        var event = (ClickTrackingService.ClickTrackingEvent) batches.getAllValues().get(0).get(0);
        org.junit.jupiter.api.Assertions.assertEquals(64, event.ipHash().length());
        org.junit.jupiter.api.Assertions.assertNotEquals("127.0.0.1", event.ipHash());
        org.junit.jupiter.api.Assertions.assertEquals("https://ref.example", event.referrer());
        org.junit.jupiter.api.Assertions.assertEquals("agent", event.userAgent());
        org.junit.jupiter.api.Assertions.assertNotNull(event.timestamp());
    }

    @Test
    void failedBatchIsRequeuedAndSuccessfulBatchIsRemoved() {
        var writer = mock(ClickBatchWriter.class);
        var tracker = new ClickTrackingService(writer, new IpHasher());
        tracker.trackClick(UUID.randomUUID(), "ref", "agent", "127.0.0.1");
        doThrow(new RuntimeException("rolled back")).doNothing().when(writer).writeBatch(anyList());
        tracker.flushQueue();
        org.junit.jupiter.api.Assertions.assertEquals(1, tracker.getQueueSize());
        tracker.flushQueue();
        org.junit.jupiter.api.Assertions.assertEquals(0, tracker.getQueueSize());
    }
}
