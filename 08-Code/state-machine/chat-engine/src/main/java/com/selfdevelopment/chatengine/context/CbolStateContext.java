package com.selfdevelopment.chatengine.context;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.chatengine.model.InteractionInstance;
import lombok.Builder;

/**
 * Context for chat engine state machine execution.
 * <p>
 * Holds the conversation instance, interaction instance (simplified),
 * market configuration, and trace context needed for conversation state transitions.
 */
@Builder
public record CbolStateContext(
        ConversationInstance conversation,
        InteractionInstance interaction,
        StateMachineMarketConfig marketConfig,
        TraceContext traceContext
) {
}
