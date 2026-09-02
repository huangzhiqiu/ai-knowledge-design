package com.selfdevelopment.ai.messaging.statemachine.diagram;

import com.selfdevelopment.ai.messaging.statemachine.api.Guard;

import com.selfdevelopment.ai.messaging.statemachine.core.Transition;

import com.selfdevelopment.ai.messaging.statemachine.api.Action;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StateMachineDiagramGenerator}.
 */
class StateMachineDiagramGeneratorTest {

    enum TestState { A, B, C, END }
    enum TestEvent { GO, BACK, INTERNAL, CANCEL }

    private StateMachine<TestState, TestEvent, Void> machine;

    @BeforeEach
    void setUp() {
        machine = StateMachineBuilder.<TestState, TestEvent, Void>builder("test-machine")
                .initialState(TestState.A)
                .endStates(TestState.END)
                .transition()
                    .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                .and()
                .transition()
                    .from(TestState.B).on(TestEvent.GO).to(TestState.C)
                .and()
                .transition()
                    .from(TestState.B).on(TestEvent.BACK).to(TestState.A)
                .and()
                .transition()
                    .from(TestState.C).on(TestEvent.CANCEL).to(TestState.END)
                .and()
                .build();
    }

    // ===== Mermaid tests =====

    @Test
    void shouldGenerateMermaidWithHeader() {
        String mermaid = StateMachineDiagramGenerator.toMermaid(machine);
        assertTrue(mermaid.startsWith("stateDiagram-v2"));
        assertTrue(mermaid.contains("title test-machine"));
    }

    @Test
    void shouldGenerateMermaidInitialState() {
        String mermaid = StateMachineDiagramGenerator.toMermaid(machine);
        assertTrue(mermaid.contains("[*] --> A"));
    }

    @Test
    void shouldGenerateMermaidEndState() {
        String mermaid = StateMachineDiagramGenerator.toMermaid(machine);
        assertTrue(mermaid.contains("END --> [*]"));
    }

    @Test
    void shouldGenerateMermaidTransitions() {
        String mermaid = StateMachineDiagramGenerator.toMermaid(machine);
        assertTrue(mermaid.contains("A --> B : GO"));
        assertTrue(mermaid.contains("B --> C : GO"));
        assertTrue(mermaid.contains("B --> A : BACK"));
        assertTrue(mermaid.contains("C --> END : CANCEL"));
    }

    @Test
    void shouldGenerateMermaidWithGuardAndAction() {
        StateMachine<TestState, TestEvent, Void> guarded = StateMachineBuilder.<TestState, TestEvent, Void>builder("guarded")
                .initialState(TestState.A)
                .transition()
                    .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                    .guard(ctx -> true)
                    .perform(ctx -> {})
                .and()
                .build();

        String mermaid = StateMachineDiagramGenerator.toMermaid(guarded);
        assertTrue(mermaid.contains("GO [guard] / action"));
    }

    @Test
    void shouldGenerateMermaidInternalTransition() {
        StateMachine<TestState, TestEvent, Void> internal = StateMachineBuilder.<TestState, TestEvent, Void>builder("internal")
                .initialState(TestState.A)
                .transition()
                    .from(TestState.A).on(TestEvent.INTERNAL).to(TestState.A)
                .and()
                .build();

        String mermaid = StateMachineDiagramGenerator.toMermaid(internal);
        assertTrue(mermaid.contains("A --> A : INTERNAL"));
    }

    @Test
    void shouldHandleNullInitialState() {
        StateMachine<TestState, TestEvent, Void> noInit = StateMachineBuilder.<TestState, TestEvent, Void>builder("no-init")
                .transition()
                    .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                .and()
                .build();

        String mermaid = StateMachineDiagramGenerator.toMermaid(noInit);
        assertFalse(mermaid.contains("[*] -->"));
    }

    // ===== PlantUML tests =====

    @Test
    void shouldGeneratePlantUmlHeader() {
        String plantUml = StateMachineDiagramGenerator.toPlantUml(machine);
        assertTrue(plantUml.startsWith("@startuml"));
        assertTrue(plantUml.contains("@enduml"));
        assertTrue(plantUml.contains("title test-machine"));
    }

    @Test
    void shouldGeneratePlantUmlInitialAndEnd() {
        String plantUml = StateMachineDiagramGenerator.toPlantUml(machine);
        assertTrue(plantUml.contains("[*] --> A"));
        assertTrue(plantUml.contains("END --> [*]"));
    }

    @Test
    void shouldGeneratePlantUmlTransitions() {
        String plantUml = StateMachineDiagramGenerator.toPlantUml(machine);
        assertTrue(plantUml.contains("A --> B : GO"));
        assertTrue(plantUml.contains("B --> C : GO"));
    }

    @Test
    void shouldGeneratePlantUmlSkinparams() {
        String plantUml = StateMachineDiagramGenerator.toPlantUml(machine);
        assertTrue(plantUml.contains("skinparam state"));
    }

    // ===== Transition table tests =====

    @Test
    void shouldGenerateTransitionTableHeader() {
        String table = StateMachineDiagramGenerator.toTransitionTable(machine);
        assertTrue(table.contains("| # | From | Event | To | Kind | Guard | Action |"));
    }

    @Test
    void shouldGenerateTransitionTableRows() {
        String table = StateMachineDiagramGenerator.toTransitionTable(machine);
        // Don't depend on row number (HashMap order), check content exists
        assertTrue(table.contains("| A | GO | B |"));
        assertTrue(table.contains("| B | GO | C |"));
        assertTrue(table.contains("| B | BACK | A |"));
        assertTrue(table.contains("| C | CANCEL | END |"));
    }

    @Test
    void shouldMarkGuardAndActionInTable() {
        StateMachine<TestState, TestEvent, Void> guarded = StateMachineBuilder.<TestState, TestEvent, Void>builder("guarded")
                .initialState(TestState.A)
                .transition()
                    .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                    .guard(ctx -> true)
                    .perform(ctx -> {})
                .and()
                .build();

        String table = StateMachineDiagramGenerator.toTransitionTable(guarded);
        assertTrue(table.contains("| Yes | Yes |"));
    }

    @Test
    void shouldMarkNoGuardAndActionInTable() {
        String table = StateMachineDiagramGenerator.toTransitionTable(machine);
        assertTrue(table.contains("| - | - |"));
    }

    // ===== Edge cases =====

    @Test
    void shouldHandleEmptyMachine() {
        StateMachine<TestState, TestEvent, Void> empty = StateMachineBuilder.<TestState, TestEvent, Void>builder("empty")
                .build();

        String mermaid = StateMachineDiagramGenerator.toMermaid(empty);
        assertNotNull(mermaid);
        assertTrue(mermaid.contains("stateDiagram-v2"));
    }

    @Test
    void shouldGenerateConsistentOutput() {
        String m1 = StateMachineDiagramGenerator.toMermaid(machine);
        String m2 = StateMachineDiagramGenerator.toMermaid(machine);
        assertEquals(m1, m2);
    }
}
