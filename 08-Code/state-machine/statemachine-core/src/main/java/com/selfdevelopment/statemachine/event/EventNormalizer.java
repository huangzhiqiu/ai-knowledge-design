package com.selfdevelopment.statemachine.event;

import java.util.Optional;

/**
 * Normalizes events from external systems into the standard {@link StandardEvent} format.
 * <p>
 * Each external system (AIBot, Genesys, App/Web) has its own event format. The normalizer
 * converts these heterogeneous events into a unified format that the state machine pipeline
 * can process consistently.
 * <p>
 * Implementations should:
 * <ul>
 *   <li>Map external event types to standard event types</li>
 *   <li>Extract entity identifiers (conversationId, interactionId)</li>
 *   <li>Generate or propagate traceId</li>
 *   <li>Extract relevant payload data</li>
 *   <li>Return empty for events that should be filtered out</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * EventNormalizer<AibotWebhookEvent, StandardEvent> normalizer = new AibotEventNormalizer();
 * Optional<StandardEvent> standard = normalizer.normalize(aibotEvent);
 * standard.ifPresent(eventDispatcher::dispatch);
 * }</pre>
 *
 * @param <S> the source event type (external system event)
 * @param <D> the destination event type (usually StandardEvent)
 */
@FunctionalInterface
public interface EventNormalizer<S, D extends StandardEvent> {

    /**
     * Normalizes an external event into a standard event.
     *
     * @param sourceEvent the external event to normalize
     * @return the normalized standard event, or empty if the event should be filtered out
     */
    Optional<D> normalize(S sourceEvent);

    /**
     * Returns the source system identifier this normalizer handles.
     * Default implementation returns the simple class name.
     */
    default String getSourceSystem() {
        return this.getClass().getSimpleName();
    }

    /**
     * Checks whether this normalizer can handle the given source event.
     * Default implementation returns true for all non-null events.
     */
    default boolean canNormalize(S sourceEvent) {
        return sourceEvent != null;
    }
}
