package com.urlshortener.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.urlshortener.dto.response.ApiErrorResponse;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Handle url not found. */
    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUrlNotFound(UrlNotFoundException ex) {
        log.debug("URL not found: {}", ex.getShortCode());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiErrorResponse("URL_NOT_FOUND", ex.getMessage()));
    }

    /** Handle url expired. */
    @ExceptionHandler(UrlExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleUrlExpired(UrlExpiredException ex) {
        log.debug("URL expired: {}", ex.getShortCode());
        return ResponseEntity.status(HttpStatus.GONE)
            .body(new ApiErrorResponse("URL_EXPIRED", ex.getMessage()));
    }

    /** Handle alias exists. */
    @ExceptionHandler(AliasExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleAliasExists(AliasExistsException ex) {
        log.debug("Alias already exists: {}", ex.getAlias());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiErrorResponse("ALIAS_EXISTS", ex.getMessage()));
    }

    /** Handle invalid url. */
    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidUrl(InvalidUrlException ex) {
        log.debug("Invalid URL: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiErrorResponse("INVALID_URL", ex.getMessage()));
    }

    /** Handle rate limit. */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ApiErrorResponse("RATE_LIMIT_EXCEEDED",
                "Too many requests. Please try again later."));
    }

    /** Handle circuit breaker open. */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiErrorResponse> handleCircuitBreakerOpen(CallNotPermittedException ex) {
        log.error("Circuit breaker open: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiErrorResponse("SERVICE_UNAVAILABLE",
                "Service temporarily unavailable. Please try again later."));
    }

    /** Handle validation. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.debug("Validation error: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiErrorResponse("VALIDATION_ERROR", message));
    }

    /** Handle illegal argument. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Invalid argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiErrorResponse("INVALID_ARGUMENT", ex.getMessage()));
    }

    /** Handle not found. */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiErrorResponse("NOT_FOUND", "Resource not found"));
    }

    /** Handle generic. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiErrorResponse("INTERNAL_ERROR", "Internal server error"));
    }
}
