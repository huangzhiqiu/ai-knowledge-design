package com.selfdevelopment.chatengine.monitor;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;

import java.util.Set;

/**
 * Monitors customer idle time and fires SYS_CUSTOMER_IDLE when the threshold is exceeded.
 * Applies to all non-terminal states (INITIATED, ACTIVE, TRANSFERRED).
 */
public class CustomerIdleMonitor extends AbstractTimeoutMonitor {

    private static final Set<ConversationState> APPLICABLE_STATES = Set.of(
            ConversationState.INITIATED,
            ConversationState.ACTIVE,
            ConversationState.TRANSFERRED
    );

    public CustomerIdleMonitor(ChatEngineStateMachineService ChatEngineStateMachineService) {
        super(ChatEngineStateMachineService);
    }

    @Override
    protected boolean isApplicable(ConversationState state) {
        return APPLICABLE_STATES.contains(state);
    }

    @Override
    protected long timeoutSeconds(CbolStateContext ctx) {
        return ctx.marketConfig().customerIdleSeconds();
    }

    @Override
    protected ConversationFact timeoutEvent() {
        return ConversationFact.SYS_CUSTOMER_IDLE;
    }
}
