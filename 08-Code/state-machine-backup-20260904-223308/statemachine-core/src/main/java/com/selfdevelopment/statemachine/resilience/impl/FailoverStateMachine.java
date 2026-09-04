package com.selfdevelopment.statemachine.resilience.impl;

import com.selfdevelopment.statemachine.resilience.FailureHandler;

import com.selfdevelopment.statemachine.resilience.FailoverContext;

import com.selfdevelopment.statemachine.core.ExtendedState;
import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.core.Transition;
import com.selfdevelopment.statemachine.exception.StateMachineException;
import com.selfdevelopment.statemachine.api.StateMachineListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Decorator that adds failover (automatic fail event generation) to a {@link StateMachine}.
 * <p>
 * When an action throws an unhandled {@link RuntimeException} (ACTION_ERROR), this decorator
 * automatically generates a fail event (via {@code failEventProvider}) and re-fires it through
 * the state machine to follow a predefined fail branch.
 * <p>
 * <b>Key design decisions:</b>
 * <ul>
 *   <li>Only handles {@link RuntimeException} (action execution errors), not
 *       {@link StateMachineException} (no transition / guard failed — those are logical, not system errors)</li>
 *   <li>Loop prevention: fail events themselves do NOT trigger failover (configurable via
 *       {@code failEventPredicate})</li>
 *   <li>Composable with {@link ResilientStateMachine}: use RetryFailureHandler first, then
 *       FailoverStateMachine as the final safety net after retries are exhausted</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * StateMachine<OrderState, OrderEvent, OrderContext> machine = ...;
 *
 * // Failover: on action error, fire ORDER_FAILED event
 * StateMachine<...> failover = new FailoverStateMachine<>(
 *     machine,
 *     ctx -> OrderEvent.ORDER_FAILED,
 *     event -> event == OrderEvent.ORDER_FAILED  // don't failover on fail events
 * );
 *
 * // Combined with retry: retry 3 times, then failover
 * FailureHandler<...> fallback = new FallbackStateFailureHandler<>(OrderState.ERROR);
 * RetryFailureHandler<...> retry = RetryFailureHandler.exponentialBackoff(3, fallback, 100, 5000);
 * StateMachine<...> resilient = new ResilientStateMachine<>(machine, retry);
 * StateMachine<...> withFailover = new FailoverStateMachine<>(resilient, ctx -> OrderEvent.ORDER_FAILED, ...);
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class FailoverStateMachine<S, E, C> implements StateMachine<S, E, C> {

    private static final Logger log = LoggerFactory.getLogger(FailoverStateMachine.class);

    private final StateMachine<S, E, C> delegate;
    private final Function<FailoverContext<S, E, C>, E> failEventProvider;
    private final Predicate<E> failEventPredicate;

    /**
     * Creates a failover state machine.
     *
     * @param delegate           the underlying state machine
     * @param failEventProvider  function that generates the fail event from failure context
     * @param failEventPredicate predicate that returns true if an event is a fail event
     *                           (fail events do NOT trigger another failover, preventing infinite loops)
     */
    public FailoverStateMachine(StateMachine<S, E, C> delegate,
                                 Function<FailoverContext<S, E, C>, E> failEventProvider,
                                 Predicate<E> failEventPredicate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.failEventProvider = Objects.requireNonNull(failEventProvider, "failEventProvider must not be null");
        this.failEventPredicate = Objects.requireNonNull(failEventPredicate, "failEventPredicate must not be null");
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context) {
        return fireEvent(sourceState, event, context, null);
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context, ExtendedState extendedState) {
        try {
            return delegate.fireEvent(sourceState, event, context, extendedState);
        } catch (StateMachineException e) {
            // StateMachineException has two categories:
            // 1. Logical errors (no transition / guard failed) — no cause, do NOT failover
            // 2. Action execution errors — has cause (original RuntimeException), trigger failover
            if (e.getCause() instanceof RuntimeException actionEx) {
                // Only failover if the current event is NOT itself a fail event (prevent infinite loops)
                if (failEventPredicate.test(event)) {
                    log.warn("Fail event '{}' itself threw an exception, not triggering another failover (loop prevention)",
                            event);
                    throw e;
                }
                return doFailover(sourceState, event, context, extendedState, actionEx);
            }
            // Logical error (no transition / guard failed) — rethrow as-is
            throw e;
        } catch (RuntimeException e) {
            // Direct RuntimeException (not wrapped in StateMachineException) — action error
            if (failEventPredicate.test(event)) {
                log.warn("Fail event '{}' itself threw an exception, not triggering another failover (loop prevention)",
                        event);
                throw e;
            }
            return doFailover(sourceState, event, context, extendedState, e);
        }
    }

    /**
     * Performs the failover: generates a fail event and re-fires it through the state machine.
     */
    private StateContext<S, E, C> doFailover(S sourceState, E originalEvent, C context,
                                                ExtendedState extendedState, RuntimeException cause) {
        FailoverContext<S, E, C> failCtx = new FailoverContext<>(sourceState, originalEvent, context, cause);
        E failEvent = failEventProvider.apply(failCtx);

        log.warn("Action failed for event '{}' from state '{}', triggering failover event '{}' (cause: {})",
                originalEvent, sourceState, failEvent, cause.toString());

        try {
            StateContext<S, E, C> result = delegate.fireEvent(sourceState, failEvent, context, extendedState);
            log.info("Failover completed: state={}, failEvent={}, targetState={}",
                    sourceState, failEvent, result.getTargetState());
            return result;
        } catch (RuntimeException failoverEx) {
            // Fail event itself failed — this is a critical error, do not loop
            log.error("Failover event '{}' also failed (loop prevention), original cause: {}",
                    failEvent, cause.toString(), failoverEx);
            throw failoverEx;
        }
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
     * Returns the underlying delegate state machine.
     */
    public StateMachine<S, E, C> getDelegate() {
        return delegate;
    }
}
