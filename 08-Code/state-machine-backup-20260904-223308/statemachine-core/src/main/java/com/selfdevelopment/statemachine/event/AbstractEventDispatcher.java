package com.selfdevelopment.statemachine.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Abstract base class for event dispatchers that route standard events through a state machine.
 * <p>
 * Subclasses implement {@link #handleEvent(StandardEvent, Object)} to provide business-specific
 * event processing logic (e.g., firing state machine events, persisting state, executing actions).
 * <p>
 * This class provides:
 * <ul>
 *   <li>Event dispatch with logging and exception handling</li>
 *   <li>Context building from StandardEvent via a pluggable function</li>
 *   <li>Interceptor registration for cross-cutting concerns (logging, metrics, tracing)</li>
 *   <li>Access to the underlying {@link EventDispatcher} for advanced configuration</li>
 * </ul>
 *
 * @param <C> the business context type (e.g., CbolStateContext, AgentConnectorStateContext)
 */
public abstract class AbstractEventDispatcher<C> {

    private static final Logger log = LoggerFactory.getLogger(AbstractEventDispatcher.class);

    protected final EventDispatcher dispatcher;
    protected final Function<StandardEvent, C> contextBuilder;

    /**
     * Creates an abstract event dispatcher.
     *
     * @param contextBuilder function to build business context from a StandardEvent
     */
    protected AbstractEventDispatcher(Function<StandardEvent, C> contextBuilder) {
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
        this.dispatcher = new EventDispatcher();
        this.dispatcher.setDefaultHandler(this::dispatchInternal);
    }

    /**
     * Dispatches a standard event through the state machine pipeline.
     *
     * @param event the standard event to dispatch
     * @throws NullPointerException if event is null
     */
    public void dispatch(StandardEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        log.info("Dispatching event: type={}, source={}, entityId={}, traceId={}",
                event.getEventType(), event.getSource(), event.getEntityId(), event.getTraceId());

        try {
            dispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to dispatch event: type={}, eventId={}, error={}",
                    event.getEventType(), event.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Internal dispatch handler that builds context and delegates to {@link #handleEvent}.
     */
    private void dispatchInternal(StandardEvent event) {
        C context = contextBuilder.apply(event);
        log.debug("Processing event through state machine: type={}, entityId={}",
                event.getEventType(), event.getEntityId());
        handleEvent(event, context);
    }

    /**
     * Handles a standard event with the built business context.
     * <p>
     * Subclasses implement this method to provide business-specific logic,
     * such as firing state machine events, persisting state, or executing actions.
     *
     * @param event   the standard event
     * @param context the business context built from the event
     */
    protected abstract void handleEvent(StandardEvent event, C context);

    /**
     * Returns the underlying event dispatcher for advanced configuration
     * (registering interceptors, custom handlers, etc.).
     *
     * @return the underlying EventDispatcher instance
     */
    public EventDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * Registers an interceptor for cross-cutting concerns (logging, metrics, tracing).
     *
     * @param name        the interceptor name
     * @param interceptor the interceptor instance
     */
    public void registerInterceptor(String name, EventDispatcher.EventInterceptor interceptor) {
        dispatcher.registerInterceptor(name, interceptor);
    }
}
