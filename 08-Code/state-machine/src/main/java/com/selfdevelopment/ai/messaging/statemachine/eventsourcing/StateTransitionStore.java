package com.selfdevelopment.ai.messaging.statemachine.eventsourcing;

import java.util.List;
import java.util.Optional;

/**
 * Store for state transition events, supporting event sourcing patterns.
 * <p>
 * Implementations provide:
 * <ul>
 *   <li>Append-only event storage</li>
 *   <li>Full event replay for an entity</li>
 *   <li>State reconstruction by replaying events</li>
 *   <li>Event count and existence checks</li>
 * </ul>
 *
 * @param <S> the state type
 * @param <E> the event type
 */
public interface StateTransitionStore<S, E> {

    /**
     * Appends a transition event to the store.
     *
     * @param event the transition event to append
     */
    void append(StateTransitionEvent<S, E> event);

    /**
     * Replays all events for an entity, in chronological order.
     *
     * @param entityId the entity identifier
     * @return list of events (empty if none)
     */
    List<StateTransitionEvent<S, E>> replay(String entityId);

    /**
     * Replays events for an entity up to a specific timestamp.
     *
     * @param entityId  the entity identifier
     * @param upTo      include events with timestamp <= this value
     * @return list of events
     */
    List<StateTransitionEvent<S, E>> replayUpTo(String entityId, java.time.Instant upTo);

    /**
     * Reconstructs the current state of an entity by replaying all accepted events.
     * <p>
     * The state is determined by the {@code toState} of the last accepted transition.
     * If no accepted transitions exist, returns empty.
     *
     * @param entityId the entity identifier
     * @return the reconstructed state, or empty if no accepted events
     */
    Optional<S> reconstructState(String entityId);

    /**
     * Returns the number of events for an entity.
     *
     * @param entityId the entity identifier
     * @return the event count
     */
    int count(String entityId);

    /**
     * Checks whether an entity has any events.
     *
     * @param entityId the entity identifier
     * @return true if at least one event exists
     */
    boolean hasEvents(String entityId);

    /**
     * Returns the last event for an entity.
     *
     * @param entityId the entity identifier
     * @return the last event, or empty if none
     */
    Optional<StateTransitionEvent<S, E>> lastEvent(String entityId);

    /**
     * Clears all events for an entity (use with caution, primarily for testing).
     *
     * @param entityId the entity identifier
     */
    void clear(String entityId);

    /**
     * Clears all events in the store.
     */
    void clearAll();
}
