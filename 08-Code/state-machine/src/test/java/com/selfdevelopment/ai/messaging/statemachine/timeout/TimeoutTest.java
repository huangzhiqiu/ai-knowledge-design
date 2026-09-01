package com.selfdevelopment.ai.messaging.statemachine.timeout;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for timeout scheduling and TimeoutAwareStateMachine.
 */
class TimeoutTest {

    enum TestState { IDLE, ACTIVE, PROCESSING, CLOSED, ERROR }
    enum TestEvent { START, PROCESS, COMPLETE, TIMEOUT, CANCEL, CLOSE }

    private InMemoryTimeoutScheduler<TestState, TestEvent> scheduler;
    private StateMachine<TestState, TestEvent, Void> machine;

    @BeforeEach
    void setUp() {
        scheduler = new InMemoryTimeoutScheduler<>("test-timeout", 2);
        machine = StateMachineBuilder.<TestState, TestEvent, Void>builder("test")
                .initialState(TestState.IDLE)
                .endStates(TestState.CLOSED)
                .transition()
                    .from(TestState.IDLE).on(TestEvent.START).to(TestState.ACTIVE)
                .and()
                .transition()
                    .from(TestState.ACTIVE).on(TestEvent.PROCESS).to(TestState.PROCESSING)
                .and()
                .transition()
                    .from(TestState.ACTIVE).on(TestEvent.TIMEOUT).to(TestState.CLOSED)
                .and()
                .transition()
                    .from(TestState.PROCESSING).on(TestEvent.COMPLETE).to(TestState.CLOSED)
                .and()
                .transition()
                    .from(TestState.PROCESSING).on(TestEvent.TIMEOUT).to(TestState.ERROR)
                .and()
                .transition()
                    .from(TestState.ACTIVE).on(TestEvent.CANCEL).to(TestState.CLOSED)
                .and()
                .build();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    // ===== TimeoutConfig tests =====

    @Test
    void shouldCreateTimeoutConfig() {
        TimeoutConfig<TestState, TestEvent> config = TimeoutConfig.<TestState, TestEvent>builder()
                .state(TestState.ACTIVE)
                .timeoutEvent(TestEvent.TIMEOUT)
                .duration(5)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        assertEquals(TestState.ACTIVE, config.getState());
        assertEquals(TestEvent.TIMEOUT, config.getTimeoutEvent());
        assertEquals(5, config.getDuration());
        assertEquals(TimeUnit.SECONDS, config.getTimeUnit());
        assertEquals(5000, config.getDurationMs());
        assertFalse(config.isRepeat());
    }

    @Test
    void shouldCreateRepeatingTimeoutConfig() {
        TimeoutConfig<TestState, TestEvent> config = TimeoutConfig.<TestState, TestEvent>builder()
                .state(TestState.ACTIVE)
                .timeoutEvent(TestEvent.TIMEOUT)
                .duration(100)
                .timeUnit(TimeUnit.MILLISECONDS)
                .repeat(true)
                .build();

        assertTrue(config.isRepeat());
    }

    @Test
    void shouldThrowOnInvalidConfig() {
        assertThrows(NullPointerException.class, () ->
                TimeoutConfig.<TestState, TestEvent>builder()
                        .timeoutEvent(TestEvent.TIMEOUT)
                        .duration(5)
                        .build());

        assertThrows(IllegalArgumentException.class, () ->
                TimeoutConfig.<TestState, TestEvent>builder()
                        .state(TestState.ACTIVE)
                        .timeoutEvent(TestEvent.TIMEOUT)
                        .duration(0)
                        .build());
    }

    // ===== InMemoryTimeoutScheduler tests =====

    @Test
    void shouldScheduleAndFireTimeout() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger firedCount = new AtomicInteger(0);

        TimeoutConfig<TestState, TestEvent> config = TimeoutConfig.<TestState, TestEvent>builder()
                .state(TestState.ACTIVE)
                .timeoutEvent(TestEvent.TIMEOUT)
                .duration(100)
                .timeUnit(TimeUnit.MILLISECONDS)
                .build();

        scheduler.schedule("entity-1", config, (id, event) -> {
            firedCount.incrementAndGet();
            latch.countDown();
        });

        assertTrue(scheduler.isScheduled("entity-1"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, firedCount.get());
        assertFalse(scheduler.isScheduled("entity-1"));  // one-shot, removed after fire
    }

    @Test
    void shouldCancelTimeout() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger firedCount = new AtomicInteger(0);

        TimeoutConfig<TestState, TestEvent> config = TimeoutConfig.<TestState, TestEvent>builder()
                .state(TestState.ACTIVE)
                .timeoutEvent(TestEvent.TIMEOUT)
                .duration(200)
                .timeUnit(TimeUnit.MILLISECONDS)
                .build();

        scheduler.schedule("entity-1", config, (id, event) -> {
            firedCount.incrementAndGet();
            latch.countDown();
        });

        scheduler.cancel("entity-1");
        assertFalse(scheduler.isScheduled("entity-1"));

        // Wait longer than the timeout to ensure it doesn't fire
        assertFalse(latch.await(500, TimeUnit.MILLISECONDS));
        assertEquals(0, firedCount.get());
    }

