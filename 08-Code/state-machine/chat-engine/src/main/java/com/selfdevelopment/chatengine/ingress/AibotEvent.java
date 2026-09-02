package com.selfdevelopment.chatengine.ingress;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an event received from the AIBot system.
 * <p>
 * This is the external event format before normalization.
 * Actual field mapping should be adjusted based on the real AIBot webhook payload.
 *
 * @param botId       the bot identifier
 * @param sessionId   the conversation session ID
 * @param eventType   the AIBot event type (e.g., "MESSAGE_RECEIVED", "BOT_REPLY", "HANDOFF")
 * @param userId      the end user identifier
 * @param message     the message content (if applicable)
 * @param metadata    additional metadata from AIBot
 * @param receivedAt  when the event was received from AIBot
 */
public record AibotEvent(
        String botId,
        String sessionId,
        String eventType,
        String userId,
        String message,
        Map<String, String> metadata,
        Instant receivedAt
) {
    /**
     * Creates a builder for AibotEvent.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AibotEvent.
     */
    public static final class Builder {
        private String botId;
        private String sessionId;
        private String eventType;
        private String userId;
        private String message;
        private Map<String, String> metadata = Map.of();
        private Instant receivedAt = Instant.now();

        public Builder botId(String botId) { this.botId = botId; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder receivedAt(Instant receivedAt) { this.receivedAt = receivedAt; return this; }

        public AibotEvent build() {
            return new AibotEvent(botId, sessionId, eventType, userId, message, metadata, receivedAt);
        }
    }
}
