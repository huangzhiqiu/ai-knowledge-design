package com.selfdevelopment.chatengine.monitor;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;

/**
 * Monitors ending grace period and fires SYS_ENDING_GRACE_TIMEOUT when the threshold is exceeded.
 * Only applies when the conversation is in the ENDING state.
 */
public class EndingGraceMonitor extends AbstractTimeoutMonitor {

    public EndingGraceMonitor(ChatEngineStateMachineService chatEngineStateMachineService) {
        super(chatEngineStateMachineService);
    }

    @Override
    protected boolean isApplicable(ConversationState state) {
        return ConversationState.ENDING.equals(state);
    }

    @Override
    protected long timeoutSeconds(CbolStateContext ctx) {
        return ctx.marketConfig().endingGraceSeconds();
    }

    @Override
    protected ConversationFact timeoutEvent() {
        return ConversationFact.SYS_ENDING_GRACE_TIMEOUT;
    }
}
