package com.selfdevelopment.ai.messaging.statemachine.builder;

import com.selfdevelopment.ai.messaging.statemachine.config.DefaultStateConfigurer;
import com.selfdevelopment.ai.messaging.statemachine.config.DefaultTransitionConfigurer;
import com.selfdevelopment.ai.messaging.statemachine.config.StateConfigurer;
import com.selfdevelopment.ai.messaging.statemachine.config.StateMachineConfigurerAdapter;
import com.selfdevelopment.ai.messaging.statemachine.config.TransitionConfigurer;
import com.selfdevelopment.ai.messaging.statemachine.core.Action;
import com.selfdevelopment.ai.messaging.statemachine.core.Guard;
import com.selfdevelopment.ai.messaging.statemachine.core.SimpleStateMachine;
import com.selfdevelopment.ai.messaging.statemachine.core.StateDef;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.core.Transition;
import com.selfdevelopment.ai.messaging.statemachine.core.TransitionKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final Map<S, StateDef<S, E, C>> stateDefs = new LinkedHashMap<>();
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
     * Builds a state machine from a {@link StateMachineConfigurerAdapter}.
     * <p>
     * This is the Spring-style configuration approach: extend the adapter, override
     * {@code configure(StateConfigurer)} and {@code configure(TransitionConfigurer)},
     * then pass an instance to this method.
     *
     * @param machineId  the machine identifier
     * @param configurer the configurer adapter instance
     * @param <S>        the state type
     * @param <E>        the event type
     * @param <C>        the context type
     * @return the constructed state machine
     * @throws RuntimeException if configuration fails (wraps checked exceptions)
     */
    public static <S, E, C> StateMachine<S, E, C> fromConfigurer(
            String machineId, StateMachineConfigurerAdapter<S, E, C> configurer) {
        Objects.requireNonNull(configurer, "configurer must not be null");
        DefaultStateConfigurer<S, E, C> stateConfig = new DefaultStateConfigurer<>();
        DefaultTransitionConfigurer<S, E, C> transitionConfig = new DefaultTransitionConfigurer<>();
        try {
            configurer.configure((StateConfigurer<S, E, C>) stateConfig);
            configurer.configure((TransitionConfigurer<S, E, C>) transitionConfig);
        } catch (Exception e) {
            throw new RuntimeException("State machine configuration failed: " + e.getMessage(), e);
        }

        Map<S, StateDef<S, E, C>> states = stateConfig.getStates();
        Set<S> ends = states.values().stream()
                .filter(StateDef::isEnd)
                .map(StateDef::getId)
                .collect(Collectors.toSet());

        return new SimpleStateMachine<>(
                machineId,
                transitionConfig.getTransitions(),
                stateConfig.getInitialState(),
                ends,
                states);
    }

    // ===== State definition methods (Spring-style) =====

    /**
     * Adds a state with entry and exit actions.
     *
     * @param id          the state identifier
     * @param entryAction the entry action (may be null)
     * @param exitAction  the exit action (may be null)
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> state(S id, Action<S, E, C> entryAction, Action<S, E, C> exitAction) {
        stateDefs.put(id, StateDef.<S, E, C>builder(id)
                .entryAction(entryAction)
                .exitAction(exitAction)
                .build());
        return this;
    }

    /**
     * Adds a state with only an entry action.
     */
    public StateMachineBuilder<S, E, C> stateWithEntry(S id, Action<S, E, C> entryAction) {
        stateDefs.put(id, StateDef.<S, E, C>builder(id).entryAction(entryAction).build());
        return this;
    }

    /**
     * Adds a state with only an exit action.
     */
    public StateMachineBuilder<S, E, C> stateWithExit(S id, Action<S, E, C> exitAction) {
        stateDefs.put(id, StateDef.<S, E, C>builder(id).exitAction(exitAction).build());
        return this;
    }

    /**
     * Adds a simple state with no entry/exit actions.
     */
    public StateMachineBuilder<S, E, C> state(S id) {
        stateDefs.putIfAbsent(id, StateDef.<S, E, C>builder(id).build());
        return this;
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
        return new SimpleStateMachine<>(machineId, transitions, initialState, endStates, stateDefs);
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
