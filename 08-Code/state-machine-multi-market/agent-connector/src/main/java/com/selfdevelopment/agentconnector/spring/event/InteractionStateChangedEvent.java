package com.selfdevelopment.agentconnector.spring.event;

import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring application event published when an interaction state transition occurs.
 * <p>
 * This event can be listened to by other Spring components for:
 * <ul>
 *   <li>State transition logging and auditing</li>
 *   <li>Triggering downstream actions (e.g., notifications, metrics)</li>
 *   <li>Cross-module communication (e.g., notifying chat-engine)</li>
 *   <li>Event sourcing and CQRS</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * {@code @EventListener}
 * public void onInteractionStateChanged(InteractionStateChangedEvent event) {
 *     log.info("Interaction {} changed from {} to {} on {}",
 *         event.getInteractionId(), event.getFromState(),
 *         event.getToState(), event.getFact());
 * }
 * </pre>
 */
@Getter
public class InteractionStateChangedEvent extends ApplicationEvent {

    private final String interactionId;
    private final String conversationId;
    private final InteractionState fromState;
    private final InteractionState toState;
    private final InteractionFact fact;
    private final String traceId;
    private final long durationMs;
    private final boolean success;

    /**
     * Creates a new interaction state changed event.
     *
     * @param source         the object that published the event
     * @param interactionId  the interaction ID
     * @param conversationId the conversation ID associated with this interaction
     * @param fromState      the previous state
     * @param toState        the new state
     * @param fact           the event/fact that triggered the transition
     * @param traceId        the trace ID for this request
     * @param durationMs     the duration of the transition in milliseconds
     * @param success        whether the transition was successful
     */
    public InteractionStateChangedEvent(
            Object source,
            String interactionId,
            String conversationId,
            InteractionState fromState,
            InteractionState toState,
            InteractionFact fact,
            String traceId,
            long durationMs,
            boolean success) {
        super(source);
        this.interactionId = interactionId;
        this.conversationId = conversationId;
        this.fromState = fromState;
        this.toState = toState;
        this.fact = fact;
        this.traceId = traceId;
        this.durationMs = durationMs;
        this.success = success;
    }
}
