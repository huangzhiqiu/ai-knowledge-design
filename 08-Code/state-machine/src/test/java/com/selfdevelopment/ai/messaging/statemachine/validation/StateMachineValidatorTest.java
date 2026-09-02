package com.selfdevelopment.ai.messaging.statemachine.validation;

import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;

import com.selfdevelopment.ai.messaging.statemachine.api.Guard;

import com.selfdevelopment.ai.messaging.statemachine.core.Transition;
import com.selfdevelopment.ai.messaging.statemachine.core.TransitionKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StateMachineValidator}.
 */
class StateMachineValidatorTest {

    private StateMachineValidator<TestState, TestEvent, Void> validator;

    enum TestState { A, B, C, D, END }
    enum TestEvent { GO, BACK, STAY }

    @BeforeEach
    void setUp() {
        validator = new StateMachineValidator<>();
    }

    @Test
    void shouldPassValidationForValidConfig() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.B, null, null),
                new Transition<>(TestState.B, TestEvent.GO, TestState.END, null, null)
        );

        List<ValidationError> errors = validator.validate(transitions, TestState.A, Set.of(TestState.END));

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
    }

    @Test
    void shouldDetectNoTransitions() {
        List<ValidationError> errors = validator.validate(List.of(), TestState.A, Set.of());

        assertEquals(1, errors.size());
        assertEquals(StateMachineValidator.NO_TRANSITIONS, errors.get(0).code());
        assertEquals(ValidationError.Severity.ERROR, errors.get(0).severity());
    }

    @Test
    void shouldWarnWhenInitialStateNotDefined() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.B, null, null)
        );

        List<ValidationError> errors = validator.validate(transitions, null, Set.of());

        assertTrue(errors.stream().anyMatch(e ->
                e.code().equals(StateMachineValidator.INITIAL_STATE_DEFINED)
                        && e.severity() == ValidationError.Severity.WARNING));
    }

    @Test
    void shouldDetectInitialStateNotInTransitions() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.B, null, null)
        );

        // Initial state C is not in any transition
        List<ValidationError> errors = validator.validate(transitions, TestState.C, Set.of());

        assertTrue(errors.stream().anyMatch(e ->
                e.code().equals(StateMachineValidator.INITIAL_STATE_REACHABLE)
                        && e.severity() == ValidationError.Severity.ERROR));
    }

    @Test
    void shouldDetectEndStateWithOutgoingTransitions() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.END, null, null),
                new Transition<>(TestState.END, TestEvent.BACK, TestState.A, null, null)  // END has outgoing
        );

        List<ValidationError> errors = validator.validate(transitions, TestState.A, Set.of(TestState.END));

        assertTrue(errors.stream().anyMatch(e ->
                e.code().equals(StateMachineValidator.END_STATE_NO_OUTGOING)
                        && e.severity() == ValidationError.Severity.ERROR));
    }

    @Test
    void shouldDetectUnreachableState() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.B, null, null),
                new Transition<>(TestState.B, TestEvent.GO, TestState.END, null, null),
                // State D is unreachable from A
                new Transition<>(TestState.D, TestEvent.GO, TestState.END, null, null)
        );

        List<ValidationError> errors = validator.validate(transitions, TestState.A, Set.of(TestState.END));

        assertTrue(errors.stream().anyMatch(e ->
                e.code().equals(StateMachineValidator.UNREACHABLE_STATE)
                        && e.severity() == ValidationError.Severity.WARNING
                        && e.message().contains("D")));
    }

    @Test
    void shouldDetectDeadEndState() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.B, null, null),
                new Transition<>(TestState.A, TestEvent.GO, TestState.C, null, null)
                // B and C have no outgoing transitions and are not end states
        );

        List<ValidationError> errors = validator.validate(transitions, TestState.A, Set.of());

        long deadEndCount = errors.stream()
                .filter(e -> e.code().equals(StateMachineValidator.DEAD_END_STATE))
                .count();
        assertEquals(2, deadEndCount);  // Both B and C
    }

    @Test
    void shouldNotFlagEndStateAsDeadEnd() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.END, null, null)
        );

        List<ValidationError> errors = validator.validate(transitions, TestState.A, Set.of(TestState.END));

        assertFalse(errors.stream().anyMatch(e ->
                e.code().equals(StateMachineValidator.DEAD_END_STATE)));
    }

    @Test
    void shouldDetectInternalTransitionWithMismatchedSourceTarget() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.STAY, TestState.B, null, null, TransitionKind.INTERNAL)
        );

        List<ValidationError> errors = validator.validate(transitions, TestState.A, Set.of());

        assertTrue(errors.stream().anyMatch(e ->
                e.code().equals(StateMachineValidator.INTERNAL_TRANSITION_MATCH)
                        && e.severity() == ValidationError.Severity.ERROR));
    }

    @Test
    void shouldPassInternalTransitionWithMatchingSourceTarget() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.STAY, TestState.A, null, null, TransitionKind.INTERNAL),
                new Transition<>(TestState.A, TestEvent.GO, TestState.B, null, null)
        );

        List<ValidationError> errors = validator.validate(transitions, TestState.A, Set.of(TestState.B));

        assertFalse(errors.stream().anyMatch(e ->
                e.code().equals(StateMachineValidator.INTERNAL_TRANSITION_MATCH)));
    }

    @Test
    void shouldWarnOnDuplicateTransitionsWithoutGuards() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.B, null, null),
                new Transition<>(TestState.A, TestEvent.GO, TestState.C, null, null)  // Same source+event, no guard
        );

        List<ValidationError> errors = validator.validate(transitions, TestState.A, Set.of());

        assertTrue(errors.stream().anyMatch(e ->
                e.code().equals(StateMachineValidator.DUPLICATE_TRANSITION_NO_GUARD)
                        && e.severity() == ValidationError.Severity.WARNING));
    }

    @Test
    void shouldNotWarnOnDuplicateTransitionsWithGuards() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.B, ctx -> true, null),
                new Transition<>(TestState.A, TestEvent.GO, TestState.C, ctx -> false, null)
        );

        List<ValidationError> errors = validator.validate(transitions, TestState.A, Set.of());

        assertFalse(errors.stream().anyMatch(e ->
                e.code().equals(StateMachineValidator.DUPLICATE_TRANSITION_NO_GUARD)));
    }

    @Test
    void shouldThrowOnValidateOrThrowWithErrors() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.B, null, null)
        );

        assertThrows(IllegalStateException.class, () ->
                validator.validateOrThrow(transitions, TestState.C, Set.of()));
    }

    @Test
    void shouldNotThrowOnValidateOrThrowWithOnlyWarnings() {
        List<Transition<TestState, TestEvent, Void>> transitions = List.of(
                new Transition<>(TestState.A, TestEvent.GO, TestState.B, null, null)
        );

        // Initial state not defined is a warning, not an error
        assertDoesNotThrow(() -> validator.validateOrThrow(transitions, null, Set.of()));
    }

    @Test
    void shouldHandleNullTransitionsGracefully() {
        List<ValidationError> errors = validator.validate(null, TestState.A, null);
        assertFalse(errors.isEmpty());
    }
}
