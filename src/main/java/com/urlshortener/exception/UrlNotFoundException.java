package com.urlshortener.exception;

public class UrlNotFoundException extends RuntimeException {

    private final String shortCode;

    /** Url not found exception. */
    public UrlNotFoundException(String shortCode) {
        super("URL not found: " + shortCode);
        this.shortCode = shortCode;
    }

    /** Returns short code. */
    public String getShortCode() {
        return shortCode;
    }
}
