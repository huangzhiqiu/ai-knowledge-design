package com.selfdevelopment.ai.messaging.statemachine.core;

import com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException;
import com.selfdevelopment.ai.messaging.statemachine.listener.StateMachineListener;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 *   <li><b>Listener support</b>: inspired by Spring StateMachine's listener mechanism</li>
 *   <li><b>Extended state</b>: key-value variables shared across transitions</li>
 * </ul>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public final class SimpleStateMachine<S, E, C> implements StateMachine<S, E, C> {

    private record TransitionKey<S, E>(S sourceState, E event) {
        static <S, E> TransitionKey<S, E> of(S sourceState, E event) {
            return new TransitionKey<>(sourceState, event);
        }
    }

    private final Map<TransitionKey<S, E>, List<Transition<S, E, C>>> transitions;
    private final Map<S, StateDef<S, E, C>> stateDefs;
    private final List<StateMachineListener<S, E, C>> listeners = new CopyOnWriteArrayList<>();
    private final String machineId;
    private final S initialState;
    private final Set<S> endStates;
    private volatile boolean started = false;

    /**
     * Creates a state machine from a list of transitions.
     */
    public SimpleStateMachine(String machineId, List<Transition<S, E, C>> transitions) {
        this(machineId, transitions, null, Collections.emptySet(), Collections.emptyMap());
    }

    /**
     * Creates a state machine with initial state and end states.
     */
    public SimpleStateMachine(String machineId, List<Transition<S, E, C>> transitions,
                              S initialState, Set<S> endStates) {
        this(machineId, transitions, initialState, endStates, Collections.emptyMap());
    }

    /**
     * Creates a state machine with state definitions (entry/exit actions).
     */
    public SimpleStateMachine(String machineId, List<Transition<S, E, C>> transitions,
                              S initialState, Set<S> endStates,
                              Map<S, StateDef<S, E, C>> stateDefs) {
        this.machineId = Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(transitions, "transitions must not be null");
        this.initialState = initialState;
        this.endStates = endStates != null ? Set.copyOf(endStates) : Collections.emptySet();
        this.stateDefs = stateDefs != null ? Map.copyOf(stateDefs) : Collections.emptyMap();

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
    public void start() {
        if (!started) {
            started = true;
            listeners.forEach(StateMachineListener::stateMachineStarted);
        }
    }

    @Override
    public void stop() {
        if (started) {
            started = false;
            listeners.forEach(StateMachineListener::stateMachineStopped);
        }
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context) {
        return fireEvent(sourceState, event, context, null);
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context, ExtendedState extendedState) {
        Objects.requireNonNull(sourceState, "sourceState must not be null");
        Objects.requireNonNull(event, "event must not be null");

        ExtendedState ext = extendedState != null ? extendedState : new ExtendedState();

        List<Transition<S, E, C>> candidates = transitions.get(TransitionKey.of(sourceState, event));
        if (candidates == null || candidates.isEmpty()) {
            StateContext<S, E, C> deniedCtx = StateContext.<S, E, C>builder()
                    .sourceState(sourceState)
                    .event(event)
                    .businessContext(context)
                    .extendedState(ext)
                    .transitionAccepted(false)
                    .build();
            listeners.forEach(l -> l.transitionDenied(deniedCtx, "No transition found"));
            throw new StateMachineException(
                    String.format("No transition found: state=%s, event=%s (machine=%s)",
                            sourceState, event, machineId));
        }

        for (Transition<S, E, C> t : candidates) {
            StateContext<S, E, C> preCtx = StateContext.<S, E, C>builder()
                    .sourceState(sourceState)
                    .targetState(t.getTargetState())
                    .event(event)
                    .businessContext(context)
                    .extendedState(ext)
                    .build();

            listeners.forEach(l -> l.transitionStarted(t, preCtx));

            if (!t.isGuardSatisfied(preCtx)) {
                // Guard failed for this candidate; try next candidate without notifying denied
                // (denied is notified once after all candidates are exhausted)
                continue;
            }

            // Execute exit action of source state (best-effort, failures do not block transition)
            if (!t.isInternal()) {
                executeSafely(() -> {
                    StateDef<S, E, C> sourceDef = stateDefs.get(sourceState);
                    if (sourceDef != null && sourceDef.hasExitAction()) {
                        sourceDef.exit(preCtx);
                    }
                }, preCtx, "exit action of state " + sourceState);
            }

            // Execute transition action (failures propagate and abort transition)
            try {
                t.executeAction(preCtx);
            } catch (RuntimeException ex) {
                StateContext<S, E, C> errorCtx = StateContext.<S, E, C>builder()
                        .sourceState(sourceState)
                        .targetState(t.getTargetState())
                        .event(event)
                        .businessContext(context)
                        .extendedState(ext)
                        .exception(ex)
                        .transitionAccepted(false)
                        .build();
                listeners.forEach(l -> l.transitionError(errorCtx));
                throw new StateMachineException("Transition action failed: " + ex.getMessage(), ex);
            }

            // Execute entry action of target state (best-effort, failures do not block transition)
            if (!t.isInternal()) {
                executeSafely(() -> {
                    StateDef<S, E, C> targetDef = stateDefs.get(t.getTargetState());
                    if (targetDef != null && targetDef.hasEntryAction()) {
                        targetDef.enter(preCtx);
                    }
                }, preCtx, "entry action of state " + t.getTargetState());
            }

            S targetState = t.isInternal() ? sourceState : t.getTargetState();
            StateContext<S, E, C> resultCtx = StateContext.<S, E, C>builder()
                    .sourceState(sourceState)
                    .targetState(targetState)
                    .event(event)
                    .businessContext(context)
                    .extendedState(ext)
                    .transitionAccepted(true)
                    .build();

            listeners.forEach(l -> l.transitionEnded(t, resultCtx));
            if (!t.isInternal() && !sourceState.equals(targetState)) {
                listeners.forEach(l -> l.stateChanged(resultCtx));
            }
            return resultCtx;
        }

        StateContext<S, E, C> deniedCtx = StateContext.<S, E, C>builder()
                .sourceState(sourceState)
                .event(event)
                .businessContext(context)
                .extendedState(ext)
                .transitionAccepted(false)
                .build();
        listeners.forEach(l -> l.transitionDenied(deniedCtx, "All guard conditions failed"));
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
        ExtendedState ext = new ExtendedState();
        for (Transition<S, E, C> t : candidates) {
            StateContext<S, E, C> ctx = StateContext.<S, E, C>builder()
                    .sourceState(sourceState)
                    .targetState(t.getTargetState())
                    .event(event)
                    .businessContext(context)
                    .extendedState(ext)
                    .build();
            if (t.isGuardSatisfied(ctx)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getTransitionCount() {
        return transitions.values().stream().mapToInt(List::size).sum();
    }

    @Override
    public Collection<Transition<S, E, C>> getAllTransitions() {
        return transitions.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    @Override
    public String getMachineId() {
        return machineId;
    }

    @Override
    public S getInitialState() {
        return initialState;
    }

    @Override
    public Collection<S> getEndStates() {
        return endStates;
    }

    @Override
    public void addListener(StateMachineListener<S, E, C> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(StateMachineListener<S, E, C> listener) {
        listeners.remove(listener);
    }

    @Override
    public String toString() {
        return "SimpleStateMachine{id='" + machineId + "', transitions=" + getTransitionCount()
                + ", initial=" + initialState + ", ends=" + endStates + "}";
    }

    /**
     * Executes a runnable safely, catching and logging any RuntimeException.
     * Used for best-effort actions (entry/exit) that should not abort the transition.
     */
    private void executeSafely(Runnable action, StateContext<S, E, C> ctx, String description) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            StateContext<S, E, C> errorCtx = StateContext.<S, E, C>builder()
                    .sourceState(ctx.getSourceState())
                    .targetState(ctx.getTargetState())
                    .event(ctx.getEvent())
                    .businessContext(ctx.getBusinessContext())
                    .extendedState(ctx.getExtendedState())
                    .exception(ex)
                    .transitionAccepted(true)
                    .build();
            listeners.forEach(l -> l.transitionError(errorCtx));
        }
    }
}
