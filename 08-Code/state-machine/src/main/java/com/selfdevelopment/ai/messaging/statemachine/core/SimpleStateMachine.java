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
 * <p>
 * <b>Two firing modes:</b>
 * <ul>
 *   <li>{@link #fireEvent} — throws {@link StateMachineException} when no transition matches
 *       or all guards fail. Use this when you want failures to propagate (e.g., with
 *       ResilientStateMachine / FailoverStateMachine decorators).</li>
 *   <li>{@link #tryFireEvent} — returns a rejected {@link StateContext} instead of throwing.
 *       Use this when you want to handle "event not applicable" gracefully without try-catch.</li>
 * </ul>
 * Both modes still throw on action execution failures (those are real errors, not "not applicable").
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

    public SimpleStateMachine(String machineId, List<Transition<S, E, C>> transitions) {
        this(machineId, transitions, null, Collections.emptySet(), Collections.emptyMap());
    }

    public SimpleStateMachine(String machineId, List<Transition<S, E, C>> transitions,
                              S initialState, Set<S> endStates) {
        this(machineId, transitions, initialState, endStates, Collections.emptyMap());
    }

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

    // ===== Lifecycle =====

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

    // ===== Event firing =====

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context) {
        return fireEvent(sourceState, event, context, null);
    }

    /**
     * Fires an event and throws on rejection (no transition / guard failed).
     * <p>
     * Internally delegates to {@link #tryFireEvent}, then throws if the result
     * was rejected. Action execution failures always throw, regardless of mode.
     */
    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context, ExtendedState extendedState) {
        StateContext<S, E, C> result = tryFireEvent(sourceState, event, context, extendedState);
        if (!result.isTransitionAccepted()) {
            // Extract the rejection reason from the context (set by tryFireEvent)
            String reason = result.getException() != null
                    ? result.getException().getMessage()
                    : "Transition rejected";
            // Preserve original message format for backward compatibility
            // (ResilientStateMachine parses "No transition found" / "guard condition failed")
            String message = reason.contains("No transition found")
                    ? String.format("No transition found: state=%s, event=%s (machine=%s)",
                            sourceState, event, machineId)
                    : String.format("Transition guard condition failed: state=%s, event=%s (machine=%s)",
                            sourceState, event, machineId);
            throw new StateMachineException(message);
        }
        return result;
    }

    /**
     * Fires an event and returns a rejected context instead of throwing.
     * <p>
     * When no transition matches or all guards fail, returns a {@link StateContext}
     * with {@code transitionAccepted=false}. Action execution failures still throw
     * (those are real errors, not "event not applicable").
     * <p>
     * Use this method when you want to handle rejections gracefully:
     * <pre>{@code
     * StateContext<S, E, C> result = machine.tryFireEvent(state, event, ctx);
     * if (!result.isTransitionAccepted()) {
     *     log.warn("Event not applicable: {}", event);
     *     return;
     * }
     * }</pre>
     *
     * @param sourceState   the current state
     * @param event         the event to fire
     * @param context       the business context
     * @param extendedState the extended state (null creates a fresh one)
     * @return the state context (accepted or rejected)
     */
    public StateContext<S, E, C> tryFireEvent(S sourceState, E event, C context, ExtendedState extendedState) {
        Objects.requireNonNull(sourceState, "sourceState must not be null");
        Objects.requireNonNull(event, "event must not be null");

        ExtendedState ext = extendedState != null ? extendedState : new ExtendedState();

        List<Transition<S, E, C>> candidates = transitions.get(TransitionKey.of(sourceState, event));
        if (candidates == null || candidates.isEmpty()) {
            StateContext<S, E, C> denied = buildContext(sourceState, null, event, context, ext,
                    false, new StateMachineException("No transition found"));
            listeners.forEach(l -> l.transitionDenied(denied, "No transition found"));
            return denied;
        }

        for (Transition<S, E, C> t : candidates) {
            StateContext<S, E, C> preCtx = buildContext(sourceState, t.getTargetState(), event,
                    context, ext, true, null);

            listeners.forEach(l -> l.transitionStarted(t, preCtx));

            if (!t.isGuardSatisfied(preCtx)) {
                continue;
            }

            // Exit action (best-effort)
            if (!t.isInternal()) {
                executeStateAction(stateDefs.get(sourceState), true, preCtx, sourceState);
            }

            // Transition action (failures propagate)
            try {
                t.executeAction(preCtx);
            } catch (RuntimeException ex) {
                StateContext<S, E, C> errorCtx = buildContext(sourceState, t.getTargetState(),
                        event, context, ext, false, ex);
                listeners.forEach(l -> l.transitionError(errorCtx));
                throw new StateMachineException("Transition action failed: " + ex.getMessage(), ex);
            }

            // Entry action (best-effort)
            if (!t.isInternal()) {
                executeStateAction(stateDefs.get(t.getTargetState()), false, preCtx, t.getTargetState());
            }

            S targetState = t.isInternal() ? sourceState : t.getTargetState();
            StateContext<S, E, C> result = buildContext(sourceState, targetState, event, context,
                    ext, true, null);

            listeners.forEach(l -> l.transitionEnded(t, result));
            if (!t.isInternal() && !sourceState.equals(targetState)) {
                listeners.forEach(l -> l.stateChanged(result));
            }
            return result;
        }

        // All guards failed
        StateContext<S, E, C> denied = buildContext(sourceState, null, event, context, ext,
                false, new StateMachineException("Transition guard condition failed"));
        listeners.forEach(l -> l.transitionDenied(denied, "All guard conditions failed"));
        return denied;
    }

    // ===== Queries =====

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
            StateContext<S, E, C> ctx = buildContext(sourceState, t.getTargetState(), event,
                    context, ext, true, null);
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

    // ===== Listeners =====

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

    // ===== Private helpers =====

    /**
     * Builds a StateContext in one place, eliminating repeated builder calls.
     */
    private StateContext<S, E, C> buildContext(S sourceState, S targetState, E event,
                                                 C context, ExtendedState ext,
                                                 boolean accepted, Exception exception) {
        return StateContext.<S, E, C>builder()
                .sourceState(sourceState)
                .targetState(targetState)
                .event(event)
                .businessContext(context)
                .extendedState(ext)
                .transitionAccepted(accepted)
                .exception(exception)
                .build();
    }

    /**
     * Executes a state entry or exit action safely (best-effort, failures do not block transition).
     *
     * @param stateDef the state definition (may be null)
     * @param isExit   true for exit action, false for entry action
     * @param ctx      the state context
     * @param state    the state (for logging)
     */
    private void executeStateAction(StateDef<S, E, C> stateDef, boolean isExit,
                                      StateContext<S, E, C> ctx, S state) {
        if (stateDef == null) {
            return;
        }
        boolean hasAction = isExit ? stateDef.hasExitAction() : stateDef.hasEntryAction();
        if (!hasAction) {
            return;
        }
        try {
            if (isExit) {
                stateDef.exit(ctx);
            } else {
                stateDef.enter(ctx);
            }
        } catch (RuntimeException ex) {
            // Best-effort: log via listener, do not abort transition
            StateContext<S, E, C> errorCtx = buildContext(
                    ctx.getSourceState(), ctx.getTargetState(), ctx.getEvent(),
                    ctx.getBusinessContext(), ctx.getExtendedState(),
                    false, ex);  // FIX: was true — entry/exit failure should NOT mark as accepted
            listeners.forEach(l -> l.transitionError(errorCtx));
        }
    }
}
