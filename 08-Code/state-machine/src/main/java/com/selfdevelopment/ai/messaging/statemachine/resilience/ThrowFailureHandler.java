package com.selfdevelopment.ai.messaging.statemachine.resilience;

import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.core.Transition;
import com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException;

/**
 * Failure handler that throws a {@link StateMachineException} on any failure.
 * <p>
 * This is the default behavior of a plain state machine. Use this when you want
 * failures to propagate immediately to the caller.
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class ThrowFailureHandler<S, E, C> implements FailureHandler<S, E, C> {

    @Override
    public StateContext<S, E, C> handleFailure(FailureType type,
                                                 S sourceState,
                                                 E event,
                                                 C context,
                                                 Transition<S, E, C> transition,
                                                 Exception exception) {
        String message = switch (type) {
            case NO_TRANSITION -> String.format("No transition found: state=%s, event=%s", sourceState, event);
            case GUARD_FAILED -> String.format("Guard condition failed: state=%s, event=%s", sourceState, event);
            case ACTION_ERROR -> String.format("Action execution failed: state=%s, event=%s, error=%s",
                    sourceState, event, exception != null ? exception.getMessage() : "unknown");
        };

        if (exception != null) {
            throw new StateMachineException(message, exception);
        }
        throw new StateMachineException(message);
    }
}
