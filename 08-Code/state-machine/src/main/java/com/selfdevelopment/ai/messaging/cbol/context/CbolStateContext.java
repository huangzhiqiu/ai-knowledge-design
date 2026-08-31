package com.selfdevelopment.ai.messaging.cbol.context;

import com.selfdevelopment.ai.messaging.cbol.config.StateMachineMarketConfig;
import com.selfdevelopment.ai.messaging.cbol.model.ConversationInstance;
import com.selfdevelopment.ai.messaging.cbol.model.InteractionInstance;
import lombok.Builder;

@Builder
public record CbolStateContext(
        ConversationInstance conversation,
        InteractionInstance interaction,
        StateMachineMarketConfig marketConfig,
        TraceContext traceContext
) {
}