package com.selfdevelopment.ai.messaging.cbol.ingress;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an event received from the Genesys contact center platform.
 * <p>
 * This is the external event format before normalization.
 * Actual field mapping should be adjusted based on the real Genesys notification payload.
 *
 * @param conversationId  the Genesys conversation ID
 * @param participantId   the participant (agent/customer) identifier
 * @param eventType       the Genesys event type (e.g., "conversation.started", "participant.joined", "message.created")
 * @param agentId         the agent identifier (if applicable)
 * @param queueId         the queue identifier (if applicable)
 * @param messageBody     the message content (if applicable)
 * @param metadata        additional metadata from Genesys
 * @param receivedAt      when the event was received from Genesys
 */
public record GenesysEvent(
        String conversationId,
        String participantId,
        String eventType,
        String agentId,
        String queueId,
        String messageBody,
        Map<String, String> metadata,
        Instant receivedAt
) {
    /**
     * Creates a builder for GenesysEvent.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for GenesysEvent.
     */
    public static final class Builder {
        private String conversationId;
        private String participantId;
        private String eventType;
        private String agentId;
        private String queueId;
        private String messageBody;
        private Map<String, String> metadata = Map.of();
        private Instant receivedAt = Instant.now();

        public Builder conversationId(String conversationId) { this.conversationId = conversationId; return this; }
        public Builder participantId(String participantId) { this.participantId = participantId; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder queueId(String queueId) { this.queueId = queueId; return this; }
        public Builder messageBody(String messageBody) { this.messageBody = messageBody; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder receivedAt(Instant receivedAt) { this.receivedAt = receivedAt; return this; }

        public GenesysEvent build() {
            return new GenesysEvent(conversationId, participantId, eventType, agentId,
                    queueId, messageBody, metadata, receivedAt);
        }
    }
}
