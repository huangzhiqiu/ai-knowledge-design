package com.selfdevelopment.ai.messaging.statemachine.core;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException;
import com.selfdevelopment.ai.messaging.statemachine.listener.StateMachineListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SimpleStateMachine} and the builder DSL.
 * Covers: basic transitions, guards, actions, internal transitions,
 * extended state, listeners, lifecycle, thread safety.
 */
class SimpleStateMachineTest {

    enum LightState { RED, GREEN, YELLOW }
    enum LightEvent { TIMER, PEDESTRIAN_BUTTON, BLINK }

    private StateMachine<LightState, LightEvent, Void> trafficLight;

    @BeforeEach
    void setUp() {
        trafficLight = StateMachineBuilder.<LightState, LightEvent, Void>builder("traffic-light")
                .initialState(LightState.RED)
                .endStates(LightState.RED)
                .transition()
                    .from(LightState.RED).on(LightEvent.TIMER).to(LightState.GREEN)
                .and()
                .transition()
                    .from(LightState.GREEN).on(LightEvent.TIMER).to(LightState.YELLOW)
                .and()
                .transition()
                    .from(LightState.YELLOW).on(LightEvent.TIMER).to(LightState.RED)
                .and()
                .build();
    }

    @Test
    void shouldTransitionRedToGreenOnTimer() {
        StateContext<LightState, LightEvent, Void> result =
                trafficLight.fireEvent(LightState.RED, LightEvent.TIMER, null);
        assertEquals(LightState.GREEN, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    @Test
    void shouldTransitionGreenToYellowOnTimer() {
        assertEquals(LightState.YELLOW,
                trafficLight.fireEvent(LightState.GREEN, LightEvent.TIMER, null).getTargetState());
    }

    @Test
    void shouldTransitionYellowToRedOnTimer() {
        assertEquals(LightState.RED,
                trafficLight.fireEvent(LightState.YELLOW, LightEvent.TIMER, null).getTargetState());
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
    void shouldReturnMachineId() {
        assertEquals("traffic-light", trafficLight.getMachineId());
    }

    @Test
    void shouldReturnInitialAndEndStates() {
        assertEquals(LightState.RED, trafficLight.getInitialState());
        assertTrue(trafficLight.getEndStates().contains(LightState.RED));
    }

    @Test
    void shouldExecuteActionOnTransition() {
        List<String> log = new ArrayList<>();
        StateMachine<LightState, LightEvent, Void> machine =
                StateMachineBuilder.<LightState, LightEvent, Void>builder("action-test")
                    .transition()
                        .from(LightState.RED).on(LightEvent.TIMER).to(LightState.GREEN)
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
                        .from(LightState.RED).on(LightEvent.TIMER).to(LightState.GREEN)
                        .guard(ctx -> ctx.getBusinessContext().allowed())
                    .and()
                    .build();

        assertEquals(LightState.GREEN,
                machine.fireEvent(LightState.RED, LightEvent.TIMER, new GuardContext(true)).getTargetState());

        assertThrows(StateMachineException.class,
                () -> machine.fireEvent(LightState.RED, LightEvent.TIMER, new GuardContext(false)));
    }

    private record GuardContext(boolean allowed) {}

    @Test
    void shouldSupportInternalTransition() {
        AtomicInteger blinkCount = new AtomicInteger(0);
        StateMachine<LightState, LightEvent, Void> machine =
                StateMachineBuilder.<LightState, LightEvent, Void>builder("internal-test")
                    .transition()
                        .from(LightState.GREEN).on(LightEvent.BLINK).to(LightState.GREEN)
                        .internal()
                        .perform(ctx -> blinkCount.incrementAndGet())
                    .and()
                    .build();

        StateContext<LightState, LightEvent, Void> result =
                machine.fireEvent(LightState.GREEN, LightEvent.BLINK, null);
        assertEquals(LightState.GREEN, result.getTargetState());
        assertEquals(1, blinkCount.get());
    }

    @Test
    void shouldShareExtendedStateAcrossTransitions() {
        StateMachine<LightState, LightEvent, Void> machine =
                StateMachineBuilder.<LightState, LightEvent, Void>builder("ext-state-test")
                    .transition()
                        .from(LightState.RED).on(LightEvent.TIMER).to(LightState.GREEN)
                        .perform(ctx -> ctx.getExtendedState().set("step", 1))
                    .and()
                    .transition()
                        .from(LightState.GREEN).on(LightEvent.TIMER).to(LightState.YELLOW)
                        .perform(ctx -> ctx.getExtendedState().set("step", 2))
                    .and()
                    .build();

        ExtendedState ext = new ExtendedState();
        machine.fireEvent(LightState.RED, LightEvent.TIMER, null, ext);
        assertEquals(Integer.valueOf(1), ext.<Integer>get("step"));

        machine.fireEvent(LightState.GREEN, LightEvent.TIMER, null, ext);
        assertEquals(Integer.valueOf(2), ext.<Integer>get("step"));
    }

    @Test
    void shouldNotifyListenersOnTransition() {
        List<String> events = new ArrayList<>();
        StateMachine<LightState, LightEvent, Void> machine =
                StateMachineBuilder.<LightState, LightEvent, Void>builder("listener-test")
                    .transition()
                        .from(LightState.RED).on(LightEvent.TIMER).to(LightState.GREEN)
                    .and()
                    .build();

        machine.addListener(new StateMachineListener<>() {
            @Override
            public void transitionStarted(Transition<LightState, LightEvent, Void> t,
                                          StateContext<LightState, LightEvent, Void> ctx) {
                events.add("started:" + t.getSourceState() + "->" + t.getTargetState());
            }
            @Override
            public void transitionEnded(Transition<LightState, LightEvent, Void> t,
                                        StateContext<LightState, LightEvent, Void> ctx) {
                events.add("ended:" + ctx.getTargetState());
            }
            @Override
            public void stateChanged(StateContext<LightState, LightEvent, Void> ctx) {
                events.add("stateChanged:" + ctx.getSourceState() + "->" + ctx.getTargetState());
            }
        });

        machine.fireEvent(LightState.RED, LightEvent.TIMER, null);
        assertEquals(3, events.size());
        assertTrue(events.get(0).startsWith("started:RED->GREEN"));
        assertEquals("ended:GREEN", events.get(1));
        assertEquals("stateChanged:RED->GREEN", events.get(2));
    }

    @Test
    void shouldNotifyListenerOnDeniedTransition() {
        List<String> denied = new ArrayList<>();
        StateMachine<LightState, LightEvent, GuardContext> machine =
                StateMachineBuilder.<LightState, LightEvent, GuardContext>builder("denied-test")
                    .transition()
                        .from(LightState.RED).on(LightEvent.TIMER).to(LightState.GREEN)
                        .guard(ctx -> ctx.getBusinessContext().allowed())
                    .and()
                    .build();

        machine.addListener(new StateMachineListener<>() {
            @Override
            public void transitionDenied(StateContext<LightState, LightEvent, GuardContext> ctx,
                                          String reason) {
                denied.add(reason);
            }
        });

        assertThrows(StateMachineException.class,
                () -> machine.fireEvent(LightState.RED, LightEvent.TIMER, new GuardContext(false)));
        assertEquals(1, denied.size());
        assertEquals("All guard conditions failed", denied.get(0));
    }

    @Test
    void shouldSupportLifecycle() {
        assertFalse(trafficLight.isStarted());
        trafficLight.start();
        assertTrue(trafficLight.isStarted());
        trafficLight.stop();
        assertFalse(trafficLight.isStarted());
    }

    @Test
    void shouldSupportMultipleTransitionsFromSameStateWithDifferentEvents() {
        StateMachine<LightState, LightEvent, Void> machine =
                StateMachineBuilder.<LightState, LightEvent, Void>builder("multi-test")
                    .transition()
                        .from(LightState.RED).on(LightEvent.TIMER).to(LightState.GREEN)
                    .and()
                    .transition()
                        .from(LightState.RED).on(LightEvent.PEDESTRIAN_BUTTON).to(LightState.YELLOW)
                    .and()
                    .build();

        assertEquals(LightState.GREEN, machine.fireEvent(LightState.RED, LightEvent.TIMER, null).getTargetState());
        assertEquals(LightState.YELLOW, machine.fireEvent(LightState.RED, LightEvent.PEDESTRIAN_BUTTON, null).getTargetState());
    }

    @Test
    void shouldAllowEmptyStateMachine() {
        StateMachine<LightState, LightEvent, Void> empty =
                StateMachineBuilder.<LightState, LightEvent, Void>builder("empty").build();
        assertEquals(0, empty.getTransitionCount());
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
                    StateContext<LightState, LightEvent, Void> result =
                            trafficLight.fireEvent(LightState.RED, LightEvent.TIMER, null);
                    if (result.getTargetState() == LightState.GREEN) {
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
