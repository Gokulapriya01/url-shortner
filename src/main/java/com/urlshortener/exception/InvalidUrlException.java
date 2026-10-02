package com.urlshortener.exception;

public class InvalidUrlException extends RuntimeException {

    /** Invalid url exception. */
    public InvalidUrlException(String message) {
        super(message);
    }
}
