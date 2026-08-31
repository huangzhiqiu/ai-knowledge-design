package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceMdcHelper;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.model.StateTransitionRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CbolStateMachineService {

    private final StateMachine<ConversationState, ConversationFact, CbolStateContext> convSm;

    public CbolStateMachineService() {
        convSm = CbolStateMachineRegistry.get(ConversationStateMachineFactory.MACHINE_ID);
    }

    public StateContext<ConversationState, ConversationFact, CbolStateContext> fire(
            CbolStateContext ctx, ConversationFact fact) {
        TraceMdcHelper.set(ctx.traceContext());
        long start = System.currentTimeMillis();
        try {
            ConversationState from = ctx.conversation().state();
            StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                    convSm.fireEvent(from, fact, ctx);

            StateTransitionRecord record = StateTransitionRecord.builder()
                    .businessId(ctx.conversation().conversationId())
                    .fromState(from.name())
                    .toState(result.getTargetState().name())
                    .fact(fact.name())
                    .guardResult(result.isTransitionAccepted())
                    .timestampMs(System.currentTimeMillis())
                    .traceId(ctx.traceContext().traceId())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
            log.info("StateTransitionRecord: {}", record);
            return result;
        } finally {
            TraceMdcHelper.clear();
        }
    }

    /**
     * Convenience method that returns only the target state.
     */
    public ConversationState fireAndGetState(CbolStateContext ctx, ConversationFact fact) {
        return fire(ctx, fact).getTargetState();
    }
}