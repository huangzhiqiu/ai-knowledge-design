package com.selfdevelopment.chatengine.spring.service;

import com.selfdevelopment.chatengine.action.ConversationActionService;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.chatengine.spring.event.ConversationStateChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;

/**
 * Spring-aware chat engine state machine service that publishes state change events.
 * <p>
 * This service extends {@link ChatEngineStateMachineService} and adds Spring event
 * publishing capabilities. When a state transition occurs, it publishes a
 * {@link ConversationStateChangedEvent} that can be listened to by other Spring components.
 * <p>
 * This allows for:
 * <ul>
 *   <li>Decoupled cross-module communication</li>
 *   <li>Event-driven architecture</li>
 *   <li>Easy integration with other Spring components</li>
 *   <li>Async event processing with @Async</li>
 * </ul>
 */
@Slf4j
@Service
public class SpringChatEngineStateMachineService extends ChatEngineStateMachineService
        implements ApplicationEventPublisherAware {

    private ApplicationEventPublisher eventPublisher;

    /**
     * Creates a new Spring-aware chat engine state machine service.
     * Uses the injected ConversationActionService to build state machines with
     * auto-discovered Actions.
     *
     * @param actionService the conversation action service
     */
    public SpringChatEngineStateMachineService(ConversationActionService actionService) {
        super(actionService);
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }

    /**
     * Fires a conversation fact event through the state machine and publishes
     * a state change event if the transition is successful.
     *
     * @param ctx  the conversation context (must not be null)
     * @param fact the event to fire (must not be null)
     * @return the target state after the transition
     */
    @Override
    public ConversationState fire(CbolStateContext ctx, ConversationFact fact) {
        ConversationState fromState = ctx.conversation().state();
        long start = System.currentTimeMillis();

        try {
            ConversationState toState = super.fire(ctx, fact);
            long duration = System.currentTimeMillis() - start;

            // Publish state changed event
            if (eventPublisher != null) {
                ConversationStateChangedEvent event = new ConversationStateChangedEvent(
                        this,
                        ctx.conversation().conversationId(),
                        fromState,
                        toState,
                        fact,
                        ctx.traceContext() != null ? ctx.traceContext().traceId() : null,
                        duration,
                        true
                );
                eventPublisher.publishEvent(event);
                log.debug("Published ConversationStateChangedEvent: {} -> {} on {}",
                        fromState, toState, fact);
            }

            return toState;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;

            // Publish failed state change event
            if (eventPublisher != null) {
                ConversationStateChangedEvent event = new ConversationStateChangedEvent(
                        this,
                        ctx.conversation().conversationId(),
                        fromState,
                        fromState, // Stay in the same state on failure
                        fact,
                        ctx.traceContext() != null ? ctx.traceContext().traceId() : null,
                        duration,
                        false
                );
                eventPublisher.publishEvent(event);
            }

            throw ex;
        }
    }
}
