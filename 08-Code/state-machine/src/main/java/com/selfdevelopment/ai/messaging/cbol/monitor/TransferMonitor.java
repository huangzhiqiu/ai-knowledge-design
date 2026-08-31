package com.selfdevelopment.ai.messaging.cbol.monitor;

import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.statemachine.CbolStateMachineService;

/**
 * Monitors transfer timeout and fires SYS_TRANSFER_TIMEOUT when the threshold is exceeded.
 * Only applies when the conversation is in the TRANSFERRED state.
 */
public class TransferMonitor extends AbstractTimeoutMonitor {

    public TransferMonitor(CbolStateMachineService cbolStateMachineService) {
        super(cbolStateMachineService);
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
