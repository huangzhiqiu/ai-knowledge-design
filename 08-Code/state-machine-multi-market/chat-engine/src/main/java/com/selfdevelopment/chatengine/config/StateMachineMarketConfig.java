package com.selfdevelopment.chatengine.config;

import lombok.Builder;

import java.util.List;

/**
 * Market-specific configuration for the state machine.
 * <p>
 * Supports multi-market deployment with per-market overrides for timeouts,
 * feature toggles, business rules, connector settings, and extensions.
 * <p>
 * Config can be loaded from YAML files with three-layer inheritance:
 * base-profile → regional-profile → market-profile
 */
@Builder
public record StateMachineMarketConfig(
        // === Timeouts ===
        long customerIdleSeconds,
        long transferTimeoutSeconds,
        long endingGraceSeconds,
        long surveyTimeoutSeconds,

        // === Feature Toggles ===
        boolean surveyEnabled,
        boolean transferEnabled,
        boolean genesysEnabled,
        boolean aibotEnabled,
        boolean regulatoryAuditEnabled,

        // === Business Rules ===
        String fallbackRoutingStrategy,      // DROP / REQUEUE / FALLBACK_QUEUE
        String surveyType,                    // CSAT / NPS / CES
        String transferTarget,                // GENESYS / INTERNAL_QUEUE / AIBOT

        // === Retry Configuration ===
        int maxRetries,
        long retryBaseDelayMs,
        long retryMaxDelayMs,

        // === Connector Settings ===
        String aibotEndpoint,
        String genesysOrgId,
        String websocketEndpoint,

        // === Extensions ===
        List<String> enabledExtensions        // e.g., ["HKRegulatoryAudit", "UKGdprRetention"]
) {
    /**
     * Default configuration used when no market-specific config is provided.
     */
    public static StateMachineMarketConfig defaultConfig() {
        return StateMachineMarketConfig.builder()
                .customerIdleSeconds(300)
                .transferTimeoutSeconds(180)
                .endingGraceSeconds(120)
                .surveyTimeoutSeconds(120)
                .surveyEnabled(false)
                .transferEnabled(true)
                .genesysEnabled(false)
                .aibotEnabled(true)
                .regulatoryAuditEnabled(false)
                .fallbackRoutingStrategy("DROP")
                .surveyType("CSAT")
                .transferTarget("INTERNAL_QUEUE")
                .maxRetries(3)
                .retryBaseDelayMs(1000)
                .retryMaxDelayMs(30000)
                .aibotEndpoint(null)
                .genesysOrgId(null)
                .websocketEndpoint(null)
                .enabledExtensions(List.of())
                .build();
    }

    /**
     * Merge this config with a higher-priority override.
     * Non-null/non-zero values from override take precedence.
     *
     * @param override higher-priority config
     * @return merged config
     */
    public StateMachineMarketConfig mergeWith(StateMachineMarketConfig override) {
        if (override == null) {
            return this;
        }
        return StateMachineMarketConfig.builder()
                .customerIdleSeconds(override.customerIdleSeconds() > 0 ? override.customerIdleSeconds() : this.customerIdleSeconds())
                .transferTimeoutSeconds(override.transferTimeoutSeconds() > 0 ? override.transferTimeoutSeconds() : this.transferTimeoutSeconds())
                .endingGraceSeconds(override.endingGraceSeconds() > 0 ? override.endingGraceSeconds() : this.endingGraceSeconds())
                .surveyTimeoutSeconds(override.surveyTimeoutSeconds() > 0 ? override.surveyTimeoutSeconds() : this.surveyTimeoutSeconds())
                .surveyEnabled(override.surveyEnabled() || this.surveyEnabled())
                .transferEnabled(override.transferEnabled() && this.transferEnabled())
                .genesysEnabled(override.genesysEnabled() || this.genesysEnabled())
                .aibotEnabled(override.aibotEnabled() && this.aibotEnabled())
                .regulatoryAuditEnabled(override.regulatoryAuditEnabled() || this.regulatoryAuditEnabled())
                .fallbackRoutingStrategy(override.fallbackRoutingStrategy() != null ? override.fallbackRoutingStrategy() : this.fallbackRoutingStrategy())
                .surveyType(override.surveyType() != null ? override.surveyType() : this.surveyType())
                .transferTarget(override.transferTarget() != null ? override.transferTarget() : this.transferTarget())
                .maxRetries(override.maxRetries() > 0 ? override.maxRetries() : this.maxRetries())
                .retryBaseDelayMs(override.retryBaseDelayMs() > 0 ? override.retryBaseDelayMs() : this.retryBaseDelayMs())
                .retryMaxDelayMs(override.retryMaxDelayMs() > 0 ? override.retryMaxDelayMs() : this.retryMaxDelayMs())
                .aibotEndpoint(override.aibotEndpoint() != null ? override.aibotEndpoint() : this.aibotEndpoint())
                .genesysOrgId(override.genesysOrgId() != null ? override.genesysOrgId() : this.genesysOrgId())
                .websocketEndpoint(override.websocketEndpoint() != null ? override.websocketEndpoint() : this.websocketEndpoint())
                .enabledExtensions(override.enabledExtensions() != null && !override.enabledExtensions().isEmpty()
                        ? override.enabledExtensions()
                        : this.enabledExtensions())
                .build();
    }
}
