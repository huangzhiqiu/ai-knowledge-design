package com.selfdevelopment.ai.messaging.cbol.monitor;

import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.statemachine.CbolStateMachineService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransferMonitor {
    private final CbolStateMachineService cbolStateMachineService;

    public void check(CbolStateContext ctx, long transferStartTs) {
        if (!ConversationState.TRANSFERRED.equals(ctx.conversation().state())) {
            return;
        }
        long timeout = ctx.marketConfig().transferTimeoutSeconds();
        long now = System.currentTimeMillis();
        boolean hit = (now - transferStartTs) >= timeout * 1000;
        if (hit) {
            cbolStateMachineService.fire(ctx, ConversationFact.SYS_TRANSFER_TIMEOUT);
        }
    }
}