package com.selfdevelopment.statemachine.resilience.impl;

import com.selfdevelopment.statemachine.resilience.FailureHandler;

import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.core.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Failure handler that returns the source state (no state change) on failure.
 * <p>
 * Instead of throwing an exception, this handler logs the failure and returns
 * a state context with {@code transitionAccepted=false} and target state = source state.
 * <p>
 * Use this when:
 * <ul>
 *   <li>Invalid events should be silently ignored (e.g., duplicate events)</li>
 *   <li>State machine should never throw for expected business rejections</li>
 *   <li>Caller prefers to check {@code result.isTransitionAccepted()} instead of try-catch</li>
 * </ul>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class ReturnSourceFailureHandler<S, E, C> implements FailureHandler<S, E, C> {

    private static final Logger log = LoggerFactory.getLogger(ReturnSourceFailureHandler.class);

    @Override
    public StateContext<S, E, C> handleFailure(FailureType type,
                                                 S sourceState,
                                                 E event,
                                                 C context,
                                                 Transition<S, E, C> transition,
                                                 Exception exception) {
        log.warn("State transition failed, returning source state: type={}, state={}, event={}, reason={}",
                type, sourceState, event,
                exception != null ? exception.getMessage() : "no transition/guard");

        return StateContext.<S, E, C>builder()
                .sourceState(sourceState)
                .targetState(sourceState)  // no state change
                .event(event)
                .businessContext(context)
                .transitionAccepted(false)
                .exception(exception)
                .build();
    }
}
