package com.selfdevelopment.ai.messaging.statemachine.timeout.impl;

import com.selfdevelopment.ai.messaging.statemachine.timeout.TimeoutConfig;

import com.selfdevelopment.ai.messaging.statemachine.timeout.StateMachineTimeoutScheduler;

import com.selfdevelopment.ai.messaging.statemachine.core.ExtendedState;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.core.Transition;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachineListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Decorator that adds automatic timeout scheduling to a {@link StateMachine}.
 * <p>
 * When the state machine transitions into a state with a configured timeout,
 * a timer is automatically started. If the state is left (via any transition)
 * before the timer elapses, the timer is cancelled. If the timer elapses,
 * the configured timeout event is automatically fired.
 * <p>
 * This eliminates the need for external monitor/timeout management code.
 * <p>
 * Usage:
 * <pre>{@code
 * StateMachine<ConvState, ConvEvent, CbolContext> machine = ...;
 * StateMachineTimeoutScheduler<ConvState, ConvEvent> scheduler =
 *     new InMemoryTimeoutScheduler<>("conv-timeout", 2);
 *
 * // Configure timeouts for specific states
 * Map<ConvState, TimeoutConfig<ConvState, ConvEvent>> timeouts = Map.of(
 *     ConvState.ACTIVE, TimeoutConfig.<ConvState, ConvEvent>builder()
 *         .state(ConvState.ACTIVE)
 *         .timeoutEvent(ConvEvent.IDLE_TIMEOUT)
 *         .duration(30).timeUnit(TimeUnit.SECONDS).build(),
 *     ConvState.TRANSFERRING, TimeoutConfig.<ConvState, ConvEvent>builder()
 *         .state(ConvState.TRANSFERRING)
 *         .timeoutEvent(ConvEvent.TRANSFER_TIMEOUT)
 *         .duration(60).timeUnit(TimeUnit.SECONDS).build()
 * );
 *
 * StateMachine<ConvState, ConvEvent, CbolContext> timeoutAware =
 *     new TimeoutAwareStateMachine<>(machine, scheduler, timeouts, "conv-123");
 *
 * // Now entering ACTIVE automatically starts a 30s idle timer
 * // Leaving ACTIVE automatically cancels it
 * // If 30s pass without leaving, IDLE_TIMEOUT is fired automatically
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class TimeoutAwareStateMachine<S, E, C> implements StateMachine<S, E, C> {

    private static final Logger log = LoggerFactory.getLogger(TimeoutAwareStateMachine.class);

    private final StateMachine<S, E, C> delegate;
    private final StateMachineTimeoutScheduler<S, E> scheduler;
    private final Map<S, TimeoutConfig<S, E>> timeoutConfigs;
    private final String entityId;

    /**
     * Creates a timeout-aware state machine.
     *
     * @param delegate       the underlying state machine
     * @param scheduler      the timeout scheduler
     * @param timeoutConfigs map of state to timeout configuration
     * @param entityId       the entity identifier (e.g., conversation ID)
     */
    public TimeoutAwareStateMachine(StateMachine<S, E, C> delegate,
                                     StateMachineTimeoutScheduler<S, E> scheduler,
                                     Map<S, TimeoutConfig<S, E>> timeoutConfigs,
                                     String entityId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.timeoutConfigs = timeoutConfigs != null
                ? Collections.unmodifiableMap(new HashMap<>(timeoutConfigs))
                : Collections.emptyMap();
        this.entityId = Objects.requireNonNull(entityId, "entityId must not be null");
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context) {
        return fireEvent(sourceState, event, context, null);
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context, ExtendedState extendedState) {
        // Cancel any existing timeout before transitioning (we're leaving the source state)
        scheduler.cancel(entityId);

        StateContext<S, E, C> result = delegate.fireEvent(sourceState, event, context, extendedState);

        // If the transition was accepted and the target state has a timeout config, schedule it
        if (result.isTransitionAccepted() && result.getTargetState() != null) {
            TimeoutConfig<S, E> config = timeoutConfigs.get(result.getTargetState());
            if (config != null) {
                scheduler.schedule(entityId, config, (id, timeoutEvent) -> {
                    log.info("Timeout fired for entity={}, firing event={}", id, timeoutEvent);
                    try {
                        // Re-read current state from the result's target state
                        // Since the state machine is stateless, we use the target state as current
                        delegate.fireEvent(result.getTargetState(), timeoutEvent, context);
                    } catch (Exception e) {
                        log.warn("Timeout event processing failed for entity={}, event={}: {}",
                                id, timeoutEvent, e.getMessage());
                    }
                });
            }
        }

        return result;
    }

    /**
     * Manually cancels the timeout for this entity.
     * Useful when the entity is closed/archived and no further timeouts should fire.
     */
    public void cancelTimeout() {
        scheduler.cancel(entityId);
    }

    /**
     * Returns whether a timeout is currently active for this entity.
     */
    public boolean isTimeoutActive() {
        return scheduler.isScheduled(entityId);
    }

    /**
     * Returns the remaining timeout in milliseconds, or -1 if no timeout is active.
     */
    public long getRemainingTimeoutMs() {
        return scheduler.getRemainingMs(entityId);
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
}
