package com.selfdevelopment.ai.messaging.statemachine.resilience;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ResilientStateMachine} and failure handlers.
 */
class ResilienceTest {

    enum TestState { A, B, ERROR }
    enum TestEvent { GO, INVALID, FAIL }

    private StateMachine<TestState, TestEvent, Void> machine;

    @BeforeEach
    void setUp() {
        machine = StateMachineBuilder.<TestState, TestEvent, Void>builder("test")
                .initialState(TestState.A)
                .transition()
                    .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                .and()
                .transition()
                    .from(TestState.A).on(TestEvent.FAIL).to(TestState.B)
                    .perform(ctx -> { throw new RuntimeException("action failed"); })
                .and()
                .build();
    }

    // ===== ThrowFailureHandler tests =====

    @Test
    void shouldThrowWithThrowHandler() {
        ResilientStateMachine<TestState, TestEvent, Void> resilient =
                new ResilientStateMachine<>(machine, new ThrowFailureHandler<>());

        assertThrows(StateMachineException.class, () ->
                resilient.fireEvent(TestState.A, TestEvent.INVALID, null));
    }

    @Test
    void shouldSucceedWithThrowHandler() {
        ResilientStateMachine<TestState, TestEvent, Void> resilient =
                new ResilientStateMachine<>(machine, new ThrowFailureHandler<>());

        StateContext<TestState, TestEvent, Void> result =
                resilient.fireEvent(TestState.A, TestEvent.GO, null);
        assertEquals(TestState.B, result.getTargetState());
    }

    // ===== ReturnSourceFailureHandler tests =====

    @Test
    void shouldReturnSourceStateOnFailure() {
        ResilientStateMachine<TestState, TestEvent, Void> resilient =
                new ResilientStateMachine<>(machine, new ReturnSourceFailureHandler<>());

        StateContext<TestState, TestEvent, Void> result =
                resilient.fireEvent(TestState.A, TestEvent.INVALID, null);

        assertEquals(TestState.A, result.getTargetState());
        assertFalse(result.isTransitionAccepted());
    }

    @Test
    void shouldReturnSourceStateOnActionError() {
        ResilientStateMachine<TestState, TestEvent, Void> resilient =
                new ResilientStateMachine<>(machine, new ReturnSourceFailureHandler<>());

        StateContext<TestState, TestEvent, Void> result =
                resilient.fireEvent(TestState.A, TestEvent.FAIL, null);

        assertEquals(TestState.A, result.getTargetState());
        assertFalse(result.isTransitionAccepted());
        assertNotNull(result.getException());
    }

    // ===== FallbackStateFailureHandler tests =====

    @Test
    void shouldTransitionToFallbackState() {
        FallbackStateFailureHandler<TestState, TestEvent, Void> fallback =
                new FallbackStateFailureHandler<>(TestState.ERROR);

        ResilientStateMachine<TestState, TestEvent, Void> resilient =
                new ResilientStateMachine<>(machine, fallback);

        StateContext<TestState, TestEvent, Void> result =
                resilient.fireEvent(TestState.A, TestEvent.INVALID, null);

        assertEquals(TestState.ERROR, result.getTargetState());
        assertTrue(result.isTransitionAccepted());  // accepted as fallback
    }

    @Test
    void shouldReturnFallbackState() {
        FallbackStateFailureHandler<TestState, TestEvent, Void> fallback =
                new FallbackStateFailureHandler<>(TestState.ERROR);
        assertEquals(TestState.ERROR, fallback.getFallbackState());
    }

    // ===== RetryFailureHandler tests =====

    @Test
    void shouldRetryThenFallbackToThrow() {
        AtomicInteger attempts = new AtomicInteger(0);

        // Create a machine that always fails on first N attempts
        StateMachine<TestState, TestEvent, Void> flakyMachine =
                StateMachineBuilder.<TestState, TestEvent, Void>builder("flaky")
                    .transition()
                        .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                        .perform(ctx -> {
                            attempts.incrementAndGet();
                            throw new RuntimeException("transient failure");
                        })
                    .and()
                    .build();

        RetryFailureHandler<TestState, TestEvent, Void> retryHandler =
                RetryFailureHandler.fixedDelay(2, new ThrowFailureHandler<>(), 0);

        ResilientStateMachine<TestState, TestEvent, Void> resilient =
                new ResilientStateMachine<>(flakyMachine, retryHandler);

        assertThrows(StateMachineException.class, () ->
                resilient.fireEvent(TestState.A, TestEvent.GO, null));

        // Initial attempt + 2 retries = 3 total
        assertEquals(3, attempts.get());
    }

    @Test
    void shouldSucceedOnRetry() {
        AtomicInteger attempts = new AtomicInteger(0);

        StateMachine<TestState, TestEvent, Void> flakyMachine =
                StateMachineBuilder.<TestState, TestEvent, Void>builder("flaky")
                    .transition()
                        .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                        .perform(ctx -> {
                            if (attempts.incrementAndGet() < 2) {
                                throw new RuntimeException("transient failure");
                            }
                        })
                    .and()
                    .build();

        RetryFailureHandler<TestState, TestEvent, Void> retryHandler =
                RetryFailureHandler.fixedDelay(3, new ThrowFailureHandler<>(), 0);

        ResilientStateMachine<TestState, TestEvent, Void> resilient =
                new ResilientStateMachine<>(flakyMachine, retryHandler);

        StateContext<TestState, TestEvent, Void> result =
                resilient.fireEvent(TestState.A, TestEvent.GO, null);

        assertEquals(TestState.B, result.getTargetState());
        assertEquals(2, attempts.get());  // succeeded on 2nd attempt
    }

    @Test
    void shouldCreateExponentialBackoffHandler() {
        RetryFailureHandler<TestState, TestEvent, Void> handler =
                RetryFailureHandler.exponentialBackoff(3, new ThrowFailureHandler<>(), 100, 5000);

        assertEquals(3, handler.getMaxRetries());
        assertEquals(100, handler.getBackoffDelay(1, null));
        assertEquals(200, handler.getBackoffDelay(2, null));
        assertEquals(400, handler.getBackoffDelay(3, null));
    }

    @Test
    void shouldThrowOnNegativeMaxRetries() {
        assertThrows(IllegalArgumentException.class, () ->
                new RetryFailureHandler<>(-1, new ThrowFailureHandler<>()));
    }

    // ===== ResilientStateMachine delegation tests =====

    @Test
    void shouldDelegateLifecycleAndQueryMethods() {
        ResilientStateMachine<TestState, TestEvent, Void> resilient =
                new ResilientStateMachine<>(machine, new ThrowFailureHandler<>());

        assertFalse(resilient.isStarted());
        resilient.start();
        assertTrue(resilient.isStarted());
        resilient.stop();

        assertTrue(resilient.hasTransition(TestState.A, TestEvent.GO));
        assertEquals(2, resilient.getTransitionCount());
        assertEquals("test", resilient.getMachineId());
        assertNotNull(resilient.getFailureHandler());
    }
}
