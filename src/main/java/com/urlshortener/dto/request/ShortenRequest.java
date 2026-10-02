package com.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenRequest {

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    private String url;

    @Size(min = 3, max = 50, message = "Custom alias must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Custom alias can only contain letters, numbers, hyphens, and underscores")
    private String customAlias;

}
