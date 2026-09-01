package com.selfdevelopment.ai.messaging.cbol.connector;

import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Generic connector interface for integrating with external systems.
 * <p>
 * Each connector encapsulates the communication protocol and error handling
 * for a specific external system (AIBot, Genesys, WebSocket, ODS).
 * <p>
 * Implementations should be thread-safe and handle connection lifecycle.
 *
 * @param <REQ> the request type
 * @param <RES> the response type
 */
public interface Connector<REQ, RES> {

    /**
     * Returns the connector name (e.g., "AIBOT", "GENESYS", "WEBSOCKET", "CHAT_HISTORY_ODS").
     */
    String getName();

    /**
     * Sends a request to the external system.
     *
     * @param request the request to send
     * @return the response, or empty if no response is expected
     * @throws ConnectorException if the request fails
     */
    Optional<RES> send(REQ request);

    /**
     * Checks whether the connector is healthy and connected.
     */
    boolean isHealthy();

    /**
     * Initializes the connector (establishes connections, loads config).
     */
    default void init() {}

    /**
     * Shuts down the connector (releases resources).
     */
    default void shutdown() {}

    /**
     * Exception thrown by connectors when an operation fails.
     */
    @Getter
    class ConnectorException extends RuntimeException {
        private final String connectorName;
        private final String operation;

        public ConnectorException(String connectorName, String operation, String message) {
            super(message);
            this.connectorName = connectorName;
            this.operation = operation;
        }

        public ConnectorException(String connectorName, String operation, String message, Throwable cause) {
            super(message, cause);
            this.connectorName = connectorName;
            this.operation = operation;
        }
    }

    /**
     * Generic connector request with metadata.
     */
    record ConnectorRequest(
            String conversationId,
            String action,
            Map<String, Object> payload,
            String traceId,
            Instant timestamp
    ) {
        public static ConnectorRequest of(String conversationId, String action, Map<String, Object> payload) {
            return new ConnectorRequest(conversationId, action, payload, null, Instant.now());
        }
    }

    /**
     * Generic connector response.
     */
    record ConnectorResponse(
            boolean success,
            String message,
            Map<String, Object> data,
            Instant timestamp
    ) {
        public static ConnectorResponse success(Map<String, Object> data) {
            return new ConnectorResponse(true, "OK", data, Instant.now());
        }

        public static ConnectorResponse failure(String message) {
            return new ConnectorResponse(false, message, Map.of(), Instant.now());
        }
    }
}
