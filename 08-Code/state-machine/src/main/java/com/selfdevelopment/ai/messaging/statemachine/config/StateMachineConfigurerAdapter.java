package com.selfdevelopment.ai.messaging.statemachine.config;

import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;

import com.selfdevelopment.ai.messaging.statemachine.api.Guard;

import com.selfdevelopment.ai.messaging.statemachine.api.Action;

/**
 * Adapter class for configuring a state machine.
 * <p>
 * Inspired by Spring StateMachine's {@code EnumStateMachineConfigurerAdapter}.
 * Users extend this class and override the {@code configure} methods to define
 * states and transitions in a clean, declarative style.
 *
 * <pre>{@code
 * public class OrderStateMachineConfig extends StateMachineConfigurerAdapter<OrderState, OrderEvent, OrderContext> {
 *
 *     @Override
 *     public void configure(StateConfigurer<OrderState, OrderEvent, OrderContext> states) {
 *         states.withStates()
 *               .initial(OrderState.CREATED)
 *               .state(OrderState.CREATED, ctx -> log.info("Entering CREATED"), null)
 *               .state(OrderState.PAID, ctx -> sendConfirmation(), null)
 *               .end(OrderState.COMPLETED)
 *               .end(OrderState.CANCELLED);
 *     }
 *
 *     @Override
 *     public void configure(TransitionConfigurer<OrderState, OrderEvent, OrderContext> transitions) {
 *         transitions.withExternal()
 *                    .source(OrderState.CREATED).target(OrderState.PAID).event(OrderEvent.PAY)
 *                    .guard(ctx -> ctx.getBusinessContext().isPaymentValid())
 *                    .action(ctx -> processPayment())
 *                .and().withExternal()
 *                    .source(OrderState.PAID).target(OrderState.SHIPPED).event(OrderEvent.SHIP)
 *                .and().withInternal()
 *                    .source(OrderState.SHIPPED).target(OrderState.SHIPPED).event(OrderEvent.UPDATE_TRACKING)
 *                    .action(ctx -> updateTrackingInfo());
 *     }
 * }
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public abstract class StateMachineConfigurerAdapter<S, E, C> {

    /**
     * Configures the states of the state machine.
     * Override to define states with entry/exit actions, initial state, and end states.
     *
     * @param states the state configurer
     * @throws Exception if configuration fails
     */
    public void configure(StateConfigurer<S, E, C> states) throws Exception {
    }

    /**
     * Configures the transitions of the state machine.
     * Override to define transitions with guards and actions.
     *
     * @param transitions the transition configurer
     * @throws Exception if configuration fails
     */
    public void configure(TransitionConfigurer<S, E, C> transitions) throws Exception {
    }
}
