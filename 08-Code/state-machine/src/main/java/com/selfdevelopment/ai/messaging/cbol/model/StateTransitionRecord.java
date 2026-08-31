package com.selfdevelopment.ai.messaging.cbol.model;

import lombok.Builder;

@Builder
public record StateTransitionRecord(
        String businessId,
        String fromState,
        String toState,
        String fact,
        boolean guardResult,
        long timestampMs,
        String traceId,
        long durationMs
) {
}