package com.selfdevelopment.chatengine.spring.event;

import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring application event published when a conversation state transition occurs.
 * <p>
 * This event can be listened to by other Spring components for:
 * <ul>
 *   <li>State transition logging and auditing</li>
 *   <li>Triggering downstream actions (e.g., notifications, metrics)</li>
 *   <li>Cross-module communication (e.g., notifying agent-connector)</li>
 *   <li>Event sourcing and CQRS</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * {@code @EventListener}
 * public void onConversationStateChanged(ConversationStateChangedEvent event) {
 *     log.info("Conversation {} changed from {} to {} on {}",
 *         event.getConversationId(), event.getFromState(),
 *         event.getToState(), event.getFact());
 * }
 * </pre>
 */
@Getter
public class ConversationStateChangedEvent extends ApplicationEvent {

    private final String conversationId;
    private final ConversationState fromState;
    private final ConversationState toState;
    private final ConversationFact fact;
    private final String traceId;
    private final long durationMs;
    private final boolean success;

    /**
     * Creates a new conversation state changed event.
     *
     * @param source         the object that published the event
     * @param conversationId the conversation ID
     * @param fromState      the previous state
     * @param toState        the new state
     * @param fact           the event/fact that triggered the transition
     * @param traceId        the trace ID for this request
     * @param durationMs     the duration of the transition in milliseconds
     * @param success        whether the transition was successful
     */
    public ConversationStateChangedEvent(
            Object source,
            String conversationId,
            ConversationState fromState,
            ConversationState toState,
            ConversationFact fact,
            String traceId,
            long durationMs,
            boolean success) {
        super(source);
        this.conversationId = conversationId;
        this.fromState = fromState;
        this.toState = toState;
        this.fact = fact;
        this.traceId = traceId;
        this.durationMs = durationMs;
        this.success = success;
    }
}