    @Test
    void shouldReplaceExistingTimeoutOnSchedule() {
        TimeoutConfig<TestState, TestEvent> config1 = TimeoutConfig.<TestState, TestEvent>builder()
                .state(TestState.ACTIVE)
                .timeoutEvent(TestEvent.TIMEOUT)
                .duration(500)
                .timeUnit(TimeUnit.MILLISECONDS)
                .build();

        TimeoutConfig<TestState, TestEvent> config2 = TimeoutConfig.<TestState, TestEvent>builder()
                .state(TestState.PROCESSING)
                .timeoutEvent(TestEvent.TIMEOUT)
                .duration(100)
                .timeUnit(TimeUnit.MILLISECONDS)
                .build();

        scheduler.schedule("entity-1", config1, (id, event) -> {});
        scheduler.schedule("entity-1", config2, (id, event) -> {});

        // Should have only the second timeout (shorter)
        assertTrue(scheduler.isScheduled("entity-1"));
        assertTrue(scheduler.getRemainingMs("entity-1") <= 100);
    }

    @Test
    void shouldReportRemainingTime() {
        TimeoutConfig<TestState, TestEvent> config = TimeoutConfig.<TestState, TestEvent>builder()
                .state(TestState.ACTIVE)
                .timeoutEvent(TestEvent.TIMEOUT)
                .duration(1000)
                .timeUnit(TimeUnit.MILLISECONDS)
                .build();

        scheduler.schedule("entity-1", config, (id, event) -> {});

        long remaining = scheduler.getRemainingMs("entity-1");
        assertTrue(remaining > 0 && remaining <= 1000);
    }

    @Test
    void shouldReturnMinusOneForNoTimeout() {
        assertEquals(-1, scheduler.getRemainingMs("nonexistent"));
        assertFalse(scheduler.isScheduled("nonexistent"));
    }

    @Test
    void shouldFireRepeatingTimeout() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger firedCount = new AtomicInteger(0);

        TimeoutConfig<TestState, TestEvent> config = TimeoutConfig.<TestState, TestEvent>builder()
                .state(TestState.ACTIVE)
                .timeoutEvent(TestEvent.TIMEOUT)
                .duration(50)
                .timeUnit(TimeUnit.MILLISECONDS)
                .repeat(true)
                .build();

