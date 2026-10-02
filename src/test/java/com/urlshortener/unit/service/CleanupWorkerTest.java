package com.urlshortener.unit.service;
import com.urlshortener.job.*;
import com.urlshortener.repository.*;
import com.urlshortener.service.CacheService;
import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.entity.Url;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class CleanupWorkerTest {
    @Test void dryRunCountsWithoutDeletionEvenWithFullBatch() {
        var urls=mock(UrlRepository.class);var events=mock(ClickEventRepository.class);
        var aggregates=mock(ClickAggregateRepository.class);var cache=mock(CacheService.class);
        when(urls.countByExpiresAtBefore(any())).thenReturn(1000L);
        var result=new CleanupWorker(urls,events,aggregates,cache,new AppProperties()).performCleanup(true);
        assertEquals(1000,result.deletedUrls());assertTrue(result.dryRun());
        verifyNoInteractions(events,aggregates,cache);verify(urls,never()).deleteAllById(any());
    }
    @Test void cleanupDeletesDependentRowsBeforeUrlsAndInvalidatesAliases() {
        var urls=mock(UrlRepository.class);var events=mock(ClickEventRepository.class);
        var aggregates=mock(ClickAggregateRepository.class);var cache=mock(CacheService.class);
        var id=UUID.randomUUID();when(urls.findExpiredUrlIds(any(),any())).thenReturn(List.of(id));
        when(urls.findById(id)).thenReturn(Optional.of(Url.builder().shortCode("code").customAlias("alias").build()));
        when(events.deleteByUrlIdIn(List.of(id))).thenReturn(5);when(aggregates.deleteByUrlIdIn(List.of(id))).thenReturn(4);
        var result=new CleanupWorker(urls,events,aggregates,cache,new AppProperties()).performCleanup(false);
        assertEquals(1,result.deletedUrls());assertEquals(5,result.deletedEvents());assertEquals(4,result.deletedAggregates());
        var order=inOrder(events,aggregates,urls);order.verify(events).deleteByUrlIdIn(List.of(id));
        order.verify(aggregates).deleteByUrlIdIn(List.of(id));order.verify(urls).deleteAllById(List.of(id));
        verify(cache).deleteUrl("code");verify(cache).deleteUrl("alias");
    }
}
