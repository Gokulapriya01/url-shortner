package com.urlshortener.dto.request;

import java.util.List;

import com.urlshortener.orchestration.OrchestrationEngine.GateDefinition;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GateRegistrationRequest {

    @NotEmpty(message = "Gates list cannot be empty")
    private List<GateDefinition> gates;
}
