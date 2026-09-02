package com.selfdevelopment.ai.messaging.cbol.connector.impl;

import com.selfdevelopment.ai.messaging.cbol.connector.Connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connector for customer-facing WebSocket connections.
 * <p>
 * Handles real-time message delivery to customers via WebSocket, including:
 * <ul>
 *   <li>Pushing messages to connected customers</li>
 *   <li>Broadcasting state changes (typing indicator, agent joined, etc.)</li>
 *   <li>Managing customer connection sessions</li>
 *   <li>Handling connection/disconnection events</li>
 * </ul>
 * <p>
 * This is a reference implementation. In production, integrate with the actual
 * WebSocket server (Netty, Spring WebSocket, or custom implementation).
 */
public class CbolWebsocketConnector implements Connector<Connector.ConnectorRequest, Connector.ConnectorResponse> {

    private static final Logger log = LoggerFactory.getLogger(CbolWebsocketConnector.class);

    private final Map<String, String> customerSessions = new ConcurrentHashMap<>();
    private volatile boolean healthy = true;

    @Override
    public String getName() {
        return "CBOL_WEBSOCKET";
    }

    @Override
    public Optional<Connector.ConnectorResponse> send(Connector.ConnectorRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String customerId = request.conversationId();

        log.info("WebSocket connector sending: action={}, customerId={}",
                request.action(), customerId);

        String sessionId = customerSessions.get(customerId);
        if (sessionId == null) {
            log.warn("No active WebSocket session for customer: {}", customerId);
            return Optional.of(Connector.ConnectorResponse.failure("No active session for customer: " + customerId));
        }

        try {
            // In production: send message via WebSocket session
            // session.getBasicRemote().sendText(jsonMessage);

            Map<String, Object> responseData = Map.of(
                    "customerId", customerId,
                    "sessionId", sessionId,
                    "action", request.action(),
                    "delivered", true
            );
            return Optional.of(Connector.ConnectorResponse.success(responseData));

        } catch (Exception e) {
            healthy = false;
            log.error("WebSocket connector failed: action={}, customerId={}, error={}",
                    request.action(), customerId, e.getMessage(), e);
            throw new Connector.ConnectorException("CBOL_WEBSOCKET", request.action(), e.getMessage(), e);
        }
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public void init() {
        log.info("CBOL WebSocket connector initialized");
    }

    @Override
    public void shutdown() {
        customerSessions.clear();
        log.info("CBOL WebSocket connector shut down, {} sessions cleared", customerSessions.size());
    }

    /**
     * Registers a customer WebSocket session.
     */
    public void registerSession(String customerId, String sessionId) {
        customerSessions.put(customerId, sessionId);
        log.debug("Registered WebSocket session: customerId={}, sessionId={}", customerId, sessionId);
    }

    /**
     * Unregisters a customer WebSocket session.
     */
    public void unregisterSession(String customerId) {
        customerSessions.remove(customerId);
        log.debug("Unregistered WebSocket session: customerId={}", customerId);
    }

    /**
     * Checks if a customer has an active WebSocket session.
     */
    public boolean hasActiveSession(String customerId) {
        return customerSessions.containsKey(customerId);
    }

    /**
     * Pushes a message to the customer.
     */
    public Optional<Connector.ConnectorResponse> pushMessage(String customerId, String message, String senderType) {
        return send(Connector.ConnectorRequest.of(customerId, "PUSH_MESSAGE", Map.of(
                "message", message,
                "senderType", senderType
        )));
    }

    /**
     * Sends a typing indicator to the customer.
     */
    public Optional<Connector.ConnectorResponse> sendTypingIndicator(String customerId, boolean isTyping, String agentName) {
        return send(Connector.ConnectorRequest.of(customerId, "TYPING_INDICATOR", Map.of(
                "isTyping", isTyping,
                "agentName", agentName != null ? agentName : ""
        )));
    }

    /**
     * Notifies the customer that an agent has joined.
     */
    public Optional<Connector.ConnectorResponse> notifyAgentJoined(String customerId, String agentName, String agentId) {
        return send(Connector.ConnectorRequest.of(customerId, "AGENT_JOINED", Map.of(
                "agentName", agentName,
                "agentId", agentId
        )));
    }
}
