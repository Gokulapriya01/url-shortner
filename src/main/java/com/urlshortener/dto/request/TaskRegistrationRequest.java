package com.urlshortener.dto.request;

import java.util.List;

import com.urlshortener.orchestration.OrchestrationEngine.TaskDefinition;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRegistrationRequest {

    @NotEmpty(message = "Tasks list cannot be empty")
    private List<TaskDefinition> tasks;
}
