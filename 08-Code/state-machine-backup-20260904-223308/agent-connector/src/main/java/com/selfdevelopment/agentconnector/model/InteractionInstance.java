package com.selfdevelopment.agentconnector.model;

import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.Builder;

@Builder
public record InteractionInstance(
        String interactionId,
        String conversationId,
        String channelType,
        InteractionState state,
        boolean needReconnect
) {
}