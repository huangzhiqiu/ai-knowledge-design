package com.selfdevelopment.chatengine.config;

import lombok.Builder;

@Builder
public record StateMachineMarketConfig(
        long customerIdleSeconds,
        long transferTimeoutSeconds,
        long endingGraceSeconds,
        long surveyTimeoutSeconds,
        boolean surveyEnabled,
        boolean transferEnabled,
        boolean genesysEnabled,
        String fallbackRoutingStrategy,
        int maxRetries,
        long retryBaseDelayMs,
        long retryMaxDelayMs
) {
    public static StateMachineMarketConfig defaultConfig() {
        return StateMachineMarketConfig.builder()
                .customerIdleSeconds(300)
                .transferTimeoutSeconds(180)
                .endingGraceSeconds(120)
                .surveyTimeoutSeconds(120)
                .surveyEnabled(false)
                .transferEnabled(true)
                .genesysEnabled(false)
                .fallbackRoutingStrategy("DROP")
                .maxRetries(3)
                .retryBaseDelayMs(1000)
                .retryMaxDelayMs(30000)
                .build();
    }
}