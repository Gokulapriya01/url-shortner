package com.urlshortener.integration;
import com.urlshortener.config.*;
import com.urlshortener.controller.*;
import com.urlshortener.domain.entity.Url;
import com.urlshortener.dto.response.*;
import com.urlshortener.exception.GlobalExceptionHandler;
import com.urlshortener.job.CleanupScheduledTask;
import com.urlshortener.service.*;
import com.urlshortener.domain.enums.PeriodType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(classes=RemainingEndpointsTest.Application.class)
@AutoConfigureMockMvc
class RemainingEndpointsTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,RedisAutoConfiguration.class,RedisRepositoriesAutoConfiguration.class})
    @EnableConfigurationProperties(AppProperties.class)
    @Import({SecurityConfig.class,AdminController.class,HealthController.class,AnalyticsController.class,
        RedirectController.class,GlobalExceptionHandler.class,RateLimitService.class})
    static class Application {}
    @Autowired MockMvc mvc;
    @MockBean DataSource dataSource;
    @MockBean RedisTemplate<String,Object> redis;
    @MockBean AnalyticsService analytics;
    @MockBean UrlService urls;
    @MockBean CleanupScheduledTask cleanup;
    @MockBean ClickTrackingService tracking;
    @Test void healthAndReadinessIncludeComponentsAndSecurityHeaders() throws Exception {
        var connection=mock(Connection.class);when(connection.isValid(5)).thenReturn(true);when(dataSource.getConnection()).thenReturn(connection);
        var factory=mock(RedisConnectionFactory.class);var redisConnection=mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);when(factory.getConnection()).thenReturn(redisConnection);when(redisConnection.ping()).thenReturn("PONG");
        for(String path:List.of("/health","/ready","/live"))mvc.perform(get(path)).andExpect(status().isOk())
            .andExpect(header().exists("Content-Security-Policy")).andExpect(header().string("X-Frame-Options","DENY"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"));
        when(connection.isValid(5)).thenReturn(false);
        mvc.perform(get("/ready")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.ready").value(false));
    }
    @Test void adminDryRunAndAnalyticsReturnSubstantiveResults() throws Exception {
        when(cleanup.performCleanup(true)).thenReturn(new CleanupScheduledTask.CleanupResult(3,0,0,true));
        mvc.perform(post("/admin/cleanup/run").contentType(MediaType.APPLICATION_JSON).content("{\"dryRun\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.deletedUrls").value(3)).andExpect(jsonPath("$.dryRun").value(true));
        mvc.perform(get("/admin/cleanup/status")).andExpect(status().isOk());
        mvc.perform(get("/admin/stats")).andExpect(status().isOk()).andExpect(jsonPath("$.clickTrackingQueueSize").value(0));
        when(analytics.getAnalytics(eq("code123"),eq(PeriodType.DAY),any(),any()))
            .thenReturn(AnalyticsResponse.builder().shortCode("code123").totalClicks(5).timeSeries(List.of()).build());
        mvc.perform(get("/api/analytics/code123?granularity=day")).andExpect(status().isOk()).andExpect(jsonPath("$.totalClicks").value(5));
        mvc.perform(get("/api/analytics/code123?granularity=bad")).andExpect(status().isBadRequest());
    }
    @Test void cachedRedirectNeverQueriesDatabaseAndCarriesHeaders() throws Exception {
        when(urls.resolveShortCode("cache123")).thenReturn(new UrlService.ResolveResult("https://example.com",UUID.randomUUID(),false));
        mvc.perform(get("/cache123")).andExpect(status().isMovedPermanently()).andExpect(header().string("Location","https://example.com"))
            .andExpect(header().exists("Content-Security-Policy"));
        verify(urls,never()).getUrlByShortCode(anyString());
    }
}
