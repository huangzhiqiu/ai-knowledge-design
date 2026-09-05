package com.selfdevelopment.agentconnector.action.connection;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.agentconnector.model.InteractionInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionSuccessActionTest {

    private ConnectionSuccessAction action;
    private AgentConnectorStateContext context;
    private InteractionInstance interaction;

    @BeforeEach
    void setUp() {
        action = new ConnectionSuccessAction();
        interaction = InteractionInstance.builder()
                .interactionId("int-test-001")
                .state(InteractionState.INITIATED)
                .channelType("GENESYS")
                .build();
        context = AgentConnectorStateContext.builder()
                .interaction(interaction)
                .build();
    }

    @Test
    void testExecute_ShouldNotThrow() {
        assertDoesNotThrow(() ->
                action.execute(InteractionState.INITIATED, InteractionState.CONNECTED,
                        InteractionFact.CONNECTION_SUCCESS, context));
    }

    @Test
    void testExecute_WithValidContext_ShouldComplete() {
        action.execute(InteractionState.INITIATED, InteractionState.CONNECTED,
                InteractionFact.CONNECTION_SUCCESS, context);

        // Action should not modify the interaction state (COLA handles state transition)
        assertEquals(InteractionState.INITIATED, interaction.state(),
                "Action should not modify state directly");
    }

    @Test
    void testExecute_WithDifferentChannelTypes_ShouldWork() {
        interaction = InteractionInstance.builder()
                .interactionId("int-test-002")
                .state(InteractionState.INITIATED)
                .channelType("AIBOT")
                .build();
        context = AgentConnectorStateContext.builder()
                .interaction(interaction)
                .build();

        assertDoesNotThrow(() ->
                action.execute(InteractionState.INITIATED, InteractionState.CONNECTED,
                        InteractionFact.CONNECTION_SUCCESS, context));
    }
}
