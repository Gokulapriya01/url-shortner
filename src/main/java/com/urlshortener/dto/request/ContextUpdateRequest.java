package com.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextUpdateRequest {

    @NotBlank(message = "Key is required")
    private String key;

    private Object value;
}
