package com.urlshortener.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.domain.entity.Url;
import com.urlshortener.service.RateLimitService;
import com.urlshortener.service.UrlService;
import com.urlshortener.service.UrlService.ResolveResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class RedirectController {

    private final UrlService urlService;
    private final RateLimitService rateLimitService;

    /** Checks rate limits, resolves the link and queues tracking before returning its redirect. */
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String code,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        rateLimitService.checkRedirectLimit(clientIp);

        log.debug("Resolving short code: code={}", code);

        ResolveResult result = urlService.resolveShortCode(code);

        // Determine redirect status
        // 301 (permanent) for non-expiring URLs
        // 302 (temporary) for expiring URLs
        HttpStatus redirectStatus = result.expiresAt() == null ? HttpStatus.MOVED_PERMANENTLY : HttpStatus.FOUND;

        return ResponseEntity.status(redirectStatus)
            .location(URI.create(result.originalUrl()))
            .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
