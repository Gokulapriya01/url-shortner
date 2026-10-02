package com.urlshortener.integration;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import static org.junit.jupiter.api.Assertions.*;
class GracefulShutdownTest {
    static final CountDownLatch entered=new CountDownLatch(1);
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,RedisAutoConfiguration.class,RedisRepositoriesAutoConfiguration.class})
    static class Application {
        @Bean SlowController slowController(){return new SlowController();}
        @Bean SecurityFilterChain security(HttpSecurity http)throws Exception {
            return http.authorizeHttpRequests(a->a.anyRequest().permitAll()).build();
        }
    }
    @RestController static class SlowController {
        @GetMapping("/slow") String slow() throws InterruptedException {
            entered.countDown(); Thread.sleep(700);return "finished";
        }
    }
    @Test void shutdownWaitsForInFlightHttpRequest() throws Exception {
        try(var context=new SpringApplicationBuilder(Application.class).properties(
                "server.port=0","server.shutdown=graceful","spring.lifecycle.timeout-per-shutdown-phase=5s").run("--server.port=0")) {
            int port=((ServletWebServerApplicationContext)context).getWebServer().getPort();
            var response=HttpClient.newHttpClient().sendAsync(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/slow")).build(),HttpResponse.BodyHandlers.ofString());
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            context.close();
            assertEquals(200,response.get(5,TimeUnit.SECONDS).statusCode());
            assertEquals("finished",response.get().body());
        }
    }
}
