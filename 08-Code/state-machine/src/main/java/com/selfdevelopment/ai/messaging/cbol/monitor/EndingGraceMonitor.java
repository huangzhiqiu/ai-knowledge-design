package com.selfdevelopment.ai.messaging.cbol.monitor;

import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.statemachine.CbolStateMachineService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EndingGraceMonitor {
    private final CbolStateMachineService cbolStateMachineService;

    public void check(CbolStateContext ctx, long enterEndingTs) {
        if (!ConversationState.ENDING.equals(ctx.conversation().state())) {
            return;
        }
        long grace = ctx.marketConfig().endingGraceSeconds();
        long now = System.currentTimeMillis();
        boolean hit = (now - enterEndingTs) >= grace * 1000;
        if (hit) {
            cbolStateMachineService.fire(ctx, ConversationFact.SYS_ENDING_GRACE_TIMEOUT);
        }
    }
}