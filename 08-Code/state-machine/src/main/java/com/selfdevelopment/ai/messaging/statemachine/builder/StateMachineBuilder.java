package com.selfdevelopment.ai.messaging.statemachine.builder;

import com.selfdevelopment.ai.messaging.statemachine.core.Action;
import com.selfdevelopment.ai.messaging.statemachine.core.Guard;
import com.selfdevelopment.ai.messaging.statemachine.core.SimpleStateMachine;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.core.Transition;
import com.selfdevelopment.ai.messaging.statemachine.core.TransitionKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fluent builder for constructing {@link StateMachine} instances.
 * <p>
 * Inspired by Spring StateMachine's builder pattern. Supports:
 * <ul>
 *   <li>External and internal transitions</li>
 *   <li>Guard conditions</li>
 *   <li>Transition actions</li>
 *   <li>Initial state and end states</li>
 * </ul>
 *
 * <pre>{@code
 * StateMachine<State, Event, Context> sm = StateMachineBuilder.<State, Event, Context>builder("order")
 *     .initialState(State.CREATED)
 *     .endStates(State.COMPLETED, State.CANCELLED)
 *     .transition()
 *         .from(State.CREATED).on(Event.PAY).to(State.PAID)
 *         .guard(ctx -> ctx.getBusinessContext().isPaymentValid())
 *         .perform(ctx -> log.info("Payment received"))
 *         .and()
 *     .transition()
 *         .from(State.PAID).on(Event.SHIP).to(State.SHIPPED)
 *         .and()
 *     .build();
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public final class StateMachineBuilder<S, E, C> {

    private final String machineId;
    private final List<Transition<S, E, C>> transitions = new ArrayList<>();
    private S initialState;
    private final Set<S> endStates = new HashSet<>();

    // Current transition being built
    private S currentSource;
    private E currentEvent;
    private S currentTarget;
    private Guard<S, E, C> currentGuard;
    private Action<S, E, C> currentAction;
    private TransitionKind currentKind = TransitionKind.EXTERNAL;

    private StateMachineBuilder(String machineId) {
        this.machineId = Objects.requireNonNull(machineId, "machineId must not be null");
    }

    /**
     * Creates a new builder instance.
     *
     * @param machineId a human-readable identifier for the state machine
     */
    public static <S, E, C> StateMachineBuilder<S, E, C> builder(String machineId) {
        return new StateMachineBuilder<>(machineId);
    }

    /**
     * Sets the initial state.
     *
     * @param initialState the initial state
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> initialState(S initialState) {
        this.initialState = initialState;
        return this;
    }

    /**
     * Adds end states.
     *
     * @param states the end states
     * @return this builder
     */
    @SafeVarargs
    public final StateMachineBuilder<S, E, C> endStates(S... states) {
        if (states != null) {
            for (S s : states) {
                endStates.add(s);
            }
        }
        return this;
    }

    /**
     * Starts defining a new transition.
     *
     * @return this builder (for fluent chaining)
     */
    public StateMachineBuilder<S, E, C> transition() {
        resetCurrent();
        return this;
    }

    /**
     * Sets the source state of the current transition.
     */
    public StateMachineBuilder<S, E, C> from(S sourceState) {
        this.currentSource = Objects.requireNonNull(sourceState, "sourceState must not be null");
        return this;
    }

    /**
     * Sets the triggering event of the current transition.
     */
    public StateMachineBuilder<S, E, C> on(E event) {
        this.currentEvent = Objects.requireNonNull(event, "event must not be null");
        return this;
    }

    /**
     * Sets the target state of the current transition.
     */
    public StateMachineBuilder<S, E, C> to(S targetState) {
        this.currentTarget = Objects.requireNonNull(targetState, "targetState must not be null");
        return this;
    }

    /**
     * Sets the guard condition for the current transition.
     *
     * @param guard the guard condition
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> guard(Guard<S, E, C> guard) {
        this.currentGuard = Objects.requireNonNull(guard, "guard must not be null");
        return this;
    }

    /**
     * Sets the action to execute on the current transition.
     *
     * @param action the action
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> perform(Action<S, E, C> action) {
        this.currentAction = Objects.requireNonNull(action, "action must not be null");
        return this;
    }

    /**
     * Marks the current transition as internal (state does not change, action executes).
     *
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> internal() {
        this.currentKind = TransitionKind.INTERNAL;
        return this;
    }

    /**
     * Marks the current transition as external (default).
     *
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> external() {
        this.currentKind = TransitionKind.EXTERNAL;
        return this;
    }

    /**
     * Completes the current transition and adds it to the state machine.
     *
     * @return this builder (for chaining the next transition)
     * @throws IllegalStateException if source/event/target are not set
     */
    public StateMachineBuilder<S, E, C> and() {
        if (currentSource == null || currentEvent == null || currentTarget == null) {
            throw new IllegalStateException(
                    "Transition must have source, event, and target set before calling and()");
        }
        transitions.add(new Transition<>(
                currentSource, currentEvent, currentTarget,
                currentGuard, currentAction, currentKind));
        resetCurrent();
        return this;
    }

    /**
     * Builds the state machine.
     * <p>
     * If a transition is in progress (source/event/target set but and() not called),
     * it is automatically completed.
     *
     * @return the constructed state machine
     */
    public StateMachine<S, E, C> build() {
        // Auto-complete any in-progress transition
        if (currentSource != null && currentEvent != null && currentTarget != null) {
            transitions.add(new Transition<>(
                    currentSource, currentEvent, currentTarget,
                    currentGuard, currentAction, currentKind));
            resetCurrent();
        }
        return new SimpleStateMachine<>(machineId, transitions, initialState, endStates);
    }

    private void resetCurrent() {
        currentSource = null;
        currentEvent = null;
        currentTarget = null;
        currentGuard = null;
        currentAction = null;
        currentKind = TransitionKind.EXTERNAL;
    }
}
