package com.selfdevelopment.ai.messaging.statemachine.metrics;

import com.selfdevelopment.ai.messaging.statemachine.core.Transition;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MonitoredStateMachine} and {@link StateMachineMetrics}.
 */
class MonitoredStateMachineTest {

    enum TestState { A, B, C }
    enum TestEvent { GO, BACK, INVALID }

    private SimpleMeterRegistry registry;
    private StateMachine<TestState, TestEvent, Void> machine;
    private MonitoredStateMachine<TestState, TestEvent, Void> monitored;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        machine = StateMachineBuilder.<TestState, TestEvent, Void>builder("test-machine")
                .initialState(TestState.A)
                .transition()
                    .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                .and()
                .transition()
                    .from(TestState.B).on(TestEvent.GO).to(TestState.C)
                .and()
                .build();
        monitored = new MonitoredStateMachine<>(machine, registry);
    }

    @Test
    void shouldRecordSuccessMetrics() {
        StateContext<TestState, TestEvent, Void> result =
                monitored.fireEvent(TestState.A, TestEvent.GO, null);

        assertEquals(TestState.B, result.getTargetState());

        // Verify success counter
        double successCount = registry.counter(
                StateMachineMetrics.METRIC_TRANSITION_SUCCESS,
                "machine", "test-machine",
                "from", "A", "to", "B", "event", "GO"
        ).count();
        assertEquals(1.0, successCount);

        // Verify event received counter
        double receivedCount = registry.counter(
                StateMachineMetrics.METRIC_EVENT_RECEIVED,
                "machine", "test-machine",
                "from", "A", "event", "GO"
        ).count();
        assertEquals(1.0, receivedCount);
    }

    @Test
    void shouldRecordDeniedMetrics() {
        assertThrows(StateMachineException.class, () ->
                monitored.fireEvent(TestState.A, TestEvent.INVALID, null));

        double deniedCount = registry.counter(
                StateMachineMetrics.METRIC_TRANSITION_DENIED,
                "machine", "test-machine",
                "from", "A", "event", "INVALID",
                "reason", "no_transition"
        ).count();
        assertEquals(1.0, deniedCount);
    }

    @Test
    void shouldRecordTimerForSuccessfulTransitions() {
        monitored.fireEvent(TestState.A, TestEvent.GO, null);

        // Timer should exist
        boolean timerExists = registry.getMeters().stream()
                .anyMatch(m -> m.getId().getName().equals(StateMachineMetrics.METRIC_TRANSITION_DURATION));
        assertTrue(timerExists);
    }

    @Test
    void shouldDelegateLifecycleMethods() {
        assertFalse(monitored.isStarted());
        monitored.start();
        assertTrue(monitored.isStarted());
        monitored.stop();
        assertFalse(monitored.isStarted());
    }

    @Test
    void shouldDelegateQueryMethods() {
        assertTrue(monitored.hasTransition(TestState.A, TestEvent.GO));
        assertFalse(monitored.hasTransition(TestState.A, TestEvent.BACK));
        assertTrue(monitored.canFire(TestState.A, TestEvent.GO, null));
        assertEquals(2, monitored.getTransitionCount());
        assertEquals("test-machine", monitored.getMachineId());
        assertEquals(TestState.A, monitored.getInitialState());
    }

    @Test
    void shouldReturnMetricsCollector() {
        assertNotNull(monitored.getMetrics());
    }

    @Test
    void shouldRecordMultipleTransitions() {
        monitored.fireEvent(TestState.A, TestEvent.GO, null);  // A -> B
        monitored.fireEvent(TestState.B, TestEvent.GO, null);  // B -> C

        double totalSuccess = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals(StateMachineMetrics.METRIC_TRANSITION_SUCCESS))
                .mapToDouble(m -> m.measure().iterator().next().getValue())
                .sum();
        assertEquals(2.0, totalSuccess);
    }

    @Test
    void shouldThrowOnNullDelegate() {
        assertThrows(NullPointerException.class, () ->
                new MonitoredStateMachine<>(null, registry));
    }

    @Test
    void shouldThrowOnNullRegistry() {
        assertThrows(NullPointerException.class, () ->
                new MonitoredStateMachine<>(machine, (SimpleMeterRegistry) null));
    }
}
