package com.selfdevelopment.ai.messaging.cbol.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Connector for the Genesys contact center platform.
 * <p>
 * Handles integration with Genesys Cloud API, including:
 * <ul>
 *   <li>Routing conversations to agents/queues</li>
 *   <li>Sending agent messages to customers</li>
 *   <li>Managing participant state</li>
 *   <li>Transferring conversations between queues/agents</li>
 * </ul>
 * <p>
 * This is a reference implementation. In production, replace the send() method
 * with actual Genesys Cloud API calls (PureCloud REST API).
 */
public class GenesysConnector implements Connector<Connector.ConnectorRequest, Connector.ConnectorResponse> {

    private static final Logger log = LoggerFactory.getLogger(GenesysConnector.class);

    private final String orgId;
    private final String clientId;
    private volatile boolean healthy = true;

    /**
     * Creates a Genesys connector.
     *
     * @param orgId    the Genesys organization ID
     * @param clientId the OAuth client ID
     */
    public GenesysConnector(String orgId, String clientId) {
        this.orgId = Objects.requireNonNull(orgId, "orgId must not be null");
        this.clientId = clientId;
    }

    @Override
    public String getName() {
        return "GENESYS";
    }

    @Override
    public Optional<Connector.ConnectorResponse> send(Connector.ConnectorRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        log.info("Genesys connector sending: action={}, conversationId={}",
                request.action(), request.conversationId());

        try {
            // In production: Genesys PureCloud API calls
            // ApiClient client = PureCloudApiClient.getClient(orgId, clientId);
            // ConversationsApi conversationsApi = new ConversationsApi(client);

            Map<String, Object> responseData = Map.of(
                    "conversationId", request.conversationId(),
                    "action", request.action(),
                    "orgId", orgId,
                    "status", "ACCEPTED"
            );
            return Optional.of(Connector.ConnectorResponse.success(responseData));

        } catch (Exception e) {
            healthy = false;
            log.error("Genesys connector failed: action={}, error={}", request.action(), e.getMessage(), e);
            throw new Connector.ConnectorException("GENESYS", request.action(), e.getMessage(), e);
        }
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public void init() {
        log.info("Genesys connector initialized: orgId={}", orgId);
    }

    @Override
    public void shutdown() {
        log.info("Genesys connector shut down");
    }

    /**
     * Routes a conversation to a specific queue.
     */
    public Optional<Connector.ConnectorResponse> routeToQueue(String conversationId, String queueId) {
        return send(Connector.ConnectorRequest.of(conversationId, "ROUTE_TO_QUEUE", Map.of(
                "queueId", queueId
        )));
    }

    /**
     * Sends an agent message to the customer.
     */
    public Optional<Connector.ConnectorResponse> sendAgentMessage(String conversationId, String agentId, String message) {
        return send(Connector.ConnectorRequest.of(conversationId, "SEND_AGENT_MESSAGE", Map.of(
                "agentId", agentId,
                "message", message
        )));
    }

    /**
     * Transfers a conversation to another agent.
     */
    public Optional<Connector.ConnectorResponse> transferToAgent(String conversationId, String fromAgentId, String toAgentId) {
        return send(Connector.ConnectorRequest.of(conversationId, "TRANSFER_TO_AGENT", Map.of(
                "fromAgentId", fromAgentId,
                "toAgentId", toAgentId
        )));
    }

    /**
     * Ends a conversation in Genesys.
     */
    public Optional<Connector.ConnectorResponse> endConversation(String conversationId) {
        return send(Connector.ConnectorRequest.of(conversationId, "END_CONVERSATION", Map.of()));
    }
}
