package com.selfdevelopment.statemachine.validation;

import com.selfdevelopment.statemachine.core.StateDef;
import com.selfdevelopment.statemachine.core.Transition;
import com.selfdevelopment.statemachine.core.TransitionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates state machine configurations for correctness and completeness.
 * <p>
 * Validation rules:
 * <ul>
 *   <li><b>NO_TRANSITIONS</b>: State machine must have at least one transition</li>
 *   <li><b>INITIAL_STATE_DEFINED</b>: Initial state should be configured (warning)</li>
 *   <li><b>INITIAL_STATE_REACHABLE</b>: Initial state must appear in transitions</li>
 *   <li><b>END_STATE_NO_OUTGOING</b>: End states must not have outgoing transitions</li>
 *   <li><b>UNREACHABLE_STATE</b>: All states must be reachable from initial state</li>
 *   <li><b>DEAD_END_STATE</b>: Non-end states must have at least one outgoing transition</li>
 *   <li><b>INTERNAL_TRANSITION_MATCH</b>: INTERNAL transitions must have source == target</li>
 *   <li><b>DUPLICATE_TRANSITION_NO_GUARD</b>: Multiple transitions with same (source, event) should have guards</li>
 * </ul>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class StateMachineValidator<S, E, C> {

    // Error codes
    public static final String NO_TRANSITIONS = "NO_TRANSITIONS";
    public static final String INITIAL_STATE_DEFINED = "INITIAL_STATE_DEFINED";
    public static final String INITIAL_STATE_REACHABLE = "INITIAL_STATE_REACHABLE";
    public static final String END_STATE_NO_OUTGOING = "END_STATE_NO_OUTGOING";
    public static final String UNREACHABLE_STATE = "UNREACHABLE_STATE";
    public static final String DEAD_END_STATE = "DEAD_END_STATE";
    public static final String INTERNAL_TRANSITION_MATCH = "INTERNAL_TRANSITION_MATCH";
    public static final String DUPLICATE_TRANSITION_NO_GUARD = "DUPLICATE_TRANSITION_NO_GUARD";

    /**
     * Validates a state machine configuration.
     *
     * @param transitions  the list of transitions
     * @param initialState the initial state (may be null)
     * @param endStates    the set of end states (may be empty)
     * @return list of validation errors (empty if valid)
     */
    public List<ValidationError> validate(List<Transition<S, E, C>> transitions,
                                           S initialState,
                                           Set<S> endStates) {
        if (transitions == null) {
            transitions = List.of();
        }
        if (endStates == null) {
            endStates = Set.of();
        }

        List<ValidationError> errors = new ArrayList<>();
        Set<S> allStates = extractAllStates(transitions);

        // Rule 1: Must have at least one transition
        if (transitions.isEmpty()) {
            errors.add(ValidationError.error(NO_TRANSITIONS,
                    "State machine must have at least one transition"));
            return Collections.unmodifiableList(errors);
        }

        // Rule 2: Initial state should be defined
        if (initialState == null) {
            errors.add(ValidationError.warning(INITIAL_STATE_DEFINED,
                    "Initial state is not configured. Consider setting an initial state."));
        }

        // Rule 3: Initial state must be in transitions
        if (initialState != null && !allStates.contains(initialState)) {
            errors.add(ValidationError.error(INITIAL_STATE_REACHABLE,
                    "Initial state '" + initialState + "' does not appear in any transition",
                    "Add a transition with '" + initialState + "' as source or target"));
        }

        // Rule 4: End states must not have outgoing transitions
        for (S endState : endStates) {
            boolean hasOutgoing = transitions.stream()
                    .anyMatch(t -> t.getSourceState().equals(endState));
            if (hasOutgoing) {
                errors.add(ValidationError.error(END_STATE_NO_OUTGOING,
                        "End state '" + endState + "' has outgoing transitions",
                        "End states should be terminal and not have outgoing transitions"));
            }
        }

        // Rule 5: All states must be reachable from initial state
        if (initialState != null) {
            validateReachability(initialState, transitions, allStates, errors);
        }

        // Rule 6: Non-end states must have outgoing transitions (no dead ends)
        for (S state : allStates) {
            if (endStates.contains(state)) {
                continue;
            }
            boolean hasOutgoing = transitions.stream()
                    .anyMatch(t -> t.getSourceState().equals(state));
            if (!hasOutgoing) {
                errors.add(ValidationError.warning(DEAD_END_STATE,
                        "State '" + state + "' has no outgoing transitions and is not an end state",
                        "Add outgoing transitions or mark this state as an end state"));
            }
        }

        // Rule 7: INTERNAL transitions must have source == target
        for (Transition<S, E, C> t : transitions) {
            if (t.getKind() == TransitionKind.INTERNAL
                    && !t.getSourceState().equals(t.getTargetState())) {
                errors.add(ValidationError.error(INTERNAL_TRANSITION_MATCH,
                        "INTERNAL transition must have source == target, but got "
                                + t.getSourceState() + " -> " + t.getTargetState(),
                        "Use EXTERNAL transition for state changes, or set target == source"));
            }
        }

        // Rule 8: Multiple transitions with same (source, event) should have guards
        validateDuplicateTransitionsHaveGuards(transitions, errors);

        return Collections.unmodifiableList(errors);
    }

    /**
     * Validates and throws if any ERROR-level validation errors exist.
     *
     * @param transitions  the list of transitions
     * @param initialState the initial state
     * @param endStates    the set of end states
     * @throws IllegalStateException if any ERROR-level validation errors are found
     */
    public void validateOrThrow(List<Transition<S, E, C>> transitions,
                                 S initialState,
                                 Set<S> endStates) {
        List<ValidationError> errors = validate(transitions, initialState, endStates);
        List<ValidationError> fatalErrors = errors.stream()
                .filter(e -> e.severity() == ValidationError.Severity.ERROR)
                .toList();

        if (!fatalErrors.isEmpty()) {
            String message = fatalErrors.stream()
                    .map(ValidationError::toString)
                    .collect(Collectors.joining("\n  ", "State machine validation failed:\n  ", ""));
            throw new IllegalStateException(message);
        }
    }

    // ===== Private Helpers =====

    private void validateReachability(S initialState,
                                       List<Transition<S, E, C>> transitions,
                                       Set<S> allStates,
                                       List<ValidationError> errors) {
        // BFS from initial state
        Set<S> reachable = new LinkedHashSet<>();
        reachable.add(initialState);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Transition<S, E, C> t : transitions) {
                if (reachable.contains(t.getSourceState()) && !reachable.contains(t.getTargetState())) {
                    reachable.add(t.getTargetState());
                    changed = true;
                }
            }
        }

        for (S state : allStates) {
            if (!reachable.contains(state)) {
                errors.add(ValidationError.warning(UNREACHABLE_STATE,
                        "State '" + state + "' is unreachable from initial state '" + initialState + "'",
                        "Consider removing this state or adding a transition to reach it"));
            }
        }
    }

    private void validateDuplicateTransitionsHaveGuards(List<Transition<S, E, C>> transitions,
                                                          List<ValidationError> errors) {
        Map<String, List<Transition<S, E, C>>> grouped = new HashMap<>();
        for (Transition<S, E, C> t : transitions) {
            String key = t.getSourceState() + "|" + t.getEvent();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        for (Map.Entry<String, List<Transition<S, E, C>>> entry : grouped.entrySet()) {
            List<Transition<S, E, C>> group = entry.getValue();
            if (group.size() > 1) {
                long withoutGuard = group.stream()
                        .filter(t -> t.getGuard() == null)
                        .count();
                if (withoutGuard > 0) {
                    errors.add(ValidationError.warning(DUPLICATE_TRANSITION_NO_GUARD,
                            "Multiple transitions for " + entry.getKey()
                                    + " but " + withoutGuard + " have no guard",
                            "Transitions without guards will always match first; add guards to distinguish conditions"));
                }
            }
        }
    }

    private Set<S> extractAllStates(List<Transition<S, E, C>> transitions) {
        Set<S> states = new HashSet<>();
        for (Transition<S, E, C> t : transitions) {
            states.add(t.getSourceState());
            states.add(t.getTargetState());
        }
        return states;
    }
}
