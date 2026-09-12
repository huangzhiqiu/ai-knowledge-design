package com.selfdevelopment.agentconnector.spring.service;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService;
import com.selfdevelopment.agentconnector.spring.event.InteractionStateChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

/**
 * Spring-aware agent connector state machine service that publishes state change events.
 * <p>
 * This service extends {@link AgentConnectorStateMachineService} and adds Spring event
 * publishing capabilities. When a state transition occurs, it publishes an
 * {@link InteractionStateChangedEvent} that can be listened to by other Spring components.
 * <p>
 * This allows for:
 * <ul>
 *   <li>Decoupled cross-module communication</li>
 *   <li>Event-driven architecture</li>
 *   <li>Easy integration with other Spring components</li>
 *   <li>Async event processing with @Async</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * {@code @Bean}
 * public SpringAgentConnectorStateMachineService springAgentConnectorStateMachineService() {
 *     return new SpringAgentConnectorStateMachineService();
 * }
 * </pre>
 */
@Slf4j
public class SpringAgentConnectorStateMachineService extends AgentConnectorStateMachineService
        implements ApplicationEventPublisherAware {

    private ApplicationEventPublisher eventPublisher;

    /**
     * Creates a new Spring-aware agent connector state machine service.
     * Uses the default interaction state machine from the factory.
     */
    public SpringAgentConnectorStateMachineService() {
        super();
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }

    /**
     * Fires an interaction fact event through the state machine and publishes
     * a state change event if the transition is successful.
     *
     * @param ctx  the interaction context (must not be null)
     * @param fact the event to fire (must not be null)
     * @return the target state after the transition
     */
    @Override
    public InteractionState fire(AgentConnectorStateContext ctx, InteractionFact fact) {
        InteractionState fromState = ctx.interaction().state();
        long start = System.currentTimeMillis();

        try {
            InteractionState toState = super.fire(ctx, fact);
            long duration = System.currentTimeMillis() - start;

            // Publish state changed event
            if (eventPublisher != null) {
                InteractionStateChangedEvent event = new InteractionStateChangedEvent(
                        this,
                        ctx.interaction().interactionId(),
                        ctx.interaction().conversationId(),
                        fromState,
                        toState,
                        fact,
                        ctx.traceId(),
                        duration,
                        true
                );
                eventPublisher.publishEvent(event);
                log.debug("Published InteractionStateChangedEvent: {} -> {} on {}",
                        fromState, toState, fact);
            }

            return toState;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;

            // Publish failed state change event
            if (eventPublisher != null) {
                InteractionStateChangedEvent event = new InteractionStateChangedEvent(
                        this,
                        ctx.interaction().interactionId(),
                        ctx.interaction().conversationId(),
                        fromState,
                        fromState, // Stay in the same state on failure
                        fact,
                        ctx.traceId(),
                        duration,
                        false
                );
                eventPublisher.publishEvent(event);
            }

            throw ex;
        }
    }
}
