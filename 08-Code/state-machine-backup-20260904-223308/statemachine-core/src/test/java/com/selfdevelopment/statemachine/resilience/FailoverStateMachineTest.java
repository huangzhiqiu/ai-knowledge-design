package com.selfdevelopment.statemachine.resilience;

import com.selfdevelopment.statemachine.resilience.impl.ThrowFailureHandler;

import com.selfdevelopment.statemachine.resilience.impl.RetryFailureHandler;

import com.selfdevelopment.statemachine.resilience.impl.ResilientStateMachine;

import com.selfdevelopment.statemachine.resilience.impl.FailoverStateMachine;

import com.selfdevelopment.statemachine.core.Transition;

import com.selfdevelopment.statemachine.api.Action;

import com.selfdevelopment.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.exception.StateMachineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FailoverStateMachine}.
 * <p>
 * Failover: when an action throws an unhandled RuntimeException, automatically
 * generate a fail event and re-fire it through the state machine.
 */
class FailoverStateMachineTest {

    enum TestState { A, B, ERROR, CLOSED }
    enum TestEvent { GO, FAIL_ACTION, NO_TRANSITION, FAIL_EVENT, FAIL_EVENT_WITH_ACTION, RECOVER, ABORT }

    private StateMachine<TestState, TestEvent, Void> machine;

    @BeforeEach
    void setUp() {
        machine = StateMachineBuilder.<TestState, TestEvent, Void>builder("failover-test")
                .initialState(TestState.A)
                .transition()
                    .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                .and()
                .transition()
                    .from(TestState.A).on(TestEvent.FAIL_ACTION).to(TestState.B)
                    .perform(ctx -> { throw new RuntimeException("simulated action failure"); })
                .and()
                // Failover target: ERROR state (no action, clean transition)
                .transition()
                    .from(TestState.A).on(TestEvent.FAIL_EVENT).to(TestState.ERROR)
                .and()
                .transition()
                    .from(TestState.B).on(TestEvent.FAIL_EVENT).to(TestState.ERROR)
                .and()
                // Fail event that itself has a failing action (for loop prevention test)
                .transition()
                    .from(TestState.A).on(TestEvent.FAIL_EVENT_WITH_ACTION).to(TestState.ERROR)
                    .perform(ctx -> { throw new RuntimeException("fail event action also failed"); })
                .and()
                .transition()
                    .from(TestState.ERROR).on(TestEvent.RECOVER).to(TestState.A)
                .and()
                .transition()
                    .from(TestState.ERROR).on(TestEvent.ABORT).to(TestState.CLOSED)
                .and()
                .build();
    }

    private FailoverStateMachine<TestState, TestEvent, Void> createFailover() {
        return new FailoverStateMachine<>(
                machine,
                ctx -> TestEvent.FAIL_EVENT,
                event -> event == TestEvent.FAIL_EVENT
        );
    }

    // ===== Basic failover tests =====

