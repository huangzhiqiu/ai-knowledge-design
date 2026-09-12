package com.selfdevelopment.chatengine.config.validation;

/**
 * Represents a single validation issue found during config validation.
 */
public record ValidationIssue(
        Severity severity,
        String field,
        String message,
        String code,
        Object actualValue,
        String suggestion
) {
    public enum Severity {
        ERROR,      // Must be fixed, config invalid
        WARNING,    // Should be fixed, config still valid
        INFO        // Informational, no action required
    }

    public static ValidationIssue error(String field, String message, String code) {
        return new ValidationIssue(Severity.ERROR, field, message, code, null, null);
    }

    public static ValidationIssue error(String field, String message, String code, Object actualValue, String suggestion) {
        return new ValidationIssue(Severity.ERROR, field, message, code, actualValue, suggestion);
    }

    public static ValidationIssue warning(String field, String message, String code) {
        return new ValidationIssue(Severity.WARNING, field, message, code, null, null);
    }

    public static ValidationIssue warning(String field, String message, String code, Object actualValue, String suggestion) {
        return new ValidationIssue(Severity.WARNING, field, message, code, actualValue, suggestion);
    }

    public static ValidationIssue info(String field, String message, String code) {
        return new ValidationIssue(Severity.INFO, field, message, code, null, null);
    }
}
