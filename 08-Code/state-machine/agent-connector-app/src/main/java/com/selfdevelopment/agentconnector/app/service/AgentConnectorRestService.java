package com.selfdevelopment.agentconnector.app.service;

import com.selfdevelopment.agentconnector.app.dto.InteractionEventRequest;
import com.selfdevelopment.agentconnector.app.dto.InteractionEventResponse;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.agentconnector.model.InteractionInstance;
import com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for handling interaction event requests via REST API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConnectorRestService {

    private final AgentConnectorStateMachineService stateMachineService;

    /**
     * Processes an interaction event request.
     *
     * @param request the event request
     * @return the event response with transition result
     */
    public InteractionEventResponse processEvent(InteractionEventRequest request) {
        String traceId = UUID.randomUUID().toString();
        String interactionId = request.getInteractionId();

        log.info("Processing interaction event: interactionId={}, fact={}, traceId={}",
                interactionId, request.getFact(), traceId);

        try {
            // Build interaction instance with current state
            // In a real application, this would be loaded from database
            InteractionInstance interaction = InteractionInstance.builder()
                    .interactionId(interactionId)
                    .conversationId(request.getConversationId())
                    .channelType(request.getChannelType())
                    .state(InteractionState.INITIATED) // Default state, should be loaded from DB
                    .build();

            // Build state context
            AgentConnectorStateContext context = AgentConnectorStateContext.builder()
                    .interaction(interaction)
                    .market(null) // Market can be set based on conversation
                    .traceId(traceId)
                    .build();

            // Fire event through state machine
            InteractionState fromState = interaction.state();
            InteractionState toState = stateMachineService.fire(context, request.getFact());

            log.info("Interaction event processed: interactionId={}, {} -> {} on {}",
                    interactionId, fromState, toState, request.getFact());

            return InteractionEventResponse.builder()
                    .interactionId(interactionId)
                    .fromState(fromState)
                    .toState(toState)
                    .fact(request.getFact().name())
                    .success(true)
                    .traceId(traceId)
                    .timestamp(Instant.now())
                    .build();

        } catch (Exception ex) {
            log.error("Failed to process interaction event: interactionId={}, fact={}, error={}",
                    interactionId, request.getFact(), ex.getMessage(), ex);

            return InteractionEventResponse.builder()
                    .interactionId(interactionId)
                    .fact(request.getFact() != null ? request.getFact().name() : "UNKNOWN")
                    .success(false)
                    .errorMessage(ex.getMessage())
                    .traceId(traceId)
                    .timestamp(Instant.now())
                    .build();
        }
    }
}
