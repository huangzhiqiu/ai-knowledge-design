package com.selfdevelopment.agentconnector.statemachine;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.agentconnector.model.InteractionInstance;
import com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService;
import com.selfdevelopment.agentconnector.statemachine.factory.InteractionStateMachineFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the Interaction state machine.
 * <p>
 * Based on Event-Driven Orchestration Design (v4.0).
 */
class InteractionStateMachineTest {

    private static AgentConnectorStateMachineService service;

    @BeforeAll
    static void setUp() {
        InteractionStateMachineFactory.create();
        service = new AgentConnectorStateMachineService();
    }

    private AgentConnectorStateContext buildCtx(InteractionState state) {
        InteractionInstance interaction = InteractionInstance.builder()
                .interactionId("int-test-001")
                .conversationId("conv-test-001")
                .channelType("GENESYS")
                .state(state)
                .build();
        return AgentConnectorStateContext.builder()
                .interaction(interaction)
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    // ===== Connection Lifecycle =====

    @Test
    void testInitiatedToConnected() {
        InteractionState result = service.fire(buildCtx(InteractionState.INITIATED), InteractionFact.CONNECTION_SUCCESS);
        assertEquals(InteractionState.CONNECTED, result);
    }

    @Test
    void testInitiatedToClosedOnConnectionFail() {
        InteractionState result = service.fire(buildCtx(InteractionState.INITIATED), InteractionFact.CONNECTION_FAIL);
        assertEquals(InteractionState.CLOSED, result);
    }

    // ===== Messaging =====

    @Test
    void testConnectedToInProgress() {
        InteractionState result = service.fire(buildCtx(InteractionState.CONNECTED), InteractionFact.FIRST_INBOUND_MESSAGE_RECEIVED);
        assertEquals(InteractionState.IN_PROGRESS, result);
    }

    @Test
    void testInProgressInternalOnInboundMessage() {
        InteractionState result = service.fire(buildCtx(InteractionState.IN_PROGRESS), InteractionFact.INBOUND_MESSAGE_RECEIVED);
        assertEquals(InteractionState.IN_PROGRESS, result);
    }

    // ===== Heartbeat & Degradation =====

    @Test
    void testConnectedToDegradedOnHeartbeatMiss() {
        InteractionState result = service.fire(buildCtx(InteractionState.CONNECTED), InteractionFact.HEARTBEAT_MISS);
        assertEquals(InteractionState.DEGRADED, result);
    }

    @Test
    void testInProgressToDegradedOnHeartbeatMiss() {
        InteractionState result = service.fire(buildCtx(InteractionState.IN_PROGRESS), InteractionFact.HEARTBEAT_MISS);
        assertEquals(InteractionState.DEGRADED, result);
    }

    @Test
    void testDegradedToConnectedOnHeartbeatRestored() {
        InteractionState result = service.fire(buildCtx(InteractionState.DEGRADED), InteractionFact.HEARTBEAT_RESTORED);
        assertEquals(InteractionState.CONNECTED, result);
    }

    // ===== Reconnection =====

    @Test
    void testDegradedToReconnecting() {
        InteractionState result = service.fire(buildCtx(InteractionState.DEGRADED), InteractionFact.RECONNECT_ATTEMPT);
        assertEquals(InteractionState.RECONNECTING, result);
    }

    @Test
    void testReconnectingToInProgress() {
        // COLA state machine matches the last defined transition for the same event
        InteractionState result = service.fire(buildCtx(InteractionState.RECONNECTING), InteractionFact.RECONNECT_SUCCESS);
        assertEquals(InteractionState.IN_PROGRESS, result);
    }

    @Test
    void testReconnectingToClosedOnFail() {
        InteractionState result = service.fire(buildCtx(InteractionState.RECONNECTING), InteractionFact.RECONNECT_FAIL);
        assertEquals(InteractionState.CLOSED, result);
    }

    // ===== Genesys Consult Transfer (GENESYS ONLY) =====

    @Test
    void testInProgressToConsultTransfer() {
        InteractionState result = service.fire(buildCtx(InteractionState.IN_PROGRESS), InteractionFact.CONSULT_TRANSFER_STARTED);
        assertEquals(InteractionState.CONSULT_TRANSFER, result);
    }

    @Test
    void testConsultTransferToInProgress() {
        InteractionState result = service.fire(buildCtx(InteractionState.CONSULT_TRANSFER), InteractionFact.CONSULT_TRANSFER_ENDED);
        assertEquals(InteractionState.IN_PROGRESS, result);
    }

    // ===== Cross-Channel Transfer (source detach marker) =====

    @Test
    void testInProgressToTransferred() {
        InteractionState result = service.fire(buildCtx(InteractionState.IN_PROGRESS), InteractionFact.TRANSFER_SUCCESS);
        assertEquals(InteractionState.TRANSFERRED, result);
    }

    @Test
    void testInProgressInternalOnTransferFailed() {
        InteractionState result = service.fire(buildCtx(InteractionState.IN_PROGRESS), InteractionFact.TRANSFER_FAILED);
        assertEquals(InteractionState.IN_PROGRESS, result);
    }

    // ===== Ending (graceful closure) =====

    @Test
    void testConnectedToClosedOnEndRequested() {
        InteractionState result = service.fire(buildCtx(InteractionState.CONNECTED), InteractionFact.END_REQUESTED);
        assertEquals(InteractionState.CLOSED, result);
    }

    @Test
    void testInProgressToClosedOnEndRequested() {
        InteractionState result = service.fire(buildCtx(InteractionState.IN_PROGRESS), InteractionFact.END_REQUESTED);
        assertEquals(InteractionState.CLOSED, result);
    }

    @Test
    void testTransferredToClosedOnEndRequested() {
        InteractionState result = service.fire(buildCtx(InteractionState.TRANSFERRED), InteractionFact.END_REQUESTED);
        assertEquals(InteractionState.CLOSED, result);
    }

    // ===== System Error (unrecoverable) =====

    @Test
    void testInProgressToClosedOnSystemError() {
        InteractionState result = service.fire(buildCtx(InteractionState.IN_PROGRESS), InteractionFact.SYSTEM_ERROR);
        assertEquals(InteractionState.CLOSED, result);
    }

    @Test
    void testConnectedToClosedOnSystemError() {
        InteractionState result = service.fire(buildCtx(InteractionState.CONNECTED), InteractionFact.SYSTEM_ERROR);
        assertEquals(InteractionState.CLOSED, result);
    }

    // ===== Downstream Unavailability =====

    @Test
    void testConnectedToDegradedOnDownstreamUnavailable() {
        InteractionState result = service.fire(buildCtx(InteractionState.CONNECTED), InteractionFact.DOWNSTREAM_UNAVAILABLE);
        assertEquals(InteractionState.DEGRADED, result);
    }

    @Test
    void testInProgressToDegradedOnDownstreamUnavailable() {
        InteractionState result = service.fire(buildCtx(InteractionState.IN_PROGRESS), InteractionFact.DOWNSTREAM_UNAVAILABLE);
        assertEquals(InteractionState.DEGRADED, result);
    }
}
