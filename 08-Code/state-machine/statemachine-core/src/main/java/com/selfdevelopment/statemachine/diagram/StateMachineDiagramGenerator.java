package com.selfdevelopment.statemachine.diagram;

import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.core.Transition;
import com.selfdevelopment.statemachine.core.TransitionKind;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates state machine diagrams from a {@link StateMachine} configuration.
 * <p>
 * Supports two output formats:
 * <ul>
 *   <li><b>Mermaid</b> — for GitHub, Markdown, and modern documentation tools</li>
 *   <li><b>PlantUML</b> — for enterprise documentation and Confluence</li>
 * </ul>
 * <p>
 * The generated diagram includes:
 * <ul>
 *   <li>Initial state marker (filled circle → initial state)</li>
 *   <li>End states (double circle)</li>
 *   <li>All transitions with event labels</li>
 *   <li>Guard conditions shown in brackets [guard]</li>
 *   <li>Internal transitions shown as self-loops</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * StateMachine<OrderState, OrderEvent, OrderContext> machine = ...;
 *
 * // Generate Mermaid diagram
 * String mermaid = StateMachineDiagramGenerator.toMermaid(machine);
 *
 * // Generate PlantUML diagram
 * String plantUml = StateMachineDiagramGenerator.toPlantUml(machine);
 *
 * // Write to file
 * Files.writeString(Path.of("state-diagram.mmd"), mermaid);
 * }</pre>
 */
public final class StateMachineDiagramGenerator {

    private StateMachineDiagramGenerator() {
        // Utility class
    }

    /**
     * Generates a Mermaid state diagram from the given state machine.
     *
     * @param machine the state machine
     * @param <S>     the state type
     * @param <E>     the event type
     * @param <C>     the context type
     * @return Mermaid diagram text
     */
    public static <S, E, C> String toMermaid(StateMachine<S, E, C> machine) {
        StringBuilder sb = new StringBuilder();
        sb.append("stateDiagram-v2\n");

        // Title
        sb.append("    title ").append(machine.getMachineId()).append("\n\n");

        // Initial state
        if (machine.getInitialState() != null) {
            sb.append("    [*] --> ").append(stateName(machine.getInitialState())).append("\n");
        }

        // Collect all states
        Set<S> allStates = collectAllStates(machine);

        // End states
        for (S endState : machine.getEndStates()) {
            sb.append("    ").append(stateName(endState)).append(" --> [*]\n");
        }

        sb.append("\n");

        // Transitions
        for (Transition<S, E, C> t : machine.getAllTransitions()) {
            String from = stateName(t.getSourceState());
            String to = stateName(t.getTargetState());
            String label = eventLabel(t);

            if (t.getKind() == TransitionKind.INTERNAL || from.equals(to)) {
                // Internal transition: self-loop
                sb.append("    ").append(from).append(" --> ").append(from)
                        .append(" : ").append(label).append("\n");
            } else {
                sb.append("    ").append(from).append(" --> ").append(to)
                        .append(" : ").append(label).append("\n");
            }
        }

        // Note states with no transitions (isolated)
        for (S state : allStates) {
            boolean hasOutgoing = machine.getAllTransitions().stream()
                    .anyMatch(t -> t.getSourceState().equals(state));
            boolean hasIncoming = machine.getAllTransitions().stream()
                    .anyMatch(t -> t.getTargetState().equals(state));
            if (!hasOutgoing && !hasIncoming && !machine.getEndStates().contains(state)
                    && !state.equals(machine.getInitialState())) {
                sb.append("\n    note right of ").append(stateName(state))
                        .append(" : Isolated state (no transitions)\n");
            }
        }

        return sb.toString();
    }

    /**
     * Generates a PlantUML state diagram from the given state machine.
     *
     * @param machine the state machine
     * @param <S>     the state type
     * @param <E>     the event type
     * @param <C>     the context type
     * @return PlantUML diagram text
     */
    public static <S, E, C> String toPlantUml(StateMachine<S, E, C> machine) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title ").append(machine.getMachineId()).append("\n\n");

        // Skinparams for better appearance
        sb.append("skinparam state {\n");
        sb.append("    BackgroundColor<<initial>> #LightBlue\n");
        sb.append("    BackgroundColor<<end>> #LightGreen\n");
        sb.append("    ArrowColor #333333\n");
        sb.append("    BorderColor #333333\n");
        sb.append("}\n\n");

        // Initial state
        if (machine.getInitialState() != null) {
            sb.append("[*] --> ").append(stateName(machine.getInitialState())).append("\n");
        }

        // End states
        for (S endState : machine.getEndStates()) {
            sb.append(stateName(endState)).append(" --> [*]\n");
        }

        sb.append("\n");

        // Transitions
        for (Transition<S, E, C> t : machine.getAllTransitions()) {
            String from = stateName(t.getSourceState());
            String to = stateName(t.getTargetState());
            String label = eventLabel(t);

            sb.append(from).append(" --> ").append(to).append(" : ").append(label).append("\n");
        }

        sb.append("\n@enduml\n");
        return sb.toString();
    }

    /**
     * Generates a transition table in Markdown format.
     *
     * @param machine the state machine
     * @param <S>     the state type
     * @param <E>     the event type
     * @param <C>     the context type
     * @return Markdown table
     */
    public static <S, E, C> String toTransitionTable(StateMachine<S, E, C> machine) {
        StringBuilder sb = new StringBuilder();
        sb.append("| # | From | Event | To | Kind | Guard | Action |\n");
        sb.append("|---|------|-------|----|------|-------|--------|\n");

        int i = 1;
        for (Transition<S, E, C> t : machine.getAllTransitions()) {
            sb.append("| ").append(i++).append(" | ")
                    .append(stateName(t.getSourceState())).append(" | ")
                    .append(stateName(t.getEvent())).append(" | ")
                    .append(stateName(t.getTargetState())).append(" | ")
                    .append(t.getKind()).append(" | ")
                    .append(t.getGuard() != null ? "Yes" : "-").append(" | ")
                    .append(t.getAction() != null ? "Yes" : "-").append(" |\n");
        }

        return sb.toString();
    }

    // ===== Private helpers =====

    private static <S, E, C> Set<S> collectAllStates(StateMachine<S, E, C> machine) {
        Set<S> states = new LinkedHashSet<>();
        if (machine.getInitialState() != null) {
            states.add(machine.getInitialState());
        }
        states.addAll(machine.getEndStates());
        for (Transition<S, E, C> t : machine.getAllTransitions()) {
            states.add(t.getSourceState());
            states.add(t.getTargetState());
        }
        return states;
    }

    private static String stateName(Object obj) {
        if (obj == null) {
            return "null";
        }
        // For enums, use name(); for others, use toString()
        if (obj instanceof Enum<?> e) {
            return e.name();
        }
        return obj.toString();
    }

    private static <S, E, C> String eventLabel(Transition<S, E, C> t) {
        StringBuilder label = new StringBuilder();
        label.append(stateName(t.getEvent()));

        if (t.getGuard() != null) {
            label.append(" [guard]");
        }

        if (t.getAction() != null) {
            label.append(" / action");
        }

        return label.toString();
    }
}
