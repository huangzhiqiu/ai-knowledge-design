package com.selfdevelopment.ai.messaging.statemachine.core;

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
public final class StateContext<S, E, C> {

    private final S sourceState;
    private final S targetState;
    private final E event;
    private final C businessContext;
    private final ExtendedState extendedState;
    private final Map<String, Object> eventHeaders;
    private final Exception exception;
    private final boolean transitionAccepted;

    private StateContext(Builder<S, E, C> builder) {
        this.sourceState = builder.sourceState;
        this.targetState = builder.targetState;
        this.event = builder.event;
        this.businessContext = builder.businessContext;
        this.extendedState = builder.extendedState != null ? builder.extendedState : new ExtendedState();
        this.eventHeaders = builder.eventHeaders;
        this.exception = builder.exception;
        this.transitionAccepted = builder.transitionAccepted;
    }

    public S getSourceState() { return sourceState; }
    public S getTargetState() { return targetState; }
    public E getEvent() { return event; }
    public C getBusinessContext() { return businessContext; }
    public ExtendedState getExtendedState() { return extendedState; }
    public Map<String, Object> getEventHeaders() { return eventHeaders; }
    public Exception getException() { return exception; }
    public boolean isTransitionAccepted() { return transitionAccepted; }

    /**
     * Creates a new builder.
     */
    public static <S, E, C> Builder<S, E, C> builder() {
        return new Builder<>();
    }

    /**
     * Builder for {@link StateContext}.
     */
    public static final class Builder<S, E, C> {
        private S sourceState;
        private S targetState;
        private E event;
        private C businessContext;
        private ExtendedState extendedState;
        private Map<String, Object> eventHeaders;
        private Exception exception;
        private boolean transitionAccepted = true;

        public Builder<S, E, C> sourceState(S sourceState) {
            this.sourceState = sourceState;
            return this;
        }

        public Builder<S, E, C> targetState(S targetState) {
            this.targetState = targetState;
            return this;
        }

        public Builder<S, E, C> event(E event) {
            this.event = event;
            return this;
        }

        public Builder<S, E, C> businessContext(C businessContext) {
            this.businessContext = businessContext;
            return this;
        }

        public Builder<S, E, C> extendedState(ExtendedState extendedState) {
            this.extendedState = extendedState;
            return this;
        }

        public Builder<S, E, C> eventHeaders(Map<String, Object> eventHeaders) {
            this.eventHeaders = eventHeaders;
            return this;
        }

        public Builder<S, E, C> exception(Exception exception) {
            this.exception = exception;
            return this;
        }

        public Builder<S, E, C> transitionAccepted(boolean transitionAccepted) {
            this.transitionAccepted = transitionAccepted;
            return this;
        }

        public StateContext<S, E, C> build() {
            return new StateContext<>(this);
        }
    }

    @Override
    public String toString() {
        return "StateContext{" + sourceState + " --[" + event + "]--> " + targetState
                + ", accepted=" + transitionAccepted + "}";
    }
}
