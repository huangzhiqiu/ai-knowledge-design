package com.selfdevelopment.ai.messaging.statemachine.eventsourcing;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable record of a single state transition event.
 * <p>
 * Used for event sourcing, audit logging, and state replay.
 *
 * @param <S> the state type
 * @param <E> the event type
 */
@Getter
@Builder
public final class StateTransitionEvent<S, E> {

    private final String entityId;
    private final String machineId;
    private final S fromState;
    private final S toState;
    private final E event;

    @Builder.Default
    private final boolean accepted = true;

    private final String denialReason;
    private final long durationMs;
    private final String traceId;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    @Builder.Default
    private final Map<String, String> metadata = Map.of();
}
