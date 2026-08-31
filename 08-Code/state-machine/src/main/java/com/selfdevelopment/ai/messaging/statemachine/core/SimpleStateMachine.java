package com.selfdevelopment.ai.messaging.statemachine.core;

import com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default, thread-safe implementation of {@link StateMachine}.
 * <p>
 * Design characteristics:
 * <ul>
 *   <li><b>Stateless</b>: does not store current state; state is injected per call</li>
 *   <li><b>Table-driven</b>: transitions stored in a {@link ConcurrentHashMap} for O(1) lookup</li>
 *   <li><b>Thread-safe</b>: immutable after construction; safe for concurrent use</li>
 *   <li><b>Zero dependencies</b>: only JDK standard library</li>
 * </ul>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public final class SimpleStateMachine<S, E, C> implements StateMachine<S, E, C> {

    /**
     * Key for the transition map: combines source state and event.
     */
    private record TransitionKey<S, E>(S sourceState, E event) {
        static <S, E> TransitionKey<S, E> of(S sourceState, E event) {
            return new TransitionKey<>(sourceState, event);
        }
    }

    private final Map<TransitionKey<S, E>, List<Transition<S, E, C>>> transitions;
    private final String machineId;

    /**
     * Creates a state machine from a list of transitions.
     * <p>
     * The transitions are copied into an internal, thread-safe structure.
     * The input list is not modified.
     *
     * @param machineId   a human-readable identifier for this state machine
     * @param transitions the list of transition rules
     * @throws NullPointerException if transitions is null
     */
    public SimpleStateMachine(String machineId, List<Transition<S, E, C>> transitions) {
        this.machineId = Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(transitions, "transitions must not be null");

        Map<TransitionKey<S, E>, List<Transition<S, E, C>>> map = new ConcurrentHashMap<>();
        for (Transition<S, E, C> t : transitions) {
            map.computeIfAbsent(
                    TransitionKey.of(t.getSourceState(), t.getEvent()),
                    k -> new CopyOnWriteArrayList<>()
            ).add(t);
        }
        this.transitions = Collections.unmodifiableMap(map);
    }

    @Override
    public S fireEvent(S sourceState, E event, C context) {
        Objects.requireNonNull(sourceState, "sourceState must not be null");
        Objects.requireNonNull(event, "event must not be null");

        List<Transition<S, E, C>> candidates = transitions.get(TransitionKey.of(sourceState, event));
        if (candidates == null || candidates.isEmpty()) {
            throw new StateMachineException(
                    String.format("No transition found: state=%s, event=%s (machine=%s)",
                            sourceState, event, machineId));
        }

        for (Transition<S, E, C> t : candidates) {
            if (t.isConditionSatisfied(context)) {
                t.executeAction(context);
                return t.getTargetState();
            }
        }

        throw new StateMachineException(
                String.format("Transition guard condition failed: state=%s, event=%s (machine=%s)",
                        sourceState, event, machineId));
    }

    @Override
    public boolean hasTransition(S sourceState, E event) {
        if (sourceState == null || event == null) {
            return false;
        }
        List<Transition<S, E, C>> candidates = transitions.get(TransitionKey.of(sourceState, event));
        return candidates != null && !candidates.isEmpty();
    }

    @Override
    public boolean canFire(S sourceState, E event, C context) {
        if (!hasTransition(sourceState, event)) {
            return false;
        }
        List<Transition<S, E, C>> candidates = transitions.get(TransitionKey.of(sourceState, event));
        for (Transition<S, E, C> t : candidates) {
            if (t.isConditionSatisfied(context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getTransitionCount() {
        return transitions.values().stream().mapToInt(List::size).sum();
    }

    public String getMachineId() {
        return machineId;
    }

    @Override
    public String toString() {
        return "SimpleStateMachine{id='" + machineId + "', transitions=" + getTransitionCount() + "}";
    }
}
