package com.selfdevelopment.statemachine.validation;

/**
 * Represents a single validation error found during state machine configuration validation.
 */
public record ValidationError(
        Severity severity,
        String code,
        String message,
        String detail
) {
    /**
     * Validation severity levels.
     */
    public enum Severity {
        /** Will cause build failure if validation is enforced. */
        ERROR,
        /** Warning that does not block build but should be reviewed. */
        WARNING
    }

    /**
     * Creates an error-level validation error.
     */
    public static ValidationError error(String code, String message) {
        return new ValidationError(Severity.ERROR, code, message, null);
    }

    /**
     * Creates an error-level validation error with detail.
     */
    public static ValidationError error(String code, String message, String detail) {
        return new ValidationError(Severity.ERROR, code, message, detail);
    }

    /**
     * Creates a warning-level validation error.
     */
    public static ValidationError warning(String code, String message) {
        return new ValidationError(Severity.WARNING, code, message, null);
    }

    /**
     * Creates a warning-level validation error with detail.
     */
    public static ValidationError warning(String code, String message, String detail) {
        return new ValidationError(Severity.WARNING, code, message, detail);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s%s",
                severity, code, message,
                detail != null ? " (" + detail + ")" : "");
    }
}
