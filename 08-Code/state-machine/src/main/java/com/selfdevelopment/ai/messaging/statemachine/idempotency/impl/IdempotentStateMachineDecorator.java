package com.selfdevelopment.ai.messaging.statemachine.idempotency.impl;

import com.selfdevelopment.ai.messaging.statemachine.idempotency.ProcessedEventStore;

import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.core.Transition;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachineListener;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decorator that adds idempotency to a {@link StateMachine} by deduplicating
 * events based on a unique event ID.
 * <p>
 * When an event with the same ID is fired multiple times, only the first
 * invocation is processed; subsequent invocations return the cached result
 * from the first invocation.
 * <p>
 * Usage:
 * <pre>{@code
 * StateMachine<State, Event, Context> machine = ...;
 * IdempotentStateMachineDecorator<State, Event, Context> idempotent =
 *     new IdempotentStateMachineDecorator<>(machine, new InMemoryProcessedEventStore());
 *
 * // First invocation — processed
 * StateContext<...> result1 = idempotent.fireEvent(state, event, context, "event-123");
 *
 * // Second invocation with same ID — returns cached result, no reprocessing
 * StateContext<...> result2 = idempotent.fireEvent(state, event, context, "event-123");
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class IdempotentStateMachineDecorator<S, E, C> implements StateMachine<S, E, C> {

    private final StateMachine<S, E, C> delegate;
    private final ProcessedEventStore eventStore;
    private final Map<String, StateContext<S, E, C>> resultCache = new ConcurrentHashMap<>();

    /**
     * Creates an idempotent decorator with the given event store.
     *
     * @param delegate   the underlying state machine
     * @param eventStore the store for tracking processed event IDs
     */
    public IdempotentStateMachineDecorator(StateMachine<S, E, C> delegate,
                                            ProcessedEventStore eventStore) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    }

    /**
     * Creates an idempotent decorator with a default in-memory event store.
     *
     * @param delegate the underlying state machine
     */
    public IdempotentStateMachineDecorator(StateMachine<S, E, C> delegate) {
        this(delegate, new InMemoryProcessedEventStore());
    }

    /**
     * Fires an event with idempotency based on the given event ID.
     * <p>
     * If the event ID has already been processed, returns the cached result
     * without reprocessing. Otherwise, processes the event and caches the result.
     *
     * @param sourceState the current state
     * @param event       the event to fire
     * @param context     the business context
     * @param eventId     a unique identifier for this event (for deduplication)
     * @return the state context after the transition (or cached result)
     */
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context, String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        // Check if already processed
        if (eventStore.isProcessed(eventId)) {
            StateContext<S, E, C> cached = resultCache.get(eventId);
            if (cached != null) {
                return cached;
            }
            // Event was processed but result not cached (shouldn't happen, but safe fallback)
            throw new IllegalStateException("Event '" + eventId + "' was processed but result is not cached");
        }

        // Process the event
        StateContext<S, E, C> result = delegate.fireEvent(sourceState, event, context);

        // Mark as processed and cache result
        if (eventStore.markProcessed(eventId)) {
            resultCache.put(eventId, result);
        }

        return result;
    }

    /**
     * Fires an event without an explicit event ID.
     * <p>
     * Without an event ID, idempotency cannot be guaranteed. A unique ID
     * is generated from (sourceState, event, context hashCode) but this
     * may not be truly unique. Use {@link #fireEvent(Object, Object, Object, String)}
     * for reliable idempotency.
     */
    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context) {
        String generatedId = generateEventId(sourceState, event, context);
        return fireEvent(sourceState, event, context, generatedId);
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context,
                                             com.selfdevelopment.ai.messaging.statemachine.core.ExtendedState extendedState) {
        return delegate.fireEvent(sourceState, event, context, extendedState);
    }

    // ===== Delegated methods =====

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public boolean isStarted() {
        return delegate.isStarted();
    }

    @Override
    public boolean hasTransition(S sourceState, E event) {
        return delegate.hasTransition(sourceState, event);
    }

    @Override
    public boolean canFire(S sourceState, E event, C context) {
        return delegate.canFire(sourceState, event, context);
    }

    @Override
    public int getTransitionCount() {
        return delegate.getTransitionCount();
    }

    @Override
    public Collection<Transition<S, E, C>> getAllTransitions() {
        return delegate.getAllTransitions();
    }

    @Override
    public String getMachineId() {
        return delegate.getMachineId();
    }

    @Override
    public S getInitialState() {
        return delegate.getInitialState();
    }

    @Override
    public Collection<S> getEndStates() {
        return delegate.getEndStates();
    }

    @Override
    public void addListener(StateMachineListener<S, E, C> listener) {
        delegate.addListener(listener);
    }

    @Override
    public void removeListener(StateMachineListener<S, E, C> listener) {
        delegate.removeListener(listener);
    }

    // ===== Idempotency management =====

    /**
     * Returns whether an event has been processed.
     *
     * @param eventId the event identifier
     * @return true if processed
     */
    public boolean isEventProcessed(String eventId) {
        return eventStore.isProcessed(eventId);
    }

    /**
     * Returns the cached result for a processed event.
     *
     * @param eventId the event identifier
     * @return the cached result, or null if not processed
     */
    public StateContext<S, E, C> getCachedResult(String eventId) {
        return resultCache.get(eventId);
    }

    /**
     * Clears the idempotency cache and processed event store.
     * Use with caution — this allows previously processed events to be reprocessed.
     */
    public void clearIdempotencyCache() {
        resultCache.clear();
        eventStore.clear();
    }

    /**
     * Returns the number of processed events.
     *
     * @return the count
     */
    public int getProcessedEventCount() {
        return eventStore.size();
    }

    private String generateEventId(S sourceState, E event, C context) {
        return sourceState + "|" + event + "|" + (context != null ? context.hashCode() : "null");
    }
}
