package com.urlshortener.exception;

public class RateLimitExceededException extends RuntimeException {

    /** Rate limit exceeded exception. */
    public RateLimitExceededException(String message) {
        super(message);
    }
}
