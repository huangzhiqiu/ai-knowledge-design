package com.selfdevelopment.ai.messaging.cbol.model;

import com.selfdevelopment.ai.messaging.cbol.enums.InteractionState;
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