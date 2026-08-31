package com.selfdevelopment.ai.messaging.statemachine.registry;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StateMachineRegistry}.
 */
class StateMachineRegistryTest {

    enum TestState { A, B }
    enum TestEvent { GO }

    private StateMachineRegistry registry;
    private StateMachine<TestState, TestEvent, Void> machine;

    @BeforeEach
    void setUp() {
        registry = new StateMachineRegistry();
        machine = StateMachineBuilder.<TestState, TestEvent, Void>builder("test-machine")
                .transition()
                    .from(TestState.A)
                    .on(TestEvent.GO)
                    .to(TestState.B)
                .and()
                .build();
    }

    @Test
    void shouldRegisterAndRetrieveMachine() {
        registry.register(machine);
        StateMachine<TestState, TestEvent, Void> retrieved = registry.get("test-machine");
        assertSame(machine, retrieved);
    }

    @Test
    void shouldThrowWhenRegisteringDuplicateId() {
        registry.register(machine);
        assertThrows(StateMachineException.class, () -> registry.register(machine));
    }

    @Test
    void shouldThrowWhenGettingUnregisteredMachine() {
        assertThrows(StateMachineException.class, () -> registry.get("nonexistent"));
    }

    @Test
    void shouldReportContains() {
        assertFalse(registry.contains("test-machine"));
        registry.register(machine);
        assertTrue(registry.contains("test-machine"));
    }

    @Test
    void shouldUnregisterMachine() {
        registry.register(machine);
        assertTrue(registry.unregister("test-machine"));
        assertFalse(registry.contains("test-machine"));
        assertFalse(registry.unregister("test-machine"));
    }

    @Test
    void shouldReportSize() {
        assertEquals(0, registry.size());
        registry.register(machine);
        assertEquals(1, registry.size());
    }

    @Test
    void shouldRetrieveMachineWithCorrectGenericTypes() {
        registry.register(machine);
        StateMachine<TestState, TestEvent, Void> retrieved = registry.get("test-machine");
        assertEquals(TestState.B,
                retrieved.fireEvent(TestState.A, TestEvent.GO, null).getTargetState());
    }
}
