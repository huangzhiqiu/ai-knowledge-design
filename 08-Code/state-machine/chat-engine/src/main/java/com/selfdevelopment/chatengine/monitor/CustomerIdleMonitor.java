package com.selfdevelopment.chatengine.monitor;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;

import java.util.Set;

/**
 * Monitors customer idle time and fires SYS_CUSTOMER_IDLE when the threshold is exceeded.
 * Applies to all non-terminal states (INITIATED, IN_PROGRESS, TRANSFERRED).
 */
public class CustomerIdleMonitor extends AbstractTimeoutMonitor {

    private static final Set<ConversationState> APPLICABLE_STATES = Set.of(
            ConversationState.INITIATED,
            ConversationState.IN_PROGRESS,
            ConversationState.TRANSFERRED
    );

    public CustomerIdleMonitor(ChatEngineStateMachineService chatEngineStateMachineService) {
        super(chatEngineStateMachineService);
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
