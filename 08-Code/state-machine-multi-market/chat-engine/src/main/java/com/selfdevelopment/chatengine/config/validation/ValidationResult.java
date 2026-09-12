package com.selfdevelopment.chatengine.config.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of config validation.
 * Contains list of issues and convenience methods for checking validity.
 */
public record ValidationResult(
        boolean valid,
        List<ValidationIssue> issues
) {
    public ValidationResult {
        if (issues == null) {
            issues = List.of();
        }
    }

    /**
     * Check if there are any ERROR severity issues.
     */
    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
    }

    /**
     * Check if there are any WARNING severity issues.
     */
    public boolean hasWarnings() {
        return issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.WARNING);
    }

    /**
     * Get all ERROR issues.
     */
    public List<ValidationIssue> getErrors() {
        return issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .collect(Collectors.toList());
    }

    /**
     * Get all WARNING issues.
     */
    public List<ValidationIssue> getWarnings() {
        return issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.WARNING)
                .collect(Collectors.toList());
    }

    /**
     * Create a valid result with no issues.
     */
    public static ValidationResult ofValid() {
        return new ValidationResult(true, List.of());
    }

    /**
     * Create a result with issues.
     */
    public static ValidationResult of(List<ValidationIssue> issues) {
        boolean hasErrors = issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(!hasErrors, new ArrayList<>(issues));
    }

    /**
     * Merge another validation result into this one.
     */
    public ValidationResult merge(ValidationResult other) {
        List<ValidationIssue> merged = new ArrayList<>(this.issues);
        merged.addAll(other.issues());
        return of(merged);
    }

    /**
     * Get a summary string of the validation result.
     */
    public String summary() {
        long errors = getErrors().size();
        long warnings = getWarnings().size();
        return String.format("Validation %s: %d errors, %d warnings, %d total issues",
                valid ? "PASSED" : "FAILED", errors, warnings, issues.size());
    }
}
