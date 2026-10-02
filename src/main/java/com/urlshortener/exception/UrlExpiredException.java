package com.urlshortener.exception;

public class UrlExpiredException extends RuntimeException {

    private final String shortCode;

    /** Url expired exception. */
    public UrlExpiredException(String shortCode) {
        super("URL has expired: " + shortCode);
        this.shortCode = shortCode;
    }

    /** Returns short code. */
    public String getShortCode() {
        return shortCode;
    }
}
