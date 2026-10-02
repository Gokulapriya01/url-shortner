package com.urlshortener.unit.service;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.repository.*;
import com.urlshortener.domain.entity.*;
import com.urlshortener.domain.enums.PeriodType;
import com.urlshortener.exception.UrlNotFoundException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AnalyticsServiceTest {
    @Test void filtersRawEventsBeforeBucketingAndReturnsMetadata() {
        var urls=mock(UrlRepository.class); var events=mock(ClickEventRepository.class);
        var aggregates=mock(ClickAggregateRepository.class);
        var service=new AnalyticsService(urls,events,aggregates);
        var id=UUID.randomUUID(); var url=Url.builder().id(id).shortCode("abc1234").originalUrl("https://example.com").clickCount(9).build();
        var from=Instant.parse("2026-10-02T02:30:00Z");var to=from.plusSeconds(60);
        when(urls.findByShortCodeOrCustomAlias("abc1234")).thenReturn(Optional.of(url));
        when(events.findByUrlIdAndTimestampBetween(id,from,to)).thenReturn(List.of(
            ClickEvent.builder().timestamp(from).referrer("https://ref.example").userAgent("agent").build(),
            ClickEvent.builder().timestamp(from.plusSeconds(1)).userAgent("x".repeat(120)).build()));
        for(var period:PeriodType.values()) {
            var result=service.getAnalytics("abc1234",period,from,to);
            assertEquals(9,result.getTotalClicks());assertEquals(2,result.getTimeSeries().get(0).getClicks());
            assertEquals("https://ref.example",result.getTopReferrers().getItems().get(0).getReferrer());
            assertEquals(2,result.getTopUserAgents().getItems().size());
        }
        assertThrows(IllegalArgumentException.class,()->service.getAnalytics("abc1234",PeriodType.HOUR,to,from));
        when(urls.findByShortCodeOrCustomAlias("missing")).thenReturn(Optional.empty());
        assertThrows(UrlNotFoundException.class,()->service.getAnalytics("missing"));
        service.getAnalyticsByPeriod("abc1234",PeriodType.DAY,from,to);
        verifyNoInteractions(aggregates);
    }
}
