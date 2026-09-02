package com.selfdevelopment.chatengine.model;

import lombok.Builder;

/**
 * Simplified interaction instance for chat-engine context.
 * <p>
 * This is a lightweight representation used in chat-engine tests and contexts.
 * The full interaction state machine lives in the agent-connector module.
 */
@Builder
public record InteractionInstance(
        String interactionId,
        String conversationId,
        String channelType,
        String state,
        boolean needReconnect
) {
}
