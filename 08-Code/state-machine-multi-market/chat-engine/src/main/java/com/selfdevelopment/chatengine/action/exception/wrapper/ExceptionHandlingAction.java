package com.selfdevelopment.chatengine.action.exception.wrapper;

import com.alibaba.cola.statemachine.Action;
import com.alibaba.cola.statemachine.Condition;
import com.selfdevelopment.chatengine.action.ConditionalAction;
import com.selfdevelopment.chatengine.action.exception.registry.ActionExceptionHandlerRegistry;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/**
 * Wrapper for {@link Action} that handles exceptions during execution.
 * <p>
 * This wrapper catches any exception thrown by the wrapped Action and delegates
 * to the {@link ActionExceptionHandlerRegistry} for handling. Importantly, exceptions
 * do NOT propagate to the state machine engine, ensuring that state transitions
 * continue regardless of Action execution failures.
 * <p>
 * Implements {@link ConditionalAction} and delegates condition evaluation to the
 * wrapped Action if it also implements ConditionalAction; otherwise defaults to
 * always satisfied.
 * <p>
 * Usage:
 * <pre>{@code
 * Action<ConversationState, ConversationFact, CbolStateContext> originalAction = ...;
 * ActionExceptionHandlerRegistry registry = ...;
 * Action<ConversationState, ConversationFact, CbolStateContext> wrappedAction =
 *     new ExceptionHandlingAction(originalAction, registry);
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 * @see com.selfdevelopment.chatengine.action.exception.handler.ActionExceptionHandler
 * @see ActionExceptionHandlerRegistry
 */
@Slf4j
public class ExceptionHandlingAction<S, E, C> implements ConditionalAction<S, E, C> {

    private final Action<S, E, C> delegate;
    private final ActionExceptionHandlerRegistry exceptionHandlerRegistry;

    /**
     * Creates a new exception handling action wrapper.
     *
     * @param delegate                 the original Action to wrap
     * @param exceptionHandlerRegistry the registry for finding exception handlers
     * @throws IllegalArgumentException if delegate or exceptionHandlerRegistry is null
     */
    public ExceptionHandlingAction(Action<S, E, C> delegate,
                                    ActionExceptionHandlerRegistry exceptionHandlerRegistry) {
        Assert.notNull(delegate, "delegate must not be null");
        Assert.notNull(exceptionHandlerRegistry, "exceptionHandlerRegistry must not be null");
        this.delegate = delegate;
        this.exceptionHandlerRegistry = exceptionHandlerRegistry;
    }

    /**
     * Returns the condition from the wrapped Action if it implements {@link ConditionalAction},
     * otherwise returns a condition that is always satisfied.
     *
     * @return the condition to evaluate before execution
     */
    @Override
    @SuppressWarnings("unchecked")
    public Condition<C> getCondition() {
        if (delegate instanceof ConditionalAction) {
            Condition<C> condition = ((ConditionalAction<S, E, C>) delegate).getCondition();
            return condition != null ? condition : ctx -> true;
        }
        return ctx -> true;
    }

    /**
     * Executes the wrapped Action with exception handling.
     * <p>
     * Any exception thrown by the delegate is caught and handled by the
     * {@link ActionExceptionHandlerRegistry}. Exceptions do NOT propagate,
     * ensuring that state transitions continue regardless of Action failures.
     *
     * @param from the source state
     * @param to   the target state
     * @param e    the event that triggered the transition
     * @param ctx  the state machine context
     */
    @Override
    @SuppressWarnings("unchecked")
    public void execute(S from, S to, E e, C ctx) {
        try {
            delegate.execute(from, to, e, ctx);
        } catch (Throwable ex) {
            // Catch ALL exceptions (including Errors) to ensure state transition continues
            log.warn("Action execution threw an exception, handling via registry: {}",
                    ex.getClass().getSimpleName());

            try {
                // Delegate to exception handler registry
                // Cast to conversation-specific types for handler compatibility
                exceptionHandlerRegistry.handleException(
                        ex,
                        (ConversationState) from,
                        (ConversationState) to,
                        (ConversationFact) e,
                        (CbolStateContext) ctx
                );
            } catch (Throwable handlerEx) {
                // Never let handler exceptions propagate - this is the last line of defense
                log.error("Exception handler registry threw an exception while handling {}",
                        ex.getClass().getSimpleName(), handlerEx);
            }

            // IMPORTANT: Do NOT rethrow the exception
            // State transition must continue regardless of Action execution failure
            log.debug("State transition continues despite Action exception: {} -> {} on {}",
                    from, to, e);
        }
    }

    /**
     * Returns the original delegate Action.
     *
     * @return the original Action
     */
    public Action<S, E, C> getDelegate() {
        return delegate;
    }

    /**
     * Wraps an Action with exception handling.
     * <p>
     * Convenience factory method for creating wrapped Actions.
     *
     * @param action                   the original Action to wrap
     * @param exceptionHandlerRegistry the registry for finding exception handlers
     * @param <S>                      the state type
     * @param <E>                      the event type
     * @param <C>                      the context type
     * @return the wrapped Action
     */
    public static <S, E, C> Action<S, E, C> wrap(Action<S, E, C> action,
                                                    ActionExceptionHandlerRegistry exceptionHandlerRegistry) {
        if (action == null) {
            return null;
        }
        if (action instanceof ExceptionHandlingAction) {
            // Already wrapped, return as-is
            return action;
        }
        return new ExceptionHandlingAction<>(action, exceptionHandlerRegistry);
    }
}
