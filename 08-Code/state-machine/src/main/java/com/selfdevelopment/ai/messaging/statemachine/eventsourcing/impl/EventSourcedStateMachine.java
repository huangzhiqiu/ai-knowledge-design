package com.selfdevelopment.ai.messaging.statemachine.eventsourcing.impl;

import com.selfdevelopment.ai.messaging.statemachine.eventsourcing.StateTransitionStore;

import com.selfdevelopment.ai.messaging.statemachine.eventsourcing.StateTransitionEvent;

import com.selfdevelopment.ai.messaging.statemachine.core.ExtendedState;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.core.Transition;
import com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachineListener;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

/**
 * Decorator that adds event sourcing to a {@link StateMachine}.
 * <p>
 * Every transition (successful, denied, or errored) is recorded as an immutable
 * event in a {@link StateTransitionStore}. This enables:
 * <ul>
 *   <li>Full audit trail of all state changes</li>
 *   <li>State reconstruction by replaying events</li>
 *   <li>Time-travel queries (state at any point in time)</li>
 *   <li>Debugging and root cause analysis</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * StateMachine<OrderState, OrderEvent, OrderContext> machine = ...;
 * StateTransitionStore<OrderState, OrderEvent> store = new InMemoryStateTransitionStore<>();
 *
 * StateMachine<OrderState, OrderEvent, OrderContext> eventSourced =
 *     new EventSourcedStateMachine<>(machine, store, "order-123");
 *
 * // All transitions are automatically recorded
 * eventSourced.fireEvent(OrderState.CREATED, OrderEvent.PAY, context);
 *
 * // Replay and reconstruct
 * List<StateTransitionEvent<...>> history = store.replay("order-123");
 * Optional<OrderState> current = store.reconstructState("order-123");
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class EventSourcedStateMachine<S, E, C> implements StateMachine<S, E, C> {

    private final StateMachine<S, E, C> delegate;
    private final StateTransitionStore<S, E> store;
    private final String defaultEntityId;

    /**
     * Creates an event-sourced state machine.
     *
     * @param delegate         the underlying state machine
     * @param store            the event store
     * @param defaultEntityId the default entity ID used when recording events
     */
    public EventSourcedStateMachine(StateMachine<S, E, C> delegate,
                                     StateTransitionStore<S, E> store,
                                     String defaultEntityId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.defaultEntityId = defaultEntityId;
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context) {
        return fireEvent(sourceState, event, context, null);
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context, ExtendedState extendedState) {
        long start = System.currentTimeMillis();
        String entityId = resolveEntityId(context);

        try {
            StateContext<S, E, C> result = delegate.fireEvent(sourceState, event, context, extendedState);
            long durationMs = System.currentTimeMillis() - start;

            // Record successful transition
            store.append(StateTransitionEvent.<S, E>builder()
                    .entityId(entityId)
                    .machineId(delegate.getMachineId())
                    .fromState(sourceState)
                    .toState(result.getTargetState())
                    .event(event)
                    .accepted(true)
                    .durationMs(durationMs)
                    .timestamp(Instant.now())
                    .build());

            return result;

        } catch (StateMachineException e) {
            long durationMs = System.currentTimeMillis() - start;
            String reason = e.getMessage() != null ? e.getMessage() : "unknown";

            // Record denied transition
            store.append(StateTransitionEvent.<S, E>builder()
                    .entityId(entityId)
                    .machineId(delegate.getMachineId())
                    .fromState(sourceState)
                    .toState(sourceState)  // state unchanged
                    .event(event)
                    .accepted(false)
                    .denialReason(reason)
                    .durationMs(durationMs)
                    .timestamp(Instant.now())
                    .build());

            throw e;
        }
    }

    /**
     * Resolves the entity ID from the context, falling back to the default.
     * <p>
     * Override this method to extract entity ID from your custom context type.
     */
    protected String resolveEntityId(C context) {
        return defaultEntityId;
    }

    // ===== Delegated methods =====

    @Override
    public void start() { delegate.start(); }

    @Override
    public void stop() { delegate.stop(); }

    @Override
    public boolean isStarted() { return delegate.isStarted(); }

    @Override
    public boolean hasTransition(S sourceState, E event) { return delegate.hasTransition(sourceState, event); }

    @Override
    public boolean canFire(S sourceState, E event, C context) { return delegate.canFire(sourceState, event, context); }

    @Override
    public int getTransitionCount() { return delegate.getTransitionCount(); }

    @Override
    public Collection<Transition<S, E, C>> getAllTransitions() { return delegate.getAllTransitions(); }

    @Override
    public String getMachineId() { return delegate.getMachineId(); }

    @Override
    public S getInitialState() { return delegate.getInitialState(); }

    @Override
    public Collection<S> getEndStates() { return delegate.getEndStates(); }

    @Override
    public void addListener(StateMachineListener<S, E, C> listener) { delegate.addListener(listener); }

    @Override
    public void removeListener(StateMachineListener<S, E, C> listener) { delegate.removeListener(listener); }

    /**
     * Returns the underlying event store.
     */
    public StateTransitionStore<S, E> getStore() {
        return store;
    }
}
