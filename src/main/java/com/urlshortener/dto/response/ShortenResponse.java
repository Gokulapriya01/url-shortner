package com.urlshortener.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenResponse {

    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private Instant expiresAt;
    private Instant createdAt;
}
