package com.urlshortener.domain.enums;

public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    BLOCKED,
    SKIPPED;

    /** Is terminal. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED;
    }

    /** Checks executable. */
    public boolean isExecutable() {
        return this == PENDING;
    }

    /** Is active. */
    public boolean isActive() {
        return this == IN_PROGRESS;
    }
}
