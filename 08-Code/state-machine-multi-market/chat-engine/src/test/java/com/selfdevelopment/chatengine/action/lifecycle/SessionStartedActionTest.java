package com.selfdevelopment.chatengine.action.lifecycle;

import com.selfdevelopment.chatengine.action.actions.lifecycle.SessionStartedAction;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionStartedActionTest {

    private SessionStartedAction action;
    private CbolStateContext context;
    private ConversationInstance conversation;

    @BeforeEach
    void setUp() {
        action = new SessionStartedAction();
        conversation = ConversationInstance.builder()
                .conversationId("conv-test-001")
                .state(ConversationState.NEW)
                .market("SG")
                .build();
        context = CbolStateContext.builder()
                .conversation(conversation)
                .build();
    }

    @Test
    void testExecute_ShouldNotThrow() {
        assertDoesNotThrow(() ->
                action.execute(ConversationState.NEW, ConversationState.INITIATED,
                        ConversationFact.SESSION_STARTED, context));
    }

    @Test
    void testExecute_WithDifferentMarkets_ShouldWork() {
        String[] markets = {"HK", "SG", "UK", "US"};

        for (String market : markets) {
            conversation = ConversationInstance.builder()
                    .conversationId("conv-test-" + market)
                    .state(ConversationState.NEW)
                    .market(market)
                    .build();
            context = CbolStateContext.builder()
                    .conversation(conversation)
                    .build();

            assertDoesNotThrow(() ->
                    action.execute(ConversationState.NEW, ConversationState.INITIATED,
                            ConversationFact.SESSION_STARTED, context),
                    "SessionStartedAction should work for market: " + market);
        }
    }
}
