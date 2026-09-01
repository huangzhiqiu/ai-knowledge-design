package com.selfdevelopment.ai.messaging.cbol.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Connector for the Chat History ODS (Operational Data Store).
 * <p>
 * Handles persistence and retrieval of chat history records, including:
 * <ul>
 *   <li>Storing conversation messages (customer, agent, bot)</li>
 *   <li>Recording conversation state changes</li>
 *   <li>Storing transfer and handoff events</li>
 *   <li>Querying conversation history for analytics</li>
 * </ul>
 * <p>
 * This is a reference implementation using an in-memory store.
 * In production, replace with actual database calls (MySQL, MongoDB, etc.).
 */
public class ChatHistoryOdsConnector implements Connector<Connector.ConnectorRequest, Connector.ConnectorResponse> {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryOdsConnector.class);

    private final List<ChatHistoryRecord> historyStore = new ArrayList<>();
    private volatile boolean healthy = true;

    @Override
    public String getName() {
        return "CHAT_HISTORY_ODS";
    }

    @Override
    public Optional<Connector.ConnectorResponse> send(Connector.ConnectorRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        log.info("Chat History ODS connector: action={}, conversationId={}",
                request.action(), request.conversationId());

        try {
            switch (request.action()) {
                case "SAVE_MESSAGE" -> saveMessage(request);
                case "SAVE_STATE_CHANGE" -> saveStateChange(request);
                case "SAVE_TRANSFER" -> saveTransfer(request);
                default -> log.debug("Unknown action for ODS: {}", request.action());
            }

            return Optional.of(Connector.ConnectorResponse.success(Map.of(
                    "conversationId", request.conversationId(),
                    "action", request.action(),
                    "saved", true
            )));

        } catch (Exception e) {
            healthy = false;
            log.error("Chat History ODS connector failed: action={}, error={}",
                    request.action(), e.getMessage(), e);
            throw new Connector.ConnectorException("CHAT_HISTORY_ODS", request.action(), e.getMessage(), e);
        }
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public void init() {
        log.info("Chat History ODS connector initialized");
    }

    @Override
    public void shutdown() {
        log.info("Chat History ODS connector shut down, {} records stored", historyStore.size());
    }

    /**
     * Saves a chat message to the ODS.
     */
    public void saveMessage(String conversationId, String senderId, String senderType, String message) {
        ChatHistoryRecord record = new ChatHistoryRecord(
                conversationId, "MESSAGE", senderId, senderType, message, Map.of(), Instant.now()
        );
        historyStore.add(record);
        log.debug("Saved message to ODS: conversationId={}, senderType={}", conversationId, senderType);
    }

    /**
     * Saves a state change event to the ODS.
     */
    public void saveStateChange(String conversationId, String fromState, String toState, String triggeredBy) {
        ChatHistoryRecord record = new ChatHistoryRecord(
                conversationId, "STATE_CHANGE", triggeredBy, "SYSTEM",
                fromState + " -> " + toState,
                Map.of("fromState", fromState, "toState", toState),
                Instant.now()
        );
        historyStore.add(record);
        log.debug("Saved state change to ODS: conversationId={}, {} -> {}", conversationId, fromState, toState);
    }

    /**
     * Saves a transfer event to the ODS.
     */
    public void saveTransfer(String conversationId, String fromQueue, String toQueue, String transferType) {
        ChatHistoryRecord record = new ChatHistoryRecord(
                conversationId, "TRANSFER", transferType, "SYSTEM",
                fromQueue + " -> " + toQueue,
                Map.of("fromQueue", fromQueue, "toQueue", toQueue, "transferType", transferType),
                Instant.now()
        );
        historyStore.add(record);
        log.debug("Saved transfer to ODS: conversationId={}, {} -> {}", conversationId, fromQueue, toQueue);
    }

    /**
     * Queries chat history for a conversation.
     */
    public List<ChatHistoryRecord> queryHistory(String conversationId) {
        return historyStore.stream()
                .filter(r -> r.conversationId().equals(conversationId))
                .toList();
    }

    /**
     * Returns the total number of stored records.
     */
    public int getRecordCount() {
        return historyStore.size();
    }

    private void saveMessage(Connector.ConnectorRequest request) {
        String conversationId = request.conversationId();
        String message = (String) request.payload().getOrDefault("message", "");
        String senderType = (String) request.payload().getOrDefault("senderType", "UNKNOWN");
        String senderId = (String) request.payload().getOrDefault("senderId", "");
        saveMessage(conversationId, senderId, senderType, message);
    }

    private void saveStateChange(Connector.ConnectorRequest request) {
        String conversationId = request.conversationId();
        String fromState = (String) request.payload().getOrDefault("fromState", "");
        String toState = (String) request.payload().getOrDefault("toState", "");
        String triggeredBy = (String) request.payload().getOrDefault("triggeredBy", "SYSTEM");
        saveStateChange(conversationId, fromState, toState, triggeredBy);
    }

    private void saveTransfer(Connector.ConnectorRequest request) {
        String conversationId = request.conversationId();
        String fromQueue = (String) request.payload().getOrDefault("fromQueue", "");
        String toQueue = (String) request.payload().getOrDefault("toQueue", "");
        String transferType = (String) request.payload().getOrDefault("transferType", "");
        saveTransfer(conversationId, fromQueue, toQueue, transferType);
    }

    /**
     * Immutable record representing a chat history entry.
     */
    public record ChatHistoryRecord(
            String conversationId,
            String eventType,
            String actorId,
            String actorType,
            String content,
            Map<String, String> metadata,
            Instant timestamp
    ) {}
}
