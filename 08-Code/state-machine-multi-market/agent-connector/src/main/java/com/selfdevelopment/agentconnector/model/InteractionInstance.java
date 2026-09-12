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
    /**
     * Returns a new InteractionInstance with the specified state.
     *
     * @param newState the new state
     * @return a new InteractionInstance with the updated state
     */
    public InteractionInstance withState(InteractionState newState) {
        return new InteractionInstance(
                this.interactionId,
                this.conversationId,
                this.channelType,
                newState,
                this.needReconnect
        );
    }
}