package com.selfdevelopment.statemachine.builder;

import com.selfdevelopment.statemachine.api.Guard;

import com.selfdevelopment.statemachine.core.Transition;

import com.selfdevelopment.statemachine.api.Action;

import com.selfdevelopment.statemachine.config.StateMachineConfigurerAdapter;
import com.selfdevelopment.statemachine.config.StateConfigurer;
import com.selfdevelopment.statemachine.config.TransitionConfigurer;
import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.exception.StateMachineException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StateMachineBuilder}.
 */
class StateMachineBuilderTest {

    enum TestState { A, B, C }
    enum TestEvent { GO, BACK }

    @Test
    void shouldBuildMachineWithBasicTransition() {
        StateMachine<TestState, TestEvent, Void> machine =
                StateMachineBuilder.<TestState, TestEvent, Void>builder("basic-test")
                    .transition()
                        .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                    .and()
                    .build();

        assertEquals(TestState.B,
                machine.fireEvent(TestState.A, TestEvent.GO, null).getTargetState());
    }

    @Test
    void shouldBuildMachineWithInitialAndEndStates() {
        StateMachine<TestState, TestEvent, Void> machine =
                StateMachineBuilder.<TestState, TestEvent, Void>builder("lifecycle-test")
                    .initialState(TestState.A)
                    .endStates(TestState.C)
                    .transition()
                        .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                    .and()
                    .transition()
                        .from(TestState.B).on(TestEvent.GO).to(TestState.C)
                    .and()
                    .build();

        assertEquals(TestState.A, machine.getInitialState());
        assertTrue(machine.getEndStates().contains(TestState.C));
    }

    @Test
    void shouldBuildMachineWithGuard() {
        StateMachine<TestState, TestEvent, Boolean> machine =
                StateMachineBuilder.<TestState, TestEvent, Boolean>builder("guard-test")
                    .transition()
                        .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                        .guard(ctx -> Boolean.TRUE.equals(ctx.getBusinessContext()))
                    .and()
                    .build();

        // Guard passes
        assertEquals(TestState.B,
                machine.fireEvent(TestState.A, TestEvent.GO, true).getTargetState());

        // Guard fails - throws exception (current design)
        assertThrows(StateMachineException.class,
                () -> machine.fireEvent(TestState.A, TestEvent.GO, false));
    }

    @Test
    void shouldBuildMachineWithAction() {
        final boolean[] actionRan = {false};
        StateMachine<TestState, TestEvent, Void> machine =
                StateMachineBuilder.<TestState, TestEvent, Void>builder("action-test")
                    .transition()
                        .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                        .perform(ctx -> actionRan[0] = true)
                    .and()
                    .build();

        machine.fireEvent(TestState.A, TestEvent.GO, null);
        assertTrue(actionRan[0]);
    }

    @Test
    void shouldBuildMachineWithInternalTransition() {
        final boolean[] actionRan = {false};
        StateMachine<TestState, TestEvent, Void> machine =
                StateMachineBuilder.<TestState, TestEvent, Void>builder("internal-test")
                    .transition()
                        .from(TestState.A).on(TestEvent.GO).to(TestState.A)
                        .internal()
                        .perform(ctx -> actionRan[0] = true)
                    .and()
                    .build();

        StateContext<TestState, TestEvent, Void> result =
                machine.fireEvent(TestState.A, TestEvent.GO, null);
        assertEquals(TestState.A, result.getTargetState());
        assertTrue(actionRan[0]);
    }

    @Test
    void shouldBuildMachineWithEntryAndExitActions() {
        final StringBuilder log = new StringBuilder();
        StateMachine<TestState, TestEvent, Void> machine =
                StateMachineBuilder.<TestState, TestEvent, Void>builder("entry-exit-test")
                    .stateWithEntry(TestState.B, ctx -> log.append("enter-B;"))
                    .stateWithExit(TestState.A, ctx -> log.append("exit-A;"))
                    .transition()
                        .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                    .and()
                    .build();

        machine.fireEvent(TestState.A, TestEvent.GO, null);
        assertEquals("exit-A;enter-B;", log.toString());
    }

    @Test
    void shouldBuildFromConfigurer() {
        StateMachine<TestState, TestEvent, Void> machine =
                StateMachineBuilder.fromConfigurer("configurer-test",
                        new StateMachineConfigurerAdapter<TestState, TestEvent, Void>() {
                            @Override
                            public void configure(StateConfigurer<TestState, TestEvent, Void> states) {
                                states.initial(TestState.A);
                                states.state(TestState.B);
                            }

                            @Override
                            public void configure(TransitionConfigurer<TestState, TestEvent, Void> transitions) {
                                transitions.withExternal()
                                        .source(TestState.A).event(TestEvent.GO).target(TestState.B);
                            }
                        });

        assertEquals(TestState.B,
                machine.fireEvent(TestState.A, TestEvent.GO, null).getTargetState());
    }

    @Test
    void shouldThrowWhenConfigurerIsNull() {
        assertThrows(NullPointerException.class,
                () -> StateMachineBuilder.fromConfigurer("null-test", null));
    }

    @Test
    void shouldThrowWhenConfigurerFails() {
        assertThrows(StateMachineException.class, () ->
                StateMachineBuilder.fromConfigurer("error-test",
                        new StateMachineConfigurerAdapter<TestState, TestEvent, Void>() {
                            @Override
                            public void configure(StateConfigurer<TestState, TestEvent, Void> states) {
                                throw new RuntimeException("config error");
                            }
                        }));
    }

    @Test
    void shouldBuildMachineWithMultipleTransitionsFromSameState() {
        StateMachine<TestState, TestEvent, Void> machine =
                StateMachineBuilder.<TestState, TestEvent, Void>builder("multi-test")
                    .transition()
                        .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                    .and()
                    .transition()
                        .from(TestState.A).on(TestEvent.BACK).to(TestState.C)
                    .and()
                    .build();

        assertEquals(TestState.B,
                machine.fireEvent(TestState.A, TestEvent.GO, null).getTargetState());
        assertEquals(TestState.C,
                machine.fireEvent(TestState.A, TestEvent.BACK, null).getTargetState());
    }
}
