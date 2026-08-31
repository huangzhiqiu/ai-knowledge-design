package com.selfdevelopment.ai.messaging.cbol.monitor;

import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.statemachine.CbolStateMachineService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CustomerIdleMonitor {
    private final CbolStateMachineService cbolStateMachineService;

    public void check(CbolStateContext ctx, long lastActivityTs) {
        long idleSec = ctx.marketConfig().customerIdleSeconds();
        long now = System.currentTimeMillis();
        boolean hit = (now - lastActivityTs) >= idleSec * 1000;
        if (hit) {
            cbolStateMachineService.fire(ctx, ConversationFact.SYS_CUSTOMER_IDLE);
        }
    }
}