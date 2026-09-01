package com.selfdevelopment.ai.messaging.cbol.connector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for all connector implementations.
 */
class ConnectorTest {

    private AibotConnector aibotConnector;
    private GenesysConnector genesysConnector;
    private CbolWebsocketConnector websocketConnector;
    private ChatHistoryOdsConnector chatHistoryConnector;

    @BeforeEach
    void setUp() {
        aibotConnector = new AibotConnector("http://localhost:8080/aibot", "test-key");
        genesysConnector = new GenesysConnector("org-1", "client-1");
        websocketConnector = new CbolWebsocketConnector();
        chatHistoryConnector = new ChatHistoryOdsConnector();

        aibotConnector.init();
        genesysConnector.init();
        websocketConnector.init();
        chatHistoryConnector.init();
    }

    @AfterEach
    void tearDown() {
        aibotConnector.shutdown();
        genesysConnector.shutdown();
        websocketConnector.shutdown();
        chatHistoryConnector.shutdown();
    }

    // ===== AibotConnector tests =====

    @Test
    void shouldReturnAibotName() {
        assertEquals("AIBOT", aibotConnector.getName());
    }

    @Test
    void shouldSendMessage() {
        Optional<Connector.ConnectorResponse> response =
                aibotConnector.sendMessage("conv-1", "hello", "user-1");

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
        assertEquals("conv-1", response.get().data().get("conversationId"));
    }

    @Test
    void shouldTriggerHandoff() {
        Optional<Connector.ConnectorResponse> response =
                aibotConnector.triggerHandoff("conv-1", "user_requested");

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
    }

    @Test
    void shouldEndSession() {
        Optional<Connector.ConnectorResponse> response =
                aibotConnector.endSession("conv-1");

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
    }

    @Test
    void shouldBeHealthy() {
        assertTrue(aibotConnector.isHealthy());
    }

    // ===== GenesysConnector tests =====

    @Test
    void shouldReturnGenesysName() {
        assertEquals("GENESYS", genesysConnector.getName());
    }

    @Test
    void shouldRouteToQueue() {
        Optional<Connector.ConnectorResponse> response =
                genesysConnector.routeToQueue("conv-1", "queue-1");

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
    }

    @Test
    void shouldSendAgentMessage() {
        Optional<Connector.ConnectorResponse> response =
                genesysConnector.sendAgentMessage("conv-1", "agent-1", "hello customer");

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
    }

    @Test
    void shouldTransferToAgent() {
        Optional<Connector.ConnectorResponse> response =
                genesysConnector.transferToAgent("conv-1", "agent-1", "agent-2");

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
    }

    @Test
    void shouldEndConversation() {
        Optional<Connector.ConnectorResponse> response =
                genesysConnector.endConversation("conv-1");

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
    }

    // ===== CbolWebsocketConnector tests =====

    @Test
    void shouldReturnWebsocketName() {
        assertEquals("CBOL_WEBSOCKET", websocketConnector.getName());
    }

    @Test
    void shouldRegisterAndCheckSession() {
        assertFalse(websocketConnector.hasActiveSession("customer-1"));

        websocketConnector.registerSession("customer-1", "session-123");
        assertTrue(websocketConnector.hasActiveSession("customer-1"));

        websocketConnector.unregisterSession("customer-1");
        assertFalse(websocketConnector.hasActiveSession("customer-1"));
    }

    @Test
    void shouldPushMessageToActiveSession() {
        websocketConnector.registerSession("customer-1", "session-123");

        Optional<Connector.ConnectorResponse> response =
                websocketConnector.pushMessage("customer-1", "hello", "agent");

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
        assertEquals(true, response.get().data().get("delivered"));
    }

    @Test
    void shouldFailPushToInactiveSession() {
        Optional<Connector.ConnectorResponse> response =
                websocketConnector.pushMessage("nonexistent", "hello", "agent");

        assertTrue(response.isPresent());
        assertFalse(response.get().success());
    }

    @Test
    void shouldSendTypingIndicator() {
        websocketConnector.registerSession("customer-1", "session-123");

        Optional<Connector.ConnectorResponse> response =
                websocketConnector.sendTypingIndicator("customer-1", true, "Agent John");

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
    }

    @Test
    void shouldNotifyAgentJoined() {
        websocketConnector.registerSession("customer-1", "session-123");

        Optional<Connector.ConnectorResponse> response =
                websocketConnector.notifyAgentJoined("customer-1", "Agent John", "agent-1");

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
    }

    // ===== ChatHistoryOdsConnector tests =====

    @Test
    void shouldReturnOdsName() {
        assertEquals("CHAT_HISTORY_ODS", chatHistoryConnector.getName());
    }

    @Test
    void shouldSaveMessage() {
        chatHistoryConnector.saveMessage("conv-1", "user-1", "customer", "hello");

        assertEquals(1, chatHistoryConnector.getRecordCount());
        assertEquals(1, chatHistoryConnector.queryHistory("conv-1").size());
    }

    @Test
    void shouldSaveStateChange() {
        chatHistoryConnector.saveStateChange("conv-1", "INITIATED", "ACTIVE", "USER_MESSAGE");

        assertEquals(1, chatHistoryConnector.getRecordCount());
    }

    @Test
    void shouldSaveTransfer() {
        chatHistoryConnector.saveTransfer("conv-1", "queue-1", "queue-2", "AGENT_TRANSFER");

        assertEquals(1, chatHistoryConnector.getRecordCount());
    }

    @Test
    void shouldQueryHistoryByConversation() {
        chatHistoryConnector.saveMessage("conv-1", "user-1", "customer", "msg1");
        chatHistoryConnector.saveMessage("conv-1", "agent-1", "agent", "msg2");
        chatHistoryConnector.saveMessage("conv-2", "user-2", "customer", "msg3");

        assertEquals(2, chatHistoryConnector.queryHistory("conv-1").size());
        assertEquals(1, chatHistoryConnector.queryHistory("conv-2").size());
        assertEquals(0, chatHistoryConnector.queryHistory("conv-3").size());
    }

    @Test
    void shouldSendViaGenericInterface() {
        Connector.ConnectorRequest request = Connector.ConnectorRequest.of(
                "conv-1", "SAVE_MESSAGE",
                Map.of("message", "test", "senderType", "customer", "senderId", "user-1")
        );

        Optional<Connector.ConnectorResponse> response = chatHistoryConnector.send(request);

        assertTrue(response.isPresent());
        assertTrue(response.get().success());
        assertEquals(1, chatHistoryConnector.getRecordCount());
    }

    // ===== ConnectorException tests =====

    @Test
    void shouldCreateConnectorException() {
        Connector.ConnectorException ex = new Connector.ConnectorException(
                "AIBOT", "SEND_MESSAGE", "Connection failed");

        assertEquals("AIBOT", ex.getConnectorName());
        assertEquals("SEND_MESSAGE", ex.getOperation());
        assertEquals("Connection failed", ex.getMessage());
    }

    @Test
    void shouldCreateConnectorResponse() {
        Connector.ConnectorResponse success = Connector.ConnectorResponse.success(Map.of("key", "value"));
        assertTrue(success.success());
        assertEquals("OK", success.message());

        Connector.ConnectorResponse failure = Connector.ConnectorResponse.failure("Error");
        assertFalse(failure.success());
        assertEquals("Error", failure.message());
    }
}
