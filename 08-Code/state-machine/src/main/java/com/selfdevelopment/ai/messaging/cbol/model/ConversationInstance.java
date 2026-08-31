package com.selfdevelopment.ai.messaging.cbol.model;

import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.enums.EndReason;
import com.selfdevelopment.ai.messaging.cbol.enums.TransferOutcome;
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
}