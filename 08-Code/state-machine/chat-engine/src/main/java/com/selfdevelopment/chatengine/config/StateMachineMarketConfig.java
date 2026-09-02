package com.selfdevelopment.chatengine.config;

import lombok.Builder;

@Builder
public record StateMachineMarketConfig(
        long customerIdleSeconds,
        long transferTimeoutSeconds,
        long endingGraceSeconds,
        boolean surveyEnabled,
        boolean transferEnabled,
        boolean genesysEnabled,
        String fallbackRoutingStrategy
) {
    public static StateMachineMarketConfig defaultConfig() {
        return StateMachineMarketConfig.builder()
                .customerIdleSeconds(300)
                .transferTimeoutSeconds(180)
                .endingGraceSeconds(120)
                .surveyEnabled(false)
                .transferEnabled(true)
                .genesysEnabled(false)
                .fallbackRoutingStrategy("DROP")
                .build();
    }
}