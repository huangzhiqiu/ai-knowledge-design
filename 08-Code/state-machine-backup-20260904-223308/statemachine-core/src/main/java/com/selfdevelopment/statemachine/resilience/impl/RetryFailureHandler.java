package com.selfdevelopment.statemachine.resilience.impl;

import com.selfdevelopment.statemachine.resilience.FailureHandler;

import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.core.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Failure handler that retries the transition a configurable number of times before delegating to a fallback handler.
 * <p>
 * Note: This handler itself does not perform retries (it is stateless). Instead,
 * it signals to the {@link ResilientStateMachine} decorator that a retry should
 * be attempted by returning a special context with retry metadata.
 * <p>
 * Use this when:
 * <ul>
 *   <li>Transient failures (network blips, temporary resource unavailability)</li>
 *   <li>Optimistic lock conflicts that should be retried</li>
 *   <li>Actions that may succeed on retry due to eventual consistency</li>
 * </ul>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class RetryFailureHandler<S, E, C> implements FailureHandler<S, E, C> {

    private static final Logger log = LoggerFactory.getLogger(RetryFailureHandler.class);

    private final int maxRetries;
    private final FailureHandler<S, E, C> fallbackHandler;
    private final BiFunction<Integer, Exception, Long> backoffStrategy;

    /**
     * Creates a retry handler with the given max retries and fallback.
     *
     * @param maxRetries      maximum number of retry attempts
     * @param fallbackHandler handler to use after all retries are exhausted
     */
    public RetryFailureHandler(int maxRetries, FailureHandler<S, E, C> fallbackHandler) {
        this(maxRetries, fallbackHandler, (attempt, ex) -> 0L);  // no backoff by default
    }

    /**
     * Creates a retry handler with backoff strategy.
     *
     * @param maxRetries      maximum number of retry attempts
     * @param fallbackHandler handler to use after all retries are exhausted
     * @param backoffStrategy function returning backoff delay in ms for a given attempt (1-based)
     */
    public RetryFailureHandler(int maxRetries,
                                FailureHandler<S, E, C> fallbackHandler,
                                BiFunction<Integer, Exception, Long> backoffStrategy) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        this.maxRetries = maxRetries;
        this.fallbackHandler = Objects.requireNonNull(fallbackHandler, "fallbackHandler must not be null");
        this.backoffStrategy = Objects.requireNonNull(backoffStrategy, "backoffStrategy must not be null");
    }

    @Override
    public StateContext<S, E, C> handleFailure(FailureType type,
                                                 S sourceState,
                                                 E event,
                                                 C context,
                                                 Transition<S, E, C> transition,
                                                 Exception exception) {
        // Retry is handled by the ResilientStateMachine decorator.
        // This method is called when retries are exhausted, so delegate to fallback.
        log.warn("All {} retries exhausted for transition: state={}, event={}, type={}",
                maxRetries, sourceState, event, type);
        return fallbackHandler.handleFailure(type, sourceState, event, context, transition, exception);
    }

    /**
     * Returns the maximum number of retries.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Returns the backoff delay for a given retry attempt.
     *
     * @param attempt   the retry attempt number (1-based)
     * @param exception the exception that caused the retry
     * @return backoff delay in milliseconds
     */
    public long getBackoffDelay(int attempt, Exception exception) {
        return backoffStrategy.apply(attempt, exception);
    }

    /**
     * Creates a retry handler with exponential backoff.
     *
     * @param maxRetries      maximum retries
     * @param fallbackHandler fallback after exhaustion
     * @param initialDelayMs  initial delay in ms
     * @param maxDelayMs      maximum delay in ms
     */
    public static <S, E, C> RetryFailureHandler<S, E, C> exponentialBackoff(
            int maxRetries, FailureHandler<S, E, C> fallbackHandler,
            long initialDelayMs, long maxDelayMs) {
        return new RetryFailureHandler<>(maxRetries, fallbackHandler,
                (attempt, ex) -> Math.min(initialDelayMs * (1L << (attempt - 1)), maxDelayMs));
    }

    /**
     * Creates a retry handler with fixed delay backoff.
     */
    public static <S, E, C> RetryFailureHandler<S, E, C> fixedDelay(
            int maxRetries, FailureHandler<S, E, C> fallbackHandler, long delayMs) {
        return new RetryFailureHandler<>(maxRetries, fallbackHandler, (attempt, ex) -> delayMs);
    }
}
