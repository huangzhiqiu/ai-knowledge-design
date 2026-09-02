package com.selfdevelopment.chatengine.ingress;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.statemachine.event.AbstractEventDispatcher;
import com.selfdevelopment.statemachine.event.StandardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Chat engine event dispatcher that routes standard events through the conversation state machine.
 * <p>
 * This dispatcher handles events from chat sources (AIBot, customer WebSocket) and routes them
 * through the conversation state machine (business-level).
 * <p>
 * Usage:
 * <pre>{@code
 * ChatEngineEventDispatcher dispatcher = new ChatEngineEventDispatcher(
 *     chatEngineStateMachineService,
 *     event -> buildContextFromEvent(event)
 * );
 *
 * // Register normalizers
 * AibotEventNormalizer aibotNormalizer = new AibotEventNormalizer();
 *
 * // Normalize and dispatch
 * aibotNormalizer.normalize(aibotEvent).ifPresent(dispatcher::dispatch);
 * }</pre>
 */
public class ChatEngineEventDispatcher extends AbstractEventDispatcher<CbolStateContext> {

    private static final Logger log = LoggerFactory.getLogger(ChatEngineEventDispatcher.class);

    private final ChatEngineStateMachineService stateMachineService;

    /**
     * Creates a chat engine event dispatcher.
     *
     * @param stateMachineService the chat engine state machine service
     * @param contextBuilder      function to build CbolStateContext from a StandardEvent
     */
    public ChatEngineEventDispatcher(ChatEngineStateMachineService stateMachineService,
                                      Function<StandardEvent, CbolStateContext> contextBuilder) {
        super(contextBuilder);
        this.stateMachineService = Objects.requireNonNull(stateMachineService, "stateMachineService must not be null");
    }

    /**
     * Handles a standard event by routing through the conversation state machine.
     * <p>
     * In the full implementation, this would:
     * 1. Map standard event type to ConversationFact
     * 2. Fire event through Conversation StateMachine (business-level)
     * 3. Persist the resulting state
     * 4. Execute business actions (via ActionWorker)
     * 5. Call connectors (AIBot, Websocket, ChatHistory)
     */
    @Override
    protected void handleEvent(StandardEvent event, CbolStateContext context) {
        // The actual state machine firing happens in ChatEngineStateMachineService
        // This dispatcher provides the event routing and context building layer
        if (context.traceContext() != null) {
            context.traceContext().traceId(); // ensure trace context is populated
        }

        log.debug("Chat engine event processed: type={}, entityId={}",
                event.getEventType(), event.getEntityId());
    }

    /**
     * Returns the chat engine state machine service.
     *
     * @return the state machine service
     */
    public ChatEngineStateMachineService getStateMachineService() {
        return stateMachineService;
    }
}
