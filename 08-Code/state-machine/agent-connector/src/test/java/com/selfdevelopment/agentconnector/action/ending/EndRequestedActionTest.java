package com.selfdevelopment.agentconnector.action.ending;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.agentconnector.model.InteractionInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndRequestedActionTest {

    private EndRequestedAction action;
    private AgentConnectorStateContext context;
    private InteractionInstance interaction;

    @BeforeEach
    void setUp() {
        action = new EndRequestedAction();
        interaction = InteractionInstance.builder()
                .interactionId("int-test-001")
                .state(InteractionState.IN_PROGRESS)
                .channelType("GENESYS")
                .build();
        context = AgentConnectorStateContext.builder()
                .interaction(interaction)
                .build();
    }

    @Test
    void testExecute_ShouldNotThrow() {
        assertDoesNotThrow(() ->
                action.execute(InteractionState.IN_PROGRESS, InteractionState.CLOSED,
                        InteractionFact.END_REQUESTED, context));
    }

    @Test
    void testExecute_FromDifferentStates_ShouldWork() {
        InteractionState[] fromStates = {
                InteractionState.CONNECTED,
                InteractionState.IN_PROGRESS,
                InteractionState.DEGRADED,
                InteractionState.RECONNECTING,
                InteractionState.TRANSFERRED,
                InteractionState.CONSULT_TRANSFER
        };

        for (InteractionState fromState : fromStates) {
            interaction = InteractionInstance.builder()
                    .interactionId("int-test-" + fromState)
                    .state(fromState)
                    .channelType("GENESYS")
                    .build();
            context = AgentConnectorStateContext.builder()
                    .interaction(interaction)
                    .build();

            assertDoesNotThrow(() ->
                    action.execute(fromState, InteractionState.CLOSED,
                            InteractionFact.END_REQUESTED, context),
                    "EndRequestedAction should work from state: " + fromState);
        }
    }
}