        scheduler.schedule("entity-1", config, (id, event) -> {
            firedCount.incrementAndGet();
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(firedCount.get() >= 3);
        assertTrue(scheduler.isScheduled("entity-1"));  // repeating, still scheduled

        scheduler.cancel("entity-1");
    }

    // ===== TimeoutAwareStateMachine tests =====

    @Test
    void shouldAutoScheduleTimeoutOnEnteringState() {
        Map<TestState, TimeoutConfig<TestState, TestEvent>> timeouts = Map.of(
                TestState.ACTIVE, TimeoutConfig.<TestState, TestEvent>builder()
                        .state(TestState.ACTIVE)
                        .timeoutEvent(TestEvent.TIMEOUT)
                        .duration(500)
                        .timeUnit(TimeUnit.MILLISECONDS)
                        .build()
        );

        TimeoutAwareStateMachine<TestState, TestEvent, Void> timeoutAware =
                new TimeoutAwareStateMachine<>(machine, scheduler, timeouts, "conv-1");

        // Enter ACTIVE state
        StateContext<TestState, TestEvent, Void> result =
                timeoutAware.fireEvent(TestState.IDLE, TestEvent.START, null);

        assertEquals(TestState.ACTIVE, result.getTargetState());
        assertTrue(timeoutAware.isTimeoutActive());
        assertTrue(timeoutAware.getRemainingTimeoutMs() > 0);
    }

    @Test
    void shouldAutoCancelTimeoutOnLeavingState() {
        Map<TestState, TimeoutConfig<TestState, TestEvent>> timeouts = Map.of(
                TestState.ACTIVE, TimeoutConfig.<TestState, TestEvent>builder()
                        .state(TestState.ACTIVE)
                        .timeoutEvent(TestEvent.TIMEOUT)
                        .duration(500)
                        .timeUnit(TimeUnit.MILLISECONDS)
                        .build()
        );

        TimeoutAwareStateMachine<TestState, TestEvent, Void> timeoutAware =
                new TimeoutAwareStateMachine<>(machine, scheduler, timeouts, "conv-1");

        // Enter ACTIVE
        timeoutAware.fireEvent(TestState.IDLE, TestEvent.START, null);
        assertTrue(timeoutAware.isTimeoutActive());

        // Leave ACTIVE -> PROCESSING
        timeoutAware.fireEvent(TestState.ACTIVE, TestEvent.PROCESS, null);
        assertFalse(timeoutAware.isTimeoutActive());  // cancelled because PROCESSING has no timeout
    }

    @Test
    void shouldAutoFireTimeoutEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Map<TestState, TimeoutConfig<TestState, TestEvent>> timeouts = Map.of(
                TestState.ACTIVE, TimeoutConfig.<TestState, TestEvent>builder()
                        .state(TestState.ACTIVE)
                        .timeoutEvent(TestEvent.TIMEOUT)
                        .duration(100)
                        .timeUnit(TimeUnit.MILLISECONDS)
                        .build()
        );

        // Add a listener to detect the timeout transition
        machine.addListener(new com.selfdevelopment.ai.messaging.statemachine.listener.StateMachineListener<>() {
            @Override
            public void stateChanged(StateContext<TestState, TestEvent, Void> context) {
                if (context.getEvent() == TestEvent.TIMEOUT) {
                    latch.countDown();
                }
            }
        });

        TimeoutAwareStateMachine<TestState, TestEvent, Void> timeoutAware =
                new TimeoutAwareStateMachine<>(machine, scheduler, timeouts, "conv-1");

        // Enter ACTIVE
        timeoutAware.fireEvent(TestState.IDLE, TestEvent.START, null);

        // Wait for timeout to fire automatically
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void shouldNotScheduleTimeoutForStateWithoutConfig() {
        Map<TestState, TimeoutConfig<TestState, TestEvent>> timeouts = Map.of(
                TestState.ACTIVE, TimeoutConfig.<TestState, TestEvent>builder()
                        .state(TestState.ACTIVE)
                        .timeoutEvent(TestEvent.TIMEOUT)
                        .duration(500)
                        .timeUnit(TimeUnit.MILLISECONDS)
                        .build()
        );

        TimeoutAwareStateMachine<TestState, TestEvent, Void> timeoutAware =
                new TimeoutAwareStateMachine<>(machine, scheduler, timeouts, "conv-1");

        // Enter PROCESSING (no timeout config)
        timeoutAware.fireEvent(TestState.IDLE, TestEvent.START, null);  // IDLE -> ACTIVE
        timeoutAware.fireEvent(TestState.ACTIVE, TestEvent.PROCESS, null);  // ACTIVE -> PROCESSING

        assertFalse(timeoutAware.isTimeoutActive());
    }

    @Test
    void shouldManuallyCancelTimeout() {
        Map<TestState, TimeoutConfig<TestState, TestEvent>> timeouts = Map.of(
                TestState.ACTIVE, TimeoutConfig.<TestState, TestEvent>builder()
                        .state(TestState.ACTIVE)
                        .timeoutEvent(TestEvent.TIMEOUT)
                        .duration(500)
                        .timeUnit(TimeUnit.MILLISECONDS)
                        .build()
        );

        TimeoutAwareStateMachine<TestState, TestEvent, Void> timeoutAware =
                new TimeoutAwareStateMachine<>(machine, scheduler, timeouts, "conv-1");

        timeoutAware.fireEvent(TestState.IDLE, TestEvent.START, null);
        assertTrue(timeoutAware.isTimeoutActive());

        timeoutAware.cancelTimeout();
        assertFalse(timeoutAware.isTimeoutActive());
    }

    @Test
    void shouldScheduleTimeoutWhenEnteringEndState() {
        // End states should not schedule timeouts (transition accepted but no config)
        Map<TestState, TimeoutConfig<TestState, TestEvent>> timeouts = Map.of();

        TimeoutAwareStateMachine<TestState, TestEvent, Void> timeoutAware =
                new TimeoutAwareStateMachine<>(machine, scheduler, timeouts, "conv-1");

        timeoutAware.fireEvent(TestState.IDLE, TestEvent.START, null);
        timeoutAware.fireEvent(TestState.ACTIVE, TestEvent.CANCEL, null);  // -> CLOSED

        assertFalse(timeoutAware.isTimeoutActive());
    }

    @Test
    void shouldHandleNullTimeoutConfigsGracefully() {
        TimeoutAwareStateMachine<TestState, TestEvent, Void> timeoutAware =
                new TimeoutAwareStateMachine<>(machine, scheduler, null, "conv-1");

        StateContext<TestState, TestEvent, Void> result =
                timeoutAware.fireEvent(TestState.IDLE, TestEvent.START, null);

        assertEquals(TestState.ACTIVE, result.getTargetState());
        assertFalse(timeoutAware.isTimeoutActive());
    }

    @Test
    void shouldShutdownScheduler() {
        InMemoryTimeoutScheduler<TestState, TestEvent> s = new InMemoryTimeoutScheduler<>();
        TimeoutConfig<TestState, TestEvent> config = TimeoutConfig.<TestState, TestEvent>builder()
                .state(TestState.ACTIVE)
                .timeoutEvent(TestEvent.TIMEOUT)
                .duration(1000)
                .timeUnit(TimeUnit.MILLISECONDS)
                .build();

        s.schedule("e1", config, (id, event) -> {});
        assertEquals(1, s.scheduledCount());

        s.shutdown();
        assertEquals(0, s.scheduledCount());
    }
}
