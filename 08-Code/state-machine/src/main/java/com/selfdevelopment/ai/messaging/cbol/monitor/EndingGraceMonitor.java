package com.selfdevelopment.ai.messaging.cbol.monitor;

import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.service.CbolStateMachineService;

/**
 * Monitors ending grace period and fires SYS_ENDING_GRACE_TIMEOUT when the threshold is exceeded.
 * Only applies when the conversation is in the ENDING state.
 */
public class EndingGraceMonitor extends AbstractTimeoutMonitor {

    public EndingGraceMonitor(CbolStateMachineService cbolStateMachineService) {
        super(cbolStateMachineService);
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
