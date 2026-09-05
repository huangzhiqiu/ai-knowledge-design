package com.selfdevelopment.chatengine.app.service;

import com.selfdevelopment.chatengine.app.dto.ConversationEventRequest;
import com.selfdevelopment.chatengine.app.dto.ConversationEventResponse;
import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for handling conversation event requests via REST API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatEngineRestService {

    private final ChatEngineStateMachineService stateMachineService;

    /**
     * Processes a conversation event request.
     *
     * @param request the event request
     * @return the event response with transition result
     */
    public ConversationEventResponse processEvent(ConversationEventRequest request) {
        TraceContext traceContext = TraceContext.generate();
        String conversationId = request.getConversationId();

        log.info("Processing conversation event: conversationId={}, fact={}, traceId={}",
                conversationId, request.getFact(), traceContext.traceId());

        try {
            // Build conversation instance with current state
            // In a real application, this would be loaded from database
            ConversationInstance conversation = ConversationInstance.builder()
                    .conversationId(conversationId)
                    .market(request.getMarket())
                    .tenantId(request.getTenantId())
                    .state(ConversationState.NEW) // Default state, should be loaded from DB
                    .build();

            // Build market config
            // In a real application, this would be loaded from config based on market
            StateMachineMarketConfig marketConfig = StateMachineMarketConfig.defaultConfig();

            // Build state context
            CbolStateContext context = CbolStateContext.builder()
                    .conversation(conversation)
                    .marketConfig(marketConfig)
                    .traceContext(traceContext)
                    .build();

            // Fire event through state machine
            ConversationState fromState = conversation.state();
            ConversationState toState = stateMachineService.fire(context, request.getFact());

            log.info("Conversation event processed: conversationId={}, {} -> {} on {}",
                    conversationId, fromState, toState, request.getFact());

            return ConversationEventResponse.builder()
                    .conversationId(conversationId)
                    .fromState(fromState)
                    .toState(toState)
                    .fact(request.getFact().name())
                    .success(true)
                    .traceId(traceContext.traceId())
                    .timestamp(Instant.now())
                    .build();

        } catch (Exception ex) {
            log.error("Failed to process conversation event: conversationId={}, fact={}, error={}",
                    conversationId, request.getFact(), ex.getMessage(), ex);

            return ConversationEventResponse.builder()
                    .conversationId(conversationId)
                    .fact(request.getFact() != null ? request.getFact().name() : "UNKNOWN")
                    .success(false)
                    .errorMessage(ex.getMessage())
                    .traceId(traceContext.traceId())
                    .timestamp(Instant.now())
                    .build();
        }
    }
}
