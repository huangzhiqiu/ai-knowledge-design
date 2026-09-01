package com.selfdevelopment.ai.messaging.statemachine.resilience;

import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.core.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Failure handler that transitions to a configured fallback state on failure.
 * <p>
 * Instead of throwing or staying in the source state, this handler moves the
 * entity to a predefined fallback state (e.g., ERROR, DEAD_LETTER).
 * <p>
 * Use this when:
 * <ul>
 *   <li>Failed transitions should route to an error handling state</li>
 *   <li>You want a "dead letter" pattern for unprocessable events</li>
 *   <li>Failed entities need to be quarantined for manual review</li>
 * </ul>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class FallbackStateFailureHandler<S, E, C> implements FailureHandler<S, E, C> {

    private static final Logger log = LoggerFactory.getLogger(FallbackStateFailureHandler.class);

    private final S fallbackState;

    /**
     * Creates a handler that transitions to the given fallback state on failure.
     *
     * @param fallbackState the state to transition to on failure
     */
    public FallbackStateFailureHandler(S fallbackState) {
        this.fallbackState = Objects.requireNonNull(fallbackState, "fallbackState must not be null");
    }

    @Override
    public StateContext<S, E, C> handleFailure(FailureType type,
                                                 S sourceState,
                                                 E event,
                                                 C context,
                                                 Transition<S, E, C> transition,
                                                 Exception exception) {
        log.warn("State transition failed, routing to fallback state: type={}, from={}, to={}, event={}, reason={}",
                type, sourceState, fallbackState, event,
                exception != null ? exception.getMessage() : "no transition/guard");

        return StateContext.<S, E, C>builder()
                .sourceState(sourceState)
                .targetState(fallbackState)
                .event(event)
                .businessContext(context)
                .transitionAccepted(true)  // accepted as fallback transition
                .exception(exception)
                .build();
    }

    /**
     * Returns the fallback state.
     */
    public S getFallbackState() {
        return fallbackState;
    }
}
