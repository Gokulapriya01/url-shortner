package com.urlshortener.unit.orchestration;

import com.urlshortener.config.AppProperties;
import com.urlshortener.orchestration.OrchestrationEngine;
import com.urlshortener.orchestration.OrchestrationEngine.*;
import com.urlshortener.domain.enums.TaskStatus;
import com.urlshortener.dto.response.MetricsResponse;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrchestrationEngineTest {
    OrchestrationEngine engine;
    @BeforeEach void setup() {
        var properties = new AppProperties();
        properties.getOrchestration().setRetryDelayMs(5);
        engine = new OrchestrationEngine(mock(ApplicationEventPublisher.class), properties);
    }
    @AfterEach void close() { engine.shutdown(); }
    TaskDefinition task(String id, int phase, String... deps) {
        return TaskDefinition.builder().id(id).name(id).phase(phase).handler(id)
            .dependencies(List.of(deps)).maxRetries(2).build();
    }
    TaskHandlerResult success(Map<String,Object> output) {
        return TaskHandlerResult.builder().success(true).output(output).build();
    }
    @Test void independentHandlersOverlap() {
        var latch = new CountDownLatch(2);
        for (String id : List.of("a", "b")) engine.registerHandler(id, (t,c) -> {
            latch.countDown();
            try { assertTrue(latch.await(2, TimeUnit.SECONDS), "Handlers must overlap"); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
            return success(Map.of());
        });
        engine.registerTasks(List.of(task("a",1),task("b",1)));
        var session=engine.createSession("parallel");
        assertEquals(2, engine.executeNext(session.getId()).size());
        assertEquals("completed",session.getStatus());
    }
    @Test void retryRecoversWithoutDoubleStartAndRecordsMetrics() {
        var attempts=new AtomicInteger();
        engine.registerHandler("a", (t,c) -> {
            if(attempts.incrementAndGet()<3) throw new IllegalStateException("temporary");
            return success(Map.of());
        });
        engine.registerTasks(List.of(task("a",1)));
        var s=engine.createSession("retry"); engine.executeNext(s.getId());
        assertEquals(3,attempts.get()); assertEquals(TaskStatus.COMPLETED,s.getTasks().get(0).getStatus());
        var m=MetricsResponse.from(s.getMetrics());
        assertEquals(2,m.getRetryCount()); assertEquals(1,m.getSuccessRate()); assertTrue(m.getMttrMs()>0);
        assertTrue(m.getLatencyMs()>0);
    }
    @Test void exhaustionSkipsAfterConfiguredRetries() {
        var attempts=new AtomicInteger();
        engine.registerHandler("a",(t,c)-> { attempts.incrementAndGet(); throw new IllegalStateException("permanent"); });
        engine.registerTasks(List.of(task("a",1)));
        var s=engine.createSession("skip"); engine.executeNext(s.getId());
        assertEquals(3,attempts.get()); assertEquals(TaskStatus.SKIPPED,s.getTasks().get(0).getStatus());
        assertEquals(1,s.getMetrics().getSkippedTasks());
    }
    @Test void exhaustionCanRollbackAndPause() {
        var definition=task("a",1); definition.setOnExhaustion("ROLLBACK");
        engine.registerHandler("a",(t,c)-> {throw new IllegalStateException("permanent");});
        engine.registerTasks(List.of(definition));
        var s=engine.createSession("rollback"); engine.executeNext(s.getId());
        assertEquals("paused",s.getStatus()); assertEquals(TaskStatus.PENDING,s.getTasks().get(0).getStatus());
        assertEquals(1,s.getMetrics().getRollbackCount());
    }
    @Test void dependenciesTransferContextAndReplanTransitively() {
        var value=new AtomicInteger(1);
        engine.registerHandler("a",(t,c)->success(Map.of("value",value.get())));
        engine.registerHandler("b",(t,c)->success(Map.of("seen",c.getData().get("a"))));
        engine.registerTasks(List.of(task("a",1),task("b",2,"a"),task("c",3,"b")));
        var s=engine.createSession("context");
        assertEquals("a",engine.executeNext(s.getId()).get(0).getDefinitionId());
        assertEquals("b",engine.executeNext(s.getId()).get(0).getDefinitionId());
        engine.executeNext(s.getId()); assertEquals("completed",s.getStatus());
        assertTrue(engine.updateTaskOutput(s.getId(),"a",Map.of("value",2)));
        assertFalse(s.getContext().getData().containsKey("b"));
        assertEquals(2,s.getCurrentPhase());
        engine.executeNext(s.getId()); engine.executeNext(s.getId());
        assertEquals(Map.of("seen",Map.of("value",2)),s.getContext().getData().get("b"));
    }
    @Test void pauseAtCheckpointPreservesContextAndResumes() {
        engine.registerTasks(List.of(task("a",1),task("b",2,"a")));
        var s=engine.createSession("pause");
        engine.registerHandler("a",(t,c)-> {engine.pauseSession(s.getId()); return success(Map.of("value",1));});
        engine.executeNext(s.getId()); assertEquals("paused",s.getStatus());
        assertTrue(engine.executeNext(s.getId()).isEmpty());
        assertEquals(Map.of("value",1),s.getContext().getData().get("a"));
        engine.resumeSession(s.getId()); engine.executeNext(s.getId()); engine.executeNext(s.getId());
        assertEquals("completed",s.getStatus());
    }
}