    @Test
    void shouldFailoverToErrorStateOnActionException() {
        FailoverStateMachine<TestState, TestEvent, Void> failover = createFailover();

        StateContext<TestState, TestEvent, Void> result =
                failover.fireEvent(TestState.A, TestEvent.FAIL_ACTION, null);

        // Original action throws → fail event fired → transitions to ERROR
        assertEquals(TestState.ERROR, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    @Test
    void shouldPassThroughNormalEvents() {
        FailoverStateMachine<TestState, TestEvent, Void> failover = createFailover();

        StateContext<TestState, TestEvent, Void> result =
                failover.fireEvent(TestState.A, TestEvent.GO, null);

        assertEquals(TestState.B, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    // ===== Loop prevention tests =====

    @Test
    void shouldNotFailoverOnFailEventItself() {
        // Use a failover that treats both FAIL_EVENT and FAIL_EVENT_WITH_ACTION as fail events
        FailoverStateMachine<TestState, TestEvent, Void> failover = new FailoverStateMachine<>(
                machine,
                ctx -> TestEvent.FAIL_EVENT_WITH_ACTION,
                event -> event == TestEvent.FAIL_EVENT || event == TestEvent.FAIL_EVENT_WITH_ACTION
        );

        // FAIL_EVENT_WITH_ACTION's action throws, but it's a fail event so no further failover
        assertThrows(Exception.class, () ->
                failover.fireEvent(TestState.A, TestEvent.FAIL_EVENT_WITH_ACTION, null));
    }

    // ===== StateMachineException (logical error) tests =====

    @Test
    void shouldNotFailoverOnNoTransition() {
        FailoverStateMachine<TestState, TestEvent, Void> failover = createFailover();

        // NO_TRANSITION event doesn't exist in state A → StateMachineException (logical error)
        assertThrows(StateMachineException.class, () ->
                failover.fireEvent(TestState.A, TestEvent.NO_TRANSITION, null));
    }

    // ===== FailoverContext tests =====

    @Test
    void shouldProvideFailoverContextToEventProvider() {
        final FailoverContext<TestState, TestEvent, Void>[] captured = new FailoverContext[1];

        FailoverStateMachine<TestState, TestEvent, Void> failover = new FailoverStateMachine<>(
                machine,
                ctx -> {
                    captured[0] = ctx;
                    return TestEvent.FAIL_EVENT;
                },
                event -> event == TestEvent.FAIL_EVENT
        );

        failover.fireEvent(TestState.A, TestEvent.FAIL_ACTION, null);

        assertNotNull(captured[0]);
        assertEquals(TestState.A, captured[0].sourceState());
        assertEquals(TestEvent.FAIL_ACTION, captured[0].originalEvent());
        assertNotNull(captured[0].cause());
        assertEquals("simulated action failure", captured[0].causeMessage());
        assertEquals("RuntimeException", captured[0].causeType());
    }

    // ===== Recovery from ERROR state tests =====

    @Test
    void shouldRecoverFromErrorState() {
        FailoverStateMachine<TestState, TestEvent, Void> failover = createFailover();

        // 1. Action fails → ERROR
        StateContext<TestState, TestEvent, Void> failed =
                failover.fireEvent(TestState.A, TestEvent.FAIL_ACTION, null);
        assertEquals(TestState.ERROR, failed.getTargetState());

        // 2. Recover from ERROR → A
        StateContext<TestState, TestEvent, Void> recovered =
                failover.fireEvent(TestState.ERROR, TestEvent.RECOVER, null);
        assertEquals(TestState.A, recovered.getTargetState());
    }

    @Test
    void shouldAbortFromErrorState() {
        FailoverStateMachine<TestState, TestEvent, Void> failover = createFailover();

        failover.fireEvent(TestState.A, TestEvent.FAIL_ACTION, null);

        StateContext<TestState, TestEvent, Void> aborted =
                failover.fireEvent(TestState.ERROR, TestEvent.ABORT, null);
        assertEquals(TestState.CLOSED, aborted.getTargetState());
    }

    // ===== Delegated methods tests =====

    @Test
    void shouldDelegateMachineId() {
        FailoverStateMachine<TestState, TestEvent, Void> failover = createFailover();
        assertEquals("failover-test", failover.getMachineId());
    }

    @Test
    void shouldDelegateTransitionCount() {
        FailoverStateMachine<TestState, TestEvent, Void> failover = createFailover();
        assertTrue(failover.getTransitionCount() > 0);
    }

    @Test
    void shouldReturnDelegate() {
        FailoverStateMachine<TestState, TestEvent, Void> failover = createFailover();
        assertSame(machine, failover.getDelegate());
    }

    // ===== Combined with ResilientStateMachine (retry then failover) =====

    @Test
    void shouldRetryThenFailover() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        StateMachine<TestState, TestEvent, Void> retryMachine =
                StateMachineBuilder.<TestState, TestEvent, Void>builder("retry-failover")
                        .initialState(TestState.A)
                        .transition()
                            .from(TestState.A).on(TestEvent.FAIL_ACTION).to(TestState.B)
                            .perform(ctx -> {
                                attemptCount.incrementAndGet();
                                throw new RuntimeException("always fails");
                            })
                        .and()
                        .transition()
                            .from(TestState.A).on(TestEvent.FAIL_EVENT).to(TestState.ERROR)
                        .and()
                        .build();

        // Retry 2 times, then failover
        RetryFailureHandler<TestState, TestEvent, Void> retryHandler =
                RetryFailureHandler.exponentialBackoff(2, new ThrowFailureHandler<>(), 0, 0);

        ResilientStateMachine<TestState, TestEvent, Void> resilient =
                new ResilientStateMachine<>(retryMachine, retryHandler);

        FailoverStateMachine<TestState, TestEvent, Void> failover = new FailoverStateMachine<>(
                resilient,
                ctx -> TestEvent.FAIL_EVENT,
                event -> event == TestEvent.FAIL_EVENT
        );

        StateContext<TestState, TestEvent, Void> result =
                failover.fireEvent(TestState.A, TestEvent.FAIL_ACTION, null);

        // 1 initial + 2 retries = 3 attempts, then failover to ERROR
        assertEquals(3, attemptCount.get());
        assertEquals(TestState.ERROR, result.getTargetState());
    }
}
