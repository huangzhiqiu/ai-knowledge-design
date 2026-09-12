package com.selfdevelopment.chatengine.config.validation;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates market configuration for correctness and consistency.
 * <p>
 * Validation rules:
 * - Timeouts must be positive
 * - Feature toggles must be consistent (e.g., genesysEnabled requires transferEnabled)
 * - Business rules must use valid enum values
 * - Retry config must be reasonable
 * - Connector endpoints must be valid URLs (if provided)
 */
public class ConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigValidator.class);

    private static final Set<String> VALID_FALLBACK_STRATEGIES = Set.of("DROP", "REQUEUE", "FALLBACK_QUEUE");
    private static final Set<String> VALID_SURVEY_TYPES = Set.of("CSAT", "NPS", "CES");
    private static final Set<String> VALID_TRANSFER_TARGETS = Set.of("GENESYS", "INTERNAL_QUEUE", "AIBOT");

    private static final long MAX_TIMEOUT_SECONDS = 86400; // 24 hours
    private static final int MAX_RETRIES = 10;
    private static final long MAX_RETRY_DELAY_MS = 600000; // 10 minutes

    /**
     * Validate a market config.
     *
     * @param config config to validate
     * @param market market code (for logging)
     * @return validation result
     */
    public ValidationResult validate(StateMachineMarketConfig config, String market) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (config == null) {
            issues.add(ValidationIssue.error("config", "Config is null", "CONFIG_NULL"));
            return ValidationResult.of(issues);
        }

        validateTimeouts(config, issues);
        validateFeatures(config, issues);
        validateBusinessRules(config, issues);
        validateRetry(config, issues);
        validateConnectors(config, issues);

        ValidationResult result = ValidationResult.of(issues);
        if (result.hasErrors()) {
            log.warn("Config validation for market {} failed: {}", market, result.summary());
        } else {
            log.debug("Config validation for market {} passed: {}", market, result.summary());
        }

        return result;
    }

    /**
     * Validate all configs in a repository.
     *
     * @param configs map of market to config
     * @return combined validation result
     */
    public ValidationResult validateAll(java.util.Map<String, StateMachineMarketConfig> configs) {
        List<ValidationIssue> allIssues = new ArrayList<>();

        for (var entry : configs.entrySet()) {
            ValidationResult result = validate(entry.getValue(), entry.getKey());
            // Prefix issues with market name
            for (ValidationIssue issue : result.issues()) {
                allIssues.add(new ValidationIssue(
                        issue.severity(),
                        "[" + entry.getKey() + "] " + issue.field(),
                        issue.message(),
                        issue.code(),
                        issue.actualValue(),
                        issue.suggestion()
                ));
            }
        }

        return ValidationResult.of(allIssues);
    }

    private void validateTimeouts(StateMachineMarketConfig config, List<ValidationIssue> issues) {
        validatePositiveTimeout(config.customerIdleSeconds(), "customerIdleSeconds", issues);
        validatePositiveTimeout(config.transferTimeoutSeconds(), "transferTimeoutSeconds", issues);
        validatePositiveTimeout(config.endingGraceSeconds(), "endingGraceSeconds", issues);
        validatePositiveTimeout(config.surveyTimeoutSeconds(), "surveyTimeoutSeconds", issues);

        // Cross-field validation: endingGrace should be <= transferTimeout
        if (config.endingGraceSeconds() > config.transferTimeoutSeconds()) {
            issues.add(ValidationIssue.warning(
                    "endingGraceSeconds",
                    "endingGraceSeconds (" + config.endingGraceSeconds() +
                            ") > transferTimeoutSeconds (" + config.transferTimeoutSeconds() + ")",
                    "TIMEOUT_INCONSISTENT",
                    config.endingGraceSeconds(),
                    "endingGrace should typically be <= transferTimeout"
            ));
        }
    }

    private void validatePositiveTimeout(long value, String field, List<ValidationIssue> issues) {
        if (value <= 0) {
            issues.add(ValidationIssue.error(
                    field,
                    field + " must be positive, got: " + value,
                    "TIMEOUT_NON_POSITIVE",
                    value,
                    "Set a positive value (e.g., 300)"
            ));
        } else if (value > MAX_TIMEOUT_SECONDS) {
            issues.add(ValidationIssue.warning(
                    field,
                    field + " is very large: " + value + "s (max recommended: " + MAX_TIMEOUT_SECONDS + "s)",
                    "TIMEOUT_TOO_LARGE",
                    value,
                    "Consider a smaller timeout value"
            ));
        }
    }

    private void validateFeatures(StateMachineMarketConfig config, List<ValidationIssue> issues) {
        // genesysEnabled requires transferEnabled
        if (config.genesysEnabled() && !config.transferEnabled()) {
            issues.add(ValidationIssue.warning(
                    "genesysEnabled",
                    "genesysEnabled is true but transferEnabled is false",
                    "FEATURE_INCONSISTENT",
                    config.genesysEnabled(),
                    "Genesys integration typically requires transfer to be enabled"
            ));
        }

        // surveyEnabled requires surveyTimeout > 0
        if (config.surveyEnabled() && config.surveyTimeoutSeconds() <= 0) {
            issues.add(ValidationIssue.error(
                    "surveyEnabled",
                    "surveyEnabled is true but surveyTimeoutSeconds is not positive",
                    "SURVEY_CONFIG_INVALID",
                    config.surveyTimeoutSeconds(),
                    "Set surveyTimeoutSeconds to a positive value"
            ));
        }
    }

    private void validateBusinessRules(StateMachineMarketConfig config, List<ValidationIssue> issues) {
        if (!VALID_FALLBACK_STRATEGIES.contains(config.fallbackRoutingStrategy())) {
            issues.add(ValidationIssue.error(
                    "fallbackRoutingStrategy",
                    "Invalid fallbackRoutingStrategy: " + config.fallbackRoutingStrategy(),
                    "INVALID_ENUM_VALUE",
                    config.fallbackRoutingStrategy(),
                    "Valid values: " + VALID_FALLBACK_STRATEGIES
            ));
        }

        if (config.surveyType() != null && !VALID_SURVEY_TYPES.contains(config.surveyType())) {
            issues.add(ValidationIssue.warning(
                    "surveyType",
                    "Unknown surveyType: " + config.surveyType(),
                    "UNKNOWN_SURVEY_TYPE",
                    config.surveyType(),
                    "Valid values: " + VALID_SURVEY_TYPES
            ));
        }

        if (!VALID_TRANSFER_TARGETS.contains(config.transferTarget())) {
            issues.add(ValidationIssue.error(
                    "transferTarget",
                    "Invalid transferTarget: " + config.transferTarget(),
                    "INVALID_ENUM_VALUE",
                    config.transferTarget(),
                    "Valid values: " + VALID_TRANSFER_TARGETS
            ));
        }

        // transferTarget=GENESYS requires genesysEnabled
        if ("GENESYS".equals(config.transferTarget()) && !config.genesysEnabled()) {
            issues.add(ValidationIssue.warning(
                    "transferTarget",
                    "transferTarget is GENESYS but genesysEnabled is false",
                    "TRANSFER_TARGET_INCONSISTENT",
                    config.transferTarget(),
                    "Enable genesysEnabled or change transferTarget"
            ));
        }
    }

    private void validateRetry(StateMachineMarketConfig config, List<ValidationIssue> issues) {
        if (config.maxRetries() < 0) {
            issues.add(ValidationIssue.error(
                    "maxRetries",
                    "maxRetries must be >= 0, got: " + config.maxRetries(),
                    "RETRY_NEGATIVE",
                    config.maxRetries(),
                    "Set maxRetries to 0 (no retry) or a positive value"
            ));
        } else if (config.maxRetries() > MAX_RETRIES) {
            issues.add(ValidationIssue.warning(
                    "maxRetries",
                    "maxRetries is very high: " + config.maxRetries() + " (max recommended: " + MAX_RETRIES + ")",
                    "RETRY_TOO_HIGH",
                    config.maxRetries(),
                    "Consider a lower retry count"
            ));
        }

        if (config.retryBaseDelayMs() <= 0) {
            issues.add(ValidationIssue.error(
                    "retryBaseDelayMs",
                    "retryBaseDelayMs must be positive, got: " + config.retryBaseDelayMs(),
                    "RETRY_DELAY_NON_POSITIVE",
                    config.retryBaseDelayMs(),
                    "Set a positive base delay (e.g., 1000ms)"
            ));
        }

        if (config.retryMaxDelayMs() <= 0) {
            issues.add(ValidationIssue.error(
                    "retryMaxDelayMs",
                    "retryMaxDelayMs must be positive, got: " + config.retryMaxDelayMs(),
                    "RETRY_DELAY_NON_POSITIVE",
                    config.retryMaxDelayMs(),
                    "Set a positive max delay"
            ));
        } else if (config.retryMaxDelayMs() > MAX_RETRY_DELAY_MS) {
            issues.add(ValidationIssue.warning(
                    "retryMaxDelayMs",
                    "retryMaxDelayMs is very large: " + config.retryMaxDelayMs() + "ms",
                    "RETRY_DELAY_TOO_LARGE",
                    config.retryMaxDelayMs(),
                    "Consider a smaller max delay"
            ));
        }

        if (config.retryBaseDelayMs() > config.retryMaxDelayMs()) {
            issues.add(ValidationIssue.error(
                    "retryBaseDelayMs",
                    "retryBaseDelayMs (" + config.retryBaseDelayMs() +
                            ") > retryMaxDelayMs (" + config.retryMaxDelayMs() + ")",
                    "RETRY_DELAY_INCONSISTENT",
                    config.retryBaseDelayMs(),
                    "base delay should be <= max delay"
            ));
        }
    }

    private void validateConnectors(StateMachineMarketConfig config, List<ValidationIssue> issues) {
        // aibotEndpoint should be a valid URL if aibotEnabled
        if (config.aibotEnabled() && config.aibotEndpoint() != null && !config.aibotEndpoint().isBlank()) {
            validateUrl(config.aibotEndpoint(), "aibotEndpoint", issues);
        }

        // genesysOrgId should be set if genesysEnabled
        if (config.genesysEnabled() && (config.genesysOrgId() == null || config.genesysOrgId().isBlank())) {
            issues.add(ValidationIssue.warning(
                    "genesysOrgId",
                    "genesysEnabled is true but genesysOrgId is not set",
                    "GENESYS_CONFIG_MISSING",
                    config.genesysOrgId(),
                    "Set genesysOrgId for Genesys integration"
            ));
        }
    }

    private void validateUrl(String url, String field, List<ValidationIssue> issues) {
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("wss://")) {
            issues.add(ValidationIssue.warning(
                    field,
                    field + " does not start with a valid protocol (http/https/wss): " + url,
                    "INVALID_URL_FORMAT",
                    url,
                    "Use a valid URL with protocol (e.g., https://...)"
            ));
        }
    }
}
