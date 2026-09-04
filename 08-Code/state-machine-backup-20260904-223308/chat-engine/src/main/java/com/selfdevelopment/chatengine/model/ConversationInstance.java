package com.selfdevelopment.chatengine.model;

import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.enums.EndReason;
import com.selfdevelopment.chatengine.enums.TransferOutcome;
import lombok.Builder;

@Builder
public record ConversationInstance(
        String conversationId,
        String market,
        String tenantId,
        ConversationState state,
        EndReason endReason,
        TransferOutcome transferOutcome,
        boolean surveyEnabled,
        boolean surveyCompleted
) {
    /**
     * Returns a new instance with the given state, preserving all other fields.
     *
     * @param newState the new state
     * @return a new ConversationInstance with the updated state
     */
    public ConversationInstance withState(ConversationState newState) {
        return new ConversationInstance(
                conversationId, market, tenantId, newState,
                endReason, transferOutcome, surveyEnabled, surveyCompleted);
    }
}