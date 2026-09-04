package com.selfdevelopment.statemachine.resilience.impl;

import com.selfdevelopment.statemachine.resilience.FailureHandler;

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

/**
 * Decorator that adds resilience (failure handling and retry) to a {@link StateMachine}.
 * <p>
 * Wraps a state machine and delegates failures to a configurable {@link FailureHandler}.
 * Supports automatic retry for transient failures when a {@link RetryFailureHandler} is used.
 * <p>
 * Usage:
 * <pre>{@code
 * StateMachine<OrderState, OrderEvent, OrderContext> machine = ...;
 *
 * // Option 1: Throw on failure (default behavior)
 * StateMachine<...> resilient = new ResilientStateMachine<>(machine, new ThrowFailureHandler<>());
 *
 * // Option 2: Return source state on failure (no exceptions)
 * StateMachine<...> resilient = new ResilientStateMachine<>(machine, new ReturnSourceFailureHandler<>());
 *
 * // Option 3: Retry with exponential backoff, then fallback to ERROR state
 * FailureHandler<...> fallback = new FallbackStateFailureHandler<>(OrderState.ERROR);
 * RetryFailureHandler<...> retry = RetryFailureHandler.exponentialBackoff(3, fallback, 100, 5000);
 * StateMachine<...> resilient = new ResilientStateMachine<>(machine, retry);
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class ResilientStateMachine<S, E, C> implements StateMachine<S, E, C> {

    private static final Logger log = LoggerFactory.getLogger(ResilientStateMachine.class);

    private final StateMachine<S, E, C> delegate;
    private final FailureHandler<S, E, C> failureHandler;

    /**
     * Creates a resilient state machine.
     *
     * @param delegate       the underlying state machine
     * @param failureHandler the failure handler strategy
     */
    public ResilientStateMachine(StateMachine<S, E, C> delegate, FailureHandler<S, E, C> failureHandler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler must not be null");
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context) {
        return fireEvent(sourceState, event, context, null);
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context, ExtendedState extendedState) {
        int maxAttempts = 1;
        RetryFailureHandler<S, E, C> retryHandler = null;

        if (failureHandler instanceof RetryFailureHandler<S, E, C> retry) {
            retryHandler = retry;
            maxAttempts = retry.getMaxRetries() + 1;  // initial attempt + retries
        }

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.fireEvent(sourceState, event, context, extendedState);
            } catch (StateMachineException e) {
                lastException = e;
                FailureHandler.FailureType type = determineFailureType(e);

                if (attempt < maxAttempts && retryHandler != null) {
                    long backoff = retryHandler.getBackoffDelay(attempt, e);
                    if (backoff > 0) {
                        try {
                            Thread.sleep(backoff);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    log.debug("Retry attempt {}/{} for transition: state={}, event={}",
                            attempt + 1, maxAttempts, sourceState, event);
                    continue;
                }

                // All retries exhausted or no retry handler — delegate to failure handler
                return failureHandler.handleFailure(type, sourceState, event, context, null, e);
            } catch (RuntimeException e) {
                lastException = e;
                if (attempt < maxAttempts && retryHandler != null) {
                    long backoff = retryHandler.getBackoffDelay(attempt, e);
                    if (backoff > 0) {
                        try {
                            Thread.sleep(backoff);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    continue;
                }
                return failureHandler.handleFailure(FailureHandler.FailureType.ACTION_ERROR,
                        sourceState, event, context, null, e);
            }
        }

        // Should not reach here, but safe fallback
        return failureHandler.handleFailure(FailureHandler.FailureType.ACTION_ERROR,
                sourceState, event, context, null, lastException);
    }

    private FailureHandler.FailureType determineFailureType(StateMachineException e) {
        String message = e.getMessage() != null ? e.getMessage() : "";
        if (message.contains("No transition found")) {
            return FailureHandler.FailureType.NO_TRANSITION;
        }
        if (message.contains("guard condition failed")) {
            return FailureHandler.FailureType.GUARD_FAILED;
        }
        return FailureHandler.FailureType.ACTION_ERROR;
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
     * Returns the failure handler.
     */
    public FailureHandler<S, E, C> getFailureHandler() {
        return failureHandler;
    }
}
