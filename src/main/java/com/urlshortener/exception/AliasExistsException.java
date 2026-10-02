package com.urlshortener.exception;

public class AliasExistsException extends RuntimeException {

    private final String alias;

    /** Alias exists exception. */
    public AliasExistsException(String alias) {
        super("Alias already exists: " + alias);
        this.alias = alias;
    }

    /** Returns alias. */
    public String getAlias() {
        return alias;
    }
}
