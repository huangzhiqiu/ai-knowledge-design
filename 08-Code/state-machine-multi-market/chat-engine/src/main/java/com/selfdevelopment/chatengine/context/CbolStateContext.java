package com.selfdevelopment.chatengine.context;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import lombok.Builder;

/**
 * Context for chat engine state machine execution.
 * <p>
 * Holds the conversation instance, market configuration, and trace context
 * needed for conversation state transitions.
 * <p>
 * Note: InteractionInstance is intentionally NOT included here.
 * Conversation and Interaction are independent state machines:
 * - Conversation state machine lives in chat-engine
 * - Interaction state machine lives in agent-connector
 * They communicate via events, not by sharing context objects.
 */
@Builder
public record CbolStateContext(
        ConversationInstance conversation,
        StateMachineMarketConfig marketConfig,
        TraceContext traceContext,
        // Failover fields - populated when an action fails and SYS_ACTION_FAILED is triggered
        String failedAction,
        String errorMessage,
        Integer retryCount
) {
}
