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
     * @return a transition builder step
     */
    public TransitionFromStep transition() {
        return new TransitionFromStep();
    }

    /**
     * Builds the state machine from all defined transitions.
     *
     * @return a new, immutable {@link StateMachine} instance
     * @throws StateMachineException if no transitions were defined
     */
    public StateMachine<S, E, C> build() {
        if (transitions.isEmpty()) {
            throw new StateMachineException("At least one transition must be defined");
        }
        return new SimpleStateMachine<>(machineId, new ArrayList<>(transitions));
    }

    // --- Inner builder steps (fluent API) ---

    /**
     * Step: define the source state.
     */
    public final class TransitionFromStep {
        private S sourceState;

        public TransitionOnStep from(S sourceState) {
            this.sourceState = Objects.requireNonNull(sourceState, "sourceState must not be null");
            return new TransitionOnStep();
        }
    }

    /**
     * Step: define the triggering event.
     */
    public final class TransitionOnStep {
        private E event;

        public TransitionToStep on(E event) {
            this.event = Objects.requireNonNull(event, "event must not be null");
            return new TransitionToStep();
        }
    }

    /**
     * Step: define the target state.
     */
    public final class TransitionToStep {
        private S targetState;
        private Condition<C> condition;
        private Action<C> action;

        public TransitionConditionStep to(S targetState) {
            this.targetState = Objects.requireNonNull(targetState, "targetState must not be null");
            return new TransitionConditionStep();
        }

        void register() {
            transitions.add(new Transition<>(
                    TransitionFromStep.this.sourceState,
                    TransitionOnStep.this.event,
                    targetState,
                    condition,
                    action));
        }
    }

    /**
     * Step: optionally define a guard condition.
     */
    public final class TransitionConditionStep {
        public TransitionActionStep when(Condition<C> condition) {
            TransitionToStep.this.condition = Objects.requireNonNull(condition, "condition must not be null");
            return new TransitionActionStep();
        }

        public TransitionActionStep perform(Action<C> action) {
            TransitionToStep.this.action = Objects.requireNonNull(action, "action must not be null");
            return new TransitionActionStep();
        }

        public StateMachineBuilder<S, E, C> and() {
            TransitionToStep.this.register();
            return StateMachineBuilder.this;
        }
    }

    /**
     * Step: optionally define an action.
     */
    public final class TransitionActionStep {
        public TransitionActionStep perform(Action<C> action) {
            TransitionToStep.this.action = Objects.requireNonNull(action, "action must not be null");
            return this;
        }

        public StateMachineBuilder<S, E, C> and() {
            TransitionToStep.this.register();
            return StateMachineBuilder.this;
        }
    }
}
