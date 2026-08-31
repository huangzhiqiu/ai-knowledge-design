package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.InteractionState;

public class InteractionStateMachineFactory {

    public static final String MACHINE_ID = "interaction";

    public static StateMachine<InteractionState, ConversationFact, CbolStateContext> build() {
        StateMachineBuilder<InteractionState, ConversationFact, CbolStateContext> builder =
                StateMachineBuilder.builder(MACHINE_ID);
        StateMachine<InteractionState, ConversationFact, CbolStateContext> sm = builder.build();
        CbolStateMachineRegistry.register(sm);
        return sm;
    }
}