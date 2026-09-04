package com.selfdevelopment.statemachine.core;

import com.selfdevelopment.statemachine.api.StateMachine;

import com.selfdevelopment.statemachine.api.Action;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Domain object representing the current status of a state machine within a transition or action.
 * <p>
 * Inspired by Spring StateMachine's {@code StateContext}. Gives actions and guards access to:
 * <ul>
 *   <li>Source and target states</li>
 *   <li>The triggering event</li>
 *   <li>The business context (type-safe, user-defined)</li>
 *   <li>The extended state (key-value variables shared across transitions)</li>
 *   <li>Event headers (metadata attached to the event)</li>
 *   <li>The exception if the transition failed</li>
 * </ul>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the business context type
 */
@Getter
@Builder
public final class StateContext<S, E, C> {

    private final S sourceState;
    private final S targetState;
    private final E event;
    private final C businessContext;

    @Builder.Default
    private final ExtendedState extendedState = new ExtendedState();

    private final Map<String, Object> eventHeaders;
    private final Exception exception;

    @Builder.Default
    private final boolean transitionAccepted = true;

    @Override
    public String toString() {
        return "StateContext{" + sourceState + " --[" + event + "]--> " + targetState
                + ", accepted=" + transitionAccepted + "}";
    }
}
