package com.selfdevelopment.chatengine.action;

import com.alibaba.cola.statemachine.Action;
import com.alibaba.cola.statemachine.Condition;

/**
 * An {@link Action} that carries an associated {@link Condition} for state machine transitions.
 * <p>
 * This interface allows Actions to define their own execution conditions directly within
 * the Action class, creating a natural binding between condition and action. When used with
 * COLA StateMachine's {@code when()} method, the condition is evaluated before the action
 * executes.
 * <p>
 * Usage:
 * <pre>{@code
 * // Action with a condition
 * @Component
 * @HandlesFact(ConversationFact.SESSION_STARTED)
 * public class SessionStartedAction implements ConditionalAction<...> {
 *
 *     @Override
 *     public Condition<CbolStateContext> getCondition() {
 *         return ctx -> ctx.conversation() != null
 *             && ctx.conversation().sessionId() != null;
 *     }
 *
 *     @Override
 *     public void execute(...) {
 *         // Action logic
 *     }
 * }
 *
 * // Action without a condition (just implement Action directly)
 * @Component
 * @HandlesFact(ConversationFact.INBOUND_MESSAGE_RECEIVED)
 * public class InboundMessageReceivedAction implements Action<...> {
 *     @Override
 *     public void execute(...) {
 *         // Always executes
 *     }
 * }
 * }</pre>
 * <p>
 * In the state machine factory, the condition is extracted automatically:
 * <pre>{@code
 * Action<...> action = actionProvider.apply(fact);
 * Condition<CbolStateContext> condition = (action instanceof ConditionalAction)
 *         ? ((ConditionalAction<...>) action).getCondition()
 *         : null;
 *
 * builder.externalTransition()
 *     .from(from)
 *     .to(to)
 *     .on(fact)
 *     .when(condition)   // null means no condition, always executes
 *     .perform(action);
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 * @see Action
 * @see Condition
 */
public interface ConditionalAction<S, E, C> extends Action<S, E, C> {

    /**
     * Returns the condition associated with this Action.
     * <p>
     * The condition is evaluated by the state machine before executing the Action.
     * If the condition returns {@code false}, the entire transition (including state
     * change and Action execution) is skipped.
     * <p>
     * Returning {@code null} means no condition is required, and the Action will
     * always execute when the transition is triggered.
     *
     * @return the condition to evaluate before execution, or {@code null} if no condition
     */
    Condition<C> getCondition();
}
