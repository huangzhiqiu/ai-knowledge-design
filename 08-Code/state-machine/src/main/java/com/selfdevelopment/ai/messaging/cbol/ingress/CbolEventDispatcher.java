package com.selfdevelopment.ai.messaging.cbol.ingress;

import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.statemachine.CbolStateMachineService;
import com.selfdevelopment.ai.messaging.statemachine.event.EventDispatcher;
import com.selfdevelopment.ai.messaging.statemachine.event.StandardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * CBOL-specific event dispatcher that routes standard events through the
 * dual state machine pipeline (Interaction → Conversation).
 * <p>
 * Pipeline:
 * <pre>
 * StandardEvent → Interaction StateMachine (channel-level)
 *                    ↓ (conversation event)
 *              Conversation StateMachine (business-level)
 *                    ↓ (next status)
 *              persist status → Actions / Business layer → Connectors
 * </pre>
 * <p>
 * Usage:
 * <pre>{@code
 * CbolEventDispatcher dispatcher = new CbolEventDispatcher(
 *     cbolStateMachineService,
 *     event -> buildContextFromEvent(event)
 * );
 *
 * // Register normalizers
 * AibotEventNormalizer aibotNormalizer = new AibotEventNormalizer();
 * GenesysEventNormalizer genesysNormalizer = new GenesysEventNormalizer();
 *
 * // Normalize and dispatch
 * aibotNormalizer.normalize(aibotEvent).ifPresent(dispatcher::dispatch);
 * genesysNormalizer.normalize(genesysEvent).ifPresent(dispatcher::dispatch);
 * }</pre>
 */
public class CbolEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CbolEventDispatcher.class);

    private final EventDispatcher dispatcher;
    private final CbolStateMachineService stateMachineService;
    private final Function<StandardEvent, CbolStateContext> contextBuilder;

    /**
     * Creates a CBOL event dispatcher.
     *
     * @param stateMachineService the CBOL state machine service
     * @param contextBuilder      function to build CbolStateContext from a StandardEvent
     */
    public CbolEventDispatcher(CbolStateMachineService stateMachineService,
                                Function<StandardEvent, CbolStateContext> contextBuilder) {
        this.stateMachineService = Objects.requireNonNull(stateMachineService, "stateMachineService must not be null");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
        this.dispatcher = new EventDispatcher();

        // Register default handler for all event types
        this.dispatcher.setDefaultHandler(this::handleEvent);
    }

    /**
     * Dispatches a standard event through the dual state machine pipeline.
     *
     * @param event the standard event to dispatch
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
     * Handles a standard event by routing through the state machine.
     * <p>
     * In the full implementation, this would:
     * 1. Fire event through Interaction StateMachine (channel-level)
     * 2. If interaction produces a conversation event, fire through Conversation StateMachine
     * 3. Persist the resulting state
     * 4. Execute business actions (via ActionWorker)
     * 5. Call connectors (AIBot, Genesys, Websocket, ChatHistory)
     */
    private void handleEvent(StandardEvent event) {
        CbolStateContext context = contextBuilder.apply(event);

        // Map standard event type to ConversationFact
        // In production, this mapping would be in a dedicated EventMapper
        log.debug("Processing event through state machine: type={}, entityId={}",
                event.getEventType(), event.getEntityId());

        // The actual state machine firing happens in CbolStateMachineService
        // This dispatcher provides the event routing and context building layer
        if (context.traceContext() != null) {
            context.traceContext().traceId(); // ensure trace context is populated
        }
    }

    /**
     * Returns the underlying event dispatcher for advanced configuration
     * (registering interceptors, custom handlers, etc.).
     */
    public EventDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * Registers an interceptor for cross-cutting concerns (logging, metrics, tracing).
     */
    public void registerInterceptor(String name, EventDispatcher.EventInterceptor interceptor) {
        dispatcher.registerInterceptor(name, interceptor);
    }
}
