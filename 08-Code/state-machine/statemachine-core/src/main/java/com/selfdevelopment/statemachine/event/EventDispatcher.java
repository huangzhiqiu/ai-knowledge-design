package com.selfdevelopment.statemachine.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Dispatches standard events to registered handlers.
 * <p>
 * The dispatcher is the central hub of the event-driven pipeline. It receives
 * normalized {@link StandardEvent}s from multiple sources and routes them to
 * the appropriate state machine handler based on event type or entity type.
 * <p>
 * Features:
 * <ul>
 *   <li>Multiple event source registration (AIBOT, GENESYS, APP_WEB)</li>
 *   <li>Event type-based handler routing</li>
 *   <li>Default handler for unmatched events</li>
 *   <li>Event interceptor chain for cross-cutting concerns (logging, metrics, validation)</li>
 *   <li>Thread-safe registration and dispatch</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * EventDispatcher dispatcher = new EventDispatcher();
 *
 * // Register handlers by event type
 * dispatcher.registerHandler("USER_MESSAGE", event -> {
 *     stateMachine.fireEvent(...);
 * });
 * dispatcher.registerHandler("AGENT_JOIN", event -> { ... });
 *
 * // Register default handler
 * dispatcher.setDefaultHandler(event -> log.warn("Unhandled event: {}", event.getEventType()));
 *
 * // Dispatch events
 * dispatcher.dispatch(standardEvent);
 * }</pre>
 */
public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final Map<String, Consumer<StandardEvent>> handlers = new ConcurrentHashMap<>();
    private final Map<String, EventInterceptor> interceptors = new ConcurrentHashMap<>();
    private volatile Consumer<StandardEvent> defaultHandler = event ->
            log.warn("No handler registered for event type: {}", event.getEventType());

    /**
     * Registers a handler for a specific event type.
     *
     * @param eventType the event type to handle
     * @param handler   the handler consumer
     */
    public void registerHandler(String eventType, Consumer<StandardEvent> handler) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        handlers.put(eventType, handler);
        log.debug("Registered handler for event type: {}", eventType);
    }

    /**
     * Removes the handler for a specific event type.
     *
     * @param eventType the event type
     */
    public void unregisterHandler(String eventType) {
        handlers.remove(eventType);
    }

    /**
     * Sets the default handler for unmatched event types.
     *
     * @param handler the default handler
     */
    public void setDefaultHandler(Consumer<StandardEvent> handler) {
        this.defaultHandler = Objects.requireNonNull(handler, "handler must not be null");
    }

    /**
     * Registers an interceptor that runs before and after event dispatch.
     *
     * @param name        the interceptor name
     * @param interceptor the interceptor
     */
    public void registerInterceptor(String name, EventInterceptor interceptor) {
        interceptors.put(name, interceptor);
    }

    /**
     * Dispatches a standard event to the appropriate handler.
     * <p>
     * The dispatch flow:
     * <ol>
     *   <li>Run all interceptors' beforeDispatch</li>
     *   <li>Find handler by event type, or use default handler</li>
     *   <li>Invoke the handler</li>
     *   <li>Run all interceptors' afterDispatch</li>
     * </ol>
     *
     * @param event the standard event to dispatch
     */
    public void dispatch(StandardEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        log.debug("Dispatching event: type={}, source={}, entityId={}",
                event.getEventType(), event.getSource(), event.getEntityId());

        // Before interceptors
        for (EventInterceptor interceptor : interceptors.values()) {
            try {
                interceptor.beforeDispatch(event);
            } catch (Exception e) {
                log.error("Interceptor beforeDispatch failed: {}", e.getMessage(), e);
            }
        }

        // Find and invoke handler
        Consumer<StandardEvent> handler = handlers.getOrDefault(event.getEventType(), defaultHandler);
        try {
            handler.accept(event);
        } catch (Exception e) {
            log.error("Handler failed for event type={}, eventId={}: {}",
                    event.getEventType(), event.getEventId(), e.getMessage(), e);
            throw e;
        }

        // After interceptors
        for (EventInterceptor interceptor : interceptors.values()) {
            try {
                interceptor.afterDispatch(event);
            } catch (Exception e) {
                log.error("Interceptor afterDispatch failed: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Checks whether a handler is registered for the given event type.
     */
    public boolean hasHandler(String eventType) {
        return handlers.containsKey(eventType);
    }

    /**
     * Returns the number of registered handlers.
     */
    public int getHandlerCount() {
        return handlers.size();
    }

    /**
     * Interceptor for event dispatch, allowing cross-cutting concerns
     * (logging, metrics, validation, tracing) to be applied uniformly.
     */
    public interface EventInterceptor {
        /**
         * Called before the event is dispatched to the handler.
         */
        default void beforeDispatch(StandardEvent event) {}

        /**
         * Called after the event has been dispatched to the handler.
         */
        default void afterDispatch(StandardEvent event) {}

        /**
         * Called when dispatch fails.
         */
        default void onDispatchError(StandardEvent event, Exception error) {}
    }
}
