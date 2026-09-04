package com.selfdevelopment.chatengine.ingress;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Chat engine event dispatcher that routes events through the conversation state machine.
 * <p>
 * <b>RESERVED CODE - Currently not used in production flow.</b>
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
 * // Dispatch a normalized event
 * dispatcher.dispatch(fact, context);
 * }</pre>
 */
public class ChatEngineEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChatEngineEventDispatcher.class);

    private final ChatEngineStateMachineService stateMachineService;
    private final Function<AibotEventNormalizer.NormalizedEvent, CbolStateContext> contextBuilder;

    /**
     * Creates a chat engine event dispatcher.
     *
     * @param stateMachineService the chat engine state machine service
     * @param contextBuilder      function to build CbolStateContext from a normalized event
     */
    public ChatEngineEventDispatcher(ChatEngineStateMachineService stateMachineService,
                                      Function<AibotEventNormalizer.NormalizedEvent, CbolStateContext> contextBuilder) {
        this.stateMachineService = Objects.requireNonNull(stateMachineService, "stateMachineService must not be null");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
    }

    /**
     * Dispatches a normalized event through the conversation state machine.
     *
     * @param event the normalized event to dispatch
     */
    public void dispatch(AibotEventNormalizer.NormalizedEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        CbolStateContext context = contextBuilder.apply(event);
        handleEvent(event.fact(), context);
    }

    /**
     * Handles an event by routing through the conversation state machine.
     *
     * @param fact    the conversation fact to fire
     * @param context the conversation context
     */
    protected void handleEvent(ConversationFact fact, CbolStateContext context) {
        if (context.traceContext() != null) {
            context.traceContext().traceId(); // ensure trace context is populated
        }

        log.debug("Chat engine event processed: fact={}, conversationId={}",
                fact, context.conversation() != null ? context.conversation().conversationId() : "unknown");

        // Fire event through state machine
        stateMachineService.fire(context, fact);
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
