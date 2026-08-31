package com.selfdevelopment.ai.messaging.statemachine.core;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SimpleStateMachine} and the builder DSL.
 */
class SimpleStateMachineTest {

    enum LightState { RED, GREEN, YELLOW }
    enum LightEvent { TIMER, PEDESTRIAN_BUTTON }

    private StateMachine<LightState, LightEvent, Void> trafficLight;

    @BeforeEach
    void setUp() {
        trafficLight = StateMachineBuilder.<LightState, LightEvent, Void>builder("traffic-light")
                .transition()
                    .from(LightState.RED)
                    .on(LightEvent.TIMER)
                    .to(LightState.GREEN)
                .and()
                .transition()
                    .from(LightState.GREEN)
                    .on(LightEvent.TIMER)
                    .to(LightState.YELLOW)
                .and()
                .transition()
                    .from(LightState.YELLOW)
                    .on(LightEvent.TIMER)
                    .to(LightState.RED)
                .and()
                .build();
    }

    @Test
    void shouldTransitionRedToGreenOnTimer() {
        LightState result = trafficLight.fireEvent(LightState.RED, LightEvent.TIMER, null);
        assertEquals(LightState.GREEN, result);
    }

    @Test
    void shouldTransitionGreenToYellowOnTimer() {
        LightState result = trafficLight.fireEvent(LightState.GREEN, LightEvent.TIMER, null);
        assertEquals(LightState.YELLOW, result);
    }

    @Test
    void shouldTransitionYellowToRedOnTimer() {
        LightState result = trafficLight.fireEvent(LightState.YELLOW, LightEvent.TIMER, null);
        assertEquals(LightState.RED, result);
    }

    @Test
    void shouldThrowWhenNoTransitionExists() {
        assertThrows(StateMachineException.class,
                () -> trafficLight.fireEvent(LightState.RED, LightEvent.PEDESTRIAN_BUTTON, null));
    }

    @Test
    void shouldReportHasTransition() {
        assertTrue(trafficLight.hasTransition(LightState.RED, LightEvent.TIMER));
        assertFalse(trafficLight.hasTransition(LightState.RED, LightEvent.PEDESTRIAN_BUTTON));
    }

    @Test
    void shouldReportCanFire() {
        assertTrue(trafficLight.canFire(LightState.RED, LightEvent.TIMER, null));
        assertFalse(trafficLight.canFire(LightState.RED, LightEvent.PEDESTRIAN_BUTTON, null));
    }

    @Test
    void shouldReturnTransitionCount() {
        assertEquals(3, trafficLight.getTransitionCount());
    }

    @Test
    void shouldExecuteActionOnTransition() {
        List<String> log = new ArrayList<>();
        StateMachine<LightState, LightEvent, Void> machine =
                StateMachineBuilder.<LightState, LightEvent, Void>builder("action-test")
                    .transition()
                        .from(LightState.RED)
                        .on(LightEvent.TIMER)
                        .to(LightState.GREEN)
                        .perform(ctx -> log.add("RED->GREEN"))
                    .and()
                    .build();

        machine.fireEvent(LightState.RED, LightEvent.TIMER, null);
        assertEquals(1, log.size());
        assertEquals("RED->GREEN", log.get(0));
    }

    @Test
    void shouldRespectGuardCondition() {
        StateMachine<LightState, LightEvent, GuardContext> machine =
                StateMachineBuilder.<LightState, LightEvent, GuardContext>builder("guard-test")
                    .transition()
                        .from(LightState.RED)
                        .on(LightEvent.TIMER)
                        .to(LightState.GREEN)
                        .when(GuardContext::allowed)
                    .and()
                    .build();

        // Condition satisfied
        assertEquals(LightState.GREEN,
                machine.fireEvent(LightState.RED, LightEvent.TIMER, new GuardContext(true)));

        // Condition not satisfied -> exception
        assertThrows(StateMachineException.class,
                () -> machine.fireEvent(LightState.RED, LightEvent.TIMER, new GuardContext(false)));
    }

    private record GuardContext(boolean allowed) {}

    @Test
    void shouldSupportMultipleTransitionsFromSameStateWithDifferentEvents() {
        StateMachine<LightState, LightEvent, Void> machine =
                StateMachineBuilder.<LightState, LightEvent, Void>builder("multi-test")
                    .transition()
                        .from(LightState.RED)
                        .on(LightEvent.TIMER)
                        .to(LightState.GREEN)
                    .and()
                    .transition()
                        .from(LightState.RED)
                        .on(LightEvent.PEDESTRIAN_BUTTON)
                        .to(LightState.YELLOW)
                    .and()
                    .build();

        assertEquals(LightState.GREEN, machine.fireEvent(LightState.RED, LightEvent.TIMER, null));
        assertEquals(LightState.YELLOW, machine.fireEvent(LightState.RED, LightEvent.PEDESTRIAN_BUTTON, null));
    }

    @Test
    void shouldThrowWhenBuildingWithNoTransitions() {
        assertThrows(StateMachineException.class,
                () -> StateMachineBuilder.<LightState, LightEvent, Void>builder("empty").build());
    }

    @Test
    void shouldBeThreadSafeUnderConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int iterations = 1000;
        Thread[] threads = new Thread[threadCount];
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    LightState result = trafficLight.fireEvent(LightState.RED, LightEvent.TIMER, null);
                    if (result == LightState.GREEN) {
                        successCount.incrementAndGet();
                    }
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(threadCount * iterations, successCount.get());
    }
}
