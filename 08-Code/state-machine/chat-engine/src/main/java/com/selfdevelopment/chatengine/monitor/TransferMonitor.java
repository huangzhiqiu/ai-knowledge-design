package com.selfdevelopment.chatengine.monitor;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;

/**
 * Monitors transfer timeout and fires SYS_TRANSFER_TIMEOUT when the threshold is exceeded.
 * Only applies when the conversation is in the TRANSFERRED state.
 */
public class TransferMonitor extends AbstractTimeoutMonitor {

    public TransferMonitor(ChatEngineStateMachineService chatEngineStateMachineService) {
        super(chatEngineStateMachineService);
    }

    @Override
    protected boolean isApplicable(ConversationState state) {
        return ConversationState.TRANSFERRED.equals(state);
    }

    @Override
    protected long timeoutSeconds(CbolStateContext ctx) {
        return ctx.marketConfig().transferTimeoutSeconds();
    }

    @Override
    protected ConversationFact timeoutEvent() {
        return ConversationFact.SYS_TRANSFER_TIMEOUT;
    }
}
