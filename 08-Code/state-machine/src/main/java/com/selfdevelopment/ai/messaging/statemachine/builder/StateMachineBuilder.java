package com.selfdevelopment.ai.messaging.statemachine.builder;

import com.selfdevelopment.ai.messaging.statemachine.core.Action;
import com.selfdevelopment.ai.messaging.statemachine.core.Condition;
import com.selfdevelopment.ai.messaging.statemachine.core.SimpleStateMachine;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.core.Transition;
import com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent DSL builder for constructing {@link StateMachine} instances.
 * <p>
 * Usage example:
 * <pre>{@code
 * StateMachine<OrderState, OrderEvent, OrderContext> machine =
 *     StateMachineBuilder.<OrderState, OrderEvent, OrderContext>builder("order-machine")
 *         .transition()
 *             .from(OrderState.NEW)
 *             .on(OrderEvent.PAY)
 *             .to(OrderState.PAID)
 *             .when(ctx -> ctx.isPaymentValid())
 *             .perform(ctx -> log.info("Payment processed"))
 *         .and()
 *         .transition()
 *             .from(OrderState.PAID)
 *             .on(OrderEvent.SHIP)
 *             .to(OrderState.SHIPPED)
 *         .and()
 *         .build();
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public final class StateMachineBuilder<S, E, C> {

    private final String machineId;
    private final List<Transition<S, E, C>> transitions = new ArrayList<>();

    // Current transition being built
    private S currentSource;
    private E currentEvent;
    private S currentTarget;
    private Condition<C> currentCondition;
    private Action<C> currentAction;

    private StateMachineBuilder(String machineId) {
        this.machineId = machineId;
    }

    /**
     * Creates a new builder instance.
     *
     * @param machineId a human-readable identifier for the state machine
     * @param <S>       the state type
     * @param <E>       the event type
     * @param <C>       the context type
     * @return a new builder
     */
    public static <S, E, C> StateMachineBuilder<S, E, C> builder(String machineId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        return new StateMachineBuilder<>(machineId);
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
     *
     * @param sourceState the source state
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> from(S sourceState) {
        this.currentSource = Objects.requireNonNull(sourceState, "sourceState must not be null");
        return this;
    }

    /**
     * Sets the triggering event of the current transition.
     *
     * @param event the event
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> on(E event) {
        this.currentEvent = Objects.requireNonNull(event, "event must not be null");
        return this;
    }

    /**
     * Sets the target state of the current transition.
     *
     * @param targetState the target state
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> to(S targetState) {
        this.currentTarget = Objects.requireNonNull(targetState, "targetState must not be null");
        return this;
    }

    /**
     * Sets an optional guard condition for the current transition.
     *
     * @param condition the guard condition
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> when(Condition<C> condition) {
        this.currentCondition = Objects.requireNonNull(condition, "condition must not be null");
        return this;
    }

    /**
     * Sets an optional action to execute on the current transition.
     *
     * @param action the action
     * @return this builder
     */
    public StateMachineBuilder<S, E, C> perform(Action<C> action) {
        this.currentAction = Objects.requireNonNull(action, "action must not be null");
        return this;
    }

    /**
     * Completes the current transition and adds it to the state machine.
     *
     * @return this builder (to chain another transition)
     * @throws StateMachineException if the current transition is incomplete
     */
    public StateMachineBuilder<S, E, C> and() {
        if (currentSource == null || currentEvent == null || currentTarget == null) {
            throw new StateMachineException(
                    "Incomplete transition: from(), on(), and to() must all be called before and()");
        }
        transitions.add(new Transition<>(currentSource, currentEvent, currentTarget, currentCondition, currentAction));
        resetCurrent();
        return this;
    }

    /**
     * Builds the state machine from all defined transitions.
     * <p>
     * If a transition was started but not completed with {@link #and()},
     * it will be completed automatically.
     *
     * @return a new, immutable {@link StateMachine} instance
     * @throws StateMachineException if no transitions were defined
     */
    public StateMachine<S, E, C> build() {
        // Auto-complete any in-progress transition
        if (currentSource != null && currentEvent != null && currentTarget != null) {
            transitions.add(new Transition<>(currentSource, currentEvent, currentTarget, currentCondition, currentAction));
            resetCurrent();
        }
        if (transitions.isEmpty()) {
            throw new StateMachineException("At least one transition must be defined");
        }
        return new SimpleStateMachine<>(machineId, new ArrayList<>(transitions));
    }

    private void resetCurrent() {
        currentSource = null;
        currentEvent = null;
        currentTarget = null;
        currentCondition = null;
        currentAction = null;
    }
}
