package com.urlshortener.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.config.AppProperties;
import com.urlshortener.controller.OrchestrationController;
import com.urlshortener.exception.GlobalExceptionHandler;
import com.urlshortener.orchestration.OrchestrationEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = OrchestrationEndpointsTest.TestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class OrchestrationEndpointsTest {
    @SpringBootConfiguration
    @EnableAsync
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
        RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
    @EnableConfigurationProperties(AppProperties.class)
    @Import({OrchestrationEngine.class, OrchestrationController.class, GlobalExceptionHandler.class})
    static class TestApplication {}

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void registersDefinitionsAndExecutesOnlyAfterGateApproval() throws Exception {
        mvc.perform(post("/api/orchestration/tasks").contentType(MediaType.APPLICATION_JSON)
            .content("{\"tasks\":[{\"id\":\"test-task\",\"name\":\"Test task\",\"phase\":1}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.registeredCount").value(1));
        mvc.perform(post("/api/orchestration/gates").contentType(MediaType.APPLICATION_JSON)
            .content("{\"gates\":[{\"id\":\"test-gate\",\"name\":\"Test gate\",\"phase\":1,\"requiresApproval\":true}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.registeredCount").value(1));
        var result = mvc.perform(post("/api/orchestration/sessions").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Test workflow\"}"))
            .andExpect(status().isCreated()).andReturn();
        var session = mapper.readTree(result.getResponse().getContentAsString());
        String path = "/api/orchestration/sessions/" + session.get("id").asText();
        mvc.perform(post(path + "/execute")).andExpect(status().isOk())
            .andExpect(jsonPath("$.executedCount").value(0));
        mvc.perform(post(path + "/gates/" + session.get("gates").get(0).get("id").asText() + "/approve")
            .contentType(MediaType.APPLICATION_JSON).content("{\"approvedBy\":\"test\"}"))
            .andExpect(status().isOk());
        mvc.perform(post(path + "/execute")).andExpect(status().isOk())
            .andExpect(jsonPath("$.executedCount").value(1))
            .andExpect(jsonPath("$.tasks[0].status").value("COMPLETED"));
        mvc.perform(get(path)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.completedTasks").value(1));
        mvc.perform(post(path + "/execute")).andExpect(status().isOk())
            .andExpect(jsonPath("$.executedCount").value(0));
    }

    @Test
    void taskBuilderDeserializationPreservesDefaultLists() throws Exception {
        var definition = mapper.readValue("{\"id\":\"defaults\",\"name\":\"Defaults\",\"phase\":1}",
            OrchestrationEngine.TaskDefinition.class);
        assertThat(definition.getDependencies()).isEmpty();
        assertThat(definition.getAcIds()).isEmpty();
    }
}
