package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceMdcHelper;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.model.StateTransitionRecord;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
public class CbolStateMachineService {

    private final StateMachine<ConversationState, ConversationFact, CbolStateContext> convSm;

    public CbolStateMachineService() {
        this.convSm = CbolStateMachineRegistry.get(ConversationStateMachineFactory.MACHINE_ID);
    }

    /**
     * Fires a conversation fact event through the state machine.
     *
     * @param ctx  the conversation context (must not be null)
     * @param fact the event to fire (must not be null)
     * @return the state context after the transition
     * @throws NullPointerException if ctx or fact is null
     */
    public StateContext<ConversationState, ConversationFact, CbolStateContext> fire(
            CbolStateContext ctx, ConversationFact fact) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(fact, "fact must not be null");
        Objects.requireNonNull(ctx.conversation(), "ctx.conversation must not be null");
        Objects.requireNonNull(ctx.traceContext(), "ctx.traceContext must not be null");

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