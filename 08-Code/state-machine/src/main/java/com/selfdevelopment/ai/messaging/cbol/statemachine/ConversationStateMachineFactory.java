package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;

public class ConversationStateMachineFactory {

    public static final String MACHINE_ID = "conversation";

    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
        StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                StateMachineBuilder.builder(MACHINE_ID);

        builder.transition()
                .from(ConversationState.INITIATED)
                .on(ConversationFact.CUSTOMER_CONNECT)
                .to(ConversationState.ACTIVE)
                .and();

        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.TRANSFER_REQUEST)
                .to(ConversationState.TRANSFERRED)
                .and();

        // v6: transfer failed -> INITIATED (no rollback)
        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.TRANSFER_FAILED)
                .to(ConversationState.INITIATED)
                .and();

        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.TRANSFER_TIMEOUT)
                .to(ConversationState.INITIATED)
                .and();

        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.CUSTOMER_CLOSE)
                .to(ConversationState.ENDING)
                .and();

        // SYSTEM events (from monitors)
        builder.transition()
                .from(ConversationState.INITIATED)
                .on(ConversationFact.SYS_CUSTOMER_IDLE)
                .to(ConversationState.ENDING)
                .and();

        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.SYS_CUSTOMER_IDLE)
                .to(ConversationState.ENDING)
                .and();

        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.SYS_CUSTOMER_IDLE)
                .to(ConversationState.ENDING)
                .and();

        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.SYS_TRANSFER_TIMEOUT)
                .to(ConversationState.INITIATED)
                .and();

        builder.transition()
                .from(ConversationState.ENDING)
                .on(ConversationFact.SYS_ENDING_GRACE_TIMEOUT)
                .to(ConversationState.CLOSED)
                .and();

        StateMachine<ConversationState, ConversationFact, CbolStateContext> sm = builder.build();
        CbolStateMachineRegistry.register(sm);
        return sm;
    }
}
