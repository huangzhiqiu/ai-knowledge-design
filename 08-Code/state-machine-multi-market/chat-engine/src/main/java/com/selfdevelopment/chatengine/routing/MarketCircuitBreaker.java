package com.selfdevelopment.chatengine.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Market isolation circuit breaker.
 * <p>
 * Prevents failures in one market from cascading to other markets.
 * Each market has its own circuit breaker with independent state.
 * <p>
 * States: CLOSED → OPEN → HALF_OPEN → CLOSED
 */
public class MarketCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(MarketCircuitBreaker.class);

    private final Map<String, CircuitState> breakers = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final long openDurationMs;
    private final int halfOpenSuccessThreshold;

    public MarketCircuitBreaker(int failureThreshold, long openDurationMs, int halfOpenSuccessThreshold) {
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
        this.halfOpenSuccessThreshold = halfOpenSuccessThreshold;
    }

    /**
     * Check if a request is allowed for the given market.
     *
     * @param market market code
     * @return true if request is allowed, false if circuit is open
     */
    public boolean allowRequest(String market) {
        CircuitState state = breakers.computeIfAbsent(market, k -> new CircuitState());
        return state.allowRequest();
    }

    /**
     * Record a successful request for the given market.
     *
     * @param market market code
     */
    public void recordSuccess(String market) {
        CircuitState state = breakers.get(market);
        if (state != null) {
            state.recordSuccess();
        }
    }

    /**
     * Record a failed request for the given market.
     *
     * @param market market code
     */
    public void recordFailure(String market) {
        CircuitState state = breakers.computeIfAbsent(market, k -> new CircuitState());
        state.recordFailure();
    }

    /**
     * Get current state of a market's circuit breaker.
     *
     * @param market market code
     * @return current state (CLOSED, OPEN, HALF_OPEN)
     */
    public State getState(String market) {
        CircuitState state = breakers.get(market);
        if (state == null) {
            return State.CLOSED;
        }
        return state.getState();
    }

    /**
     * Reset circuit breaker for a market.
     *
     * @param market market code
     */
    public void reset(String market) {
        CircuitState state = breakers.get(market);
        if (state != null) {
            state.reset();
        }
    }

    /**
     * Circuit breaker states.
     */
    public enum State {
        CLOSED,      // Normal operation, requests allowed
        OPEN,        // Failure threshold exceeded, requests rejected
        HALF_OPEN    // Testing if service has recovered
    }

    /**
     * Internal circuit state per market.
     */
    private class CircuitState {
        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
        private volatile Instant openedAt;

        boolean allowRequest() {
            State currentState = state.get();

            if (currentState == State.CLOSED) {
                return true;
            }

            if (currentState == State.OPEN) {
                // Check if open duration has elapsed
                if (openedAt != null &&
                        Instant.now().isAfter(openedAt.plusMillis(openDurationMs))) {
                    // Transition to HALF_OPEN
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        halfOpenSuccessCount.set(0);
                        log.info("Circuit breaker for market transitioned to HALF_OPEN");
                        return true;
                    }
                    return false;
                }
                return false;
            }

            // HALF_OPEN: allow limited requests
            return true;
        }

        void recordSuccess() {
            State currentState = state.get();

            if (currentState == State.HALF_OPEN) {
                int successes = halfOpenSuccessCount.incrementAndGet();
                if (successes >= halfOpenSuccessThreshold) {
                    if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                        failureCount.set(0);
                        log.info("Circuit breaker for market transitioned to CLOSED after {} successes", successes);
                    }
                }
            } else if (currentState == State.CLOSED) {
                // Reset failure count on success
                failureCount.set(0);
            }
        }

        void recordFailure() {
            State currentState = state.get();

            if (currentState == State.HALF_OPEN) {
                // Failure in HALF_OPEN → back to OPEN
                if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    openedAt = Instant.now();
                    log.warn("Circuit breaker for market transitioned back to OPEN after HALF_OPEN failure");
                }
                return;
            }

            if (currentState == State.CLOSED) {
                int failures = failureCount.incrementAndGet();
                if (failures >= failureThreshold) {
                    if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                        openedAt = Instant.now();
                        log.warn("Circuit breaker for market opened after {} failures", failures);
                    }
                }
            }
        }

        State getState() {
            // Check if OPEN state should transition to HALF_OPEN
            if (state.get() == State.OPEN && openedAt != null &&
                    Instant.now().isAfter(openedAt.plusMillis(openDurationMs))) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenSuccessCount.set(0);
                }
            }
            return state.get();
        }

        void reset() {
            state.set(State.CLOSED);
            failureCount.set(0);
            halfOpenSuccessCount.set(0);
            openedAt = null;
        }
    }

    /**
     * Create circuit breaker with default settings.
     * - failureThreshold: 5
     * - openDurationMs: 30000 (30 seconds)
     * - halfOpenSuccessThreshold: 3
     */
    public static MarketCircuitBreaker createDefault() {
        return new MarketCircuitBreaker(5, 30000, 3);
    }
}
