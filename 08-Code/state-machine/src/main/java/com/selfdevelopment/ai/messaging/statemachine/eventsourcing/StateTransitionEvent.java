package com.selfdevelopment.ai.messaging.statemachine.eventsourcing;

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
public record StateTransitionEvent<S, E>(
        String entityId,
        String machineId,
        S fromState,
        S toState,
        E event,
        boolean accepted,
        String denialReason,
        long durationMs,
        String traceId,
        Instant timestamp,
        Map<String, String> metadata
) {
    /**
     * Creates a builder for a state transition event.
     */
    public static <S, E> Builder<S, E> builder() {
        return new Builder<>();
    }

    /**
     * Builder for {@link StateTransitionEvent}.
     */
    public static class Builder<S, E> {
        private String entityId;
        private String machineId;
        private S fromState;
        private S toState;
        private E event;
        private boolean accepted = true;
        private String denialReason;
        private long durationMs;
        private String traceId;
        private Instant timestamp = Instant.now();
        private Map<String, String> metadata = Map.of();

        public Builder<S, E> entityId(String entityId) { this.entityId = entityId; return this; }
        public Builder<S, E> machineId(String machineId) { this.machineId = machineId; return this; }
        public Builder<S, E> fromState(S fromState) { this.fromState = fromState; return this; }
        public Builder<S, E> toState(S toState) { this.toState = toState; return this; }
        public Builder<S, E> event(E event) { this.event = event; return this; }
        public Builder<S, E> accepted(boolean accepted) { this.accepted = accepted; return this; }
        public Builder<S, E> denialReason(String denialReason) { this.denialReason = denialReason; return this; }
        public Builder<S, E> durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder<S, E> traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder<S, E> timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder<S, E> metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }

        public StateTransitionEvent<S, E> build() {
            return new StateTransitionEvent<>(entityId, machineId, fromState, toState, event,
                    accepted, denialReason, durationMs, traceId, timestamp, metadata);
        }
    }
}
