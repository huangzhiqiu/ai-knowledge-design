package com.selfdevelopment.ai.messaging.statemachine.event;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Standard event contract for the event-driven state machine pipeline.
 * <p>
 * All external events (AIBot, Genesys, App/Web) are normalized into this
 * standard format before entering the state machine pipeline. This ensures
 * consistent processing across all event sources.
 * <p>
 * Key fields:
 * <ul>
 *   <li><b>eventId</b> — unique identifier for idempotency</li>
 *   <li><b>eventType</b> — semantic event type (maps to state machine event)</li>
 *   <li><b>source</b> — origin system (AIBOT, GENESYS, APP_WEB)</li>
 *   <li><b>entityId</b> — target entity identifier (conversationId, interactionId)</li>
 *   <li><b>timestamp</b> — event creation time</li>
 *   <li><b>traceId</b> — distributed tracing identifier</li>
 *   <li><b>payload</b> — event-specific data (type-safe via getPayloadAs)</li>
 *   <li><b>metadata</b> — additional key-value attributes</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * StandardEvent event = StandardEvent.builder()
 *     .eventId("evt-001")
 *     .eventType("USER_MESSAGE")
 *     .source("AIBOT")
 *     .entityId("conv-123")
 *     .timestamp(Instant.now())
 *     .traceId("trace-abc")
 *     .payload(Map.of("message", "hello", "userId", "u-001"))
 *     .build();
 *
 * // Type-safe payload extraction
 * Optional<String> message = event.getPayloadAs("message", String.class);
 * }</pre>
 */
public interface StandardEvent {

    /**
     * Returns the unique event identifier (for idempotency).
     */
    String getEventId();

    /**
     * Returns the semantic event type (maps to state machine event enum).
     */
    String getEventType();

    /**
     * Returns the source system identifier (e.g., "AIBOT", "GENESYS", "APP_WEB").
     */
    String getSource();

    /**
     * Returns the target entity identifier (e.g., conversationId, interactionId).
     */
    String getEntityId();

    /**
     * Returns the event creation timestamp.
     */
    Instant getTimestamp();

    /**
     * Returns the distributed trace identifier.
     */
    String getTraceId();

    /**
     * Returns the event payload as an unmodifiable map.
     */
    Map<String, Object> getPayload();

    /**
     * Returns the event metadata as an unmodifiable map.
     */
    Map<String, String> getMetadata();

    /**
     * Type-safe payload extraction.
     *
     * @param key  the payload key
     * @param type the expected value type
     * @param <T>  the expected type
     * @return the value if present and type matches, otherwise empty
     */
    @SuppressWarnings("unchecked")
    default <T> Optional<T> getPayloadAs(String key, Class<T> type) {
        Object value = getPayload().get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    /**
     * Returns a metadata value by key.
     */
    default Optional<String> getMetadata(String key) {
        return Optional.ofNullable(getMetadata().get(key));
    }

    /**
     * Creates a builder for constructing StandardEvent instances.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link StandardEvent}.
     */
    final class Builder {
        private String eventId;
        private String eventType;
        private String source;
        private String entityId;
        private Instant timestamp = Instant.now();
        private String traceId;
        private Map<String, Object> payload = Map.of();
        private Map<String, String> metadata = Map.of();

        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder entityId(String entityId) { this.entityId = entityId; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder payload(Map<String, Object> payload) { this.payload = payload; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }

        public StandardEvent build() {
            return new DefaultStandardEvent(eventId, eventType, source, entityId,
                    timestamp, traceId, payload, metadata);
        }
    }

    /**
     * Default immutable implementation of {@link StandardEvent}.
     */
    final class DefaultStandardEvent implements StandardEvent {
        private final String eventId;
        private final String eventType;
        private final String source;
        private final String entityId;
        private final Instant timestamp;
        private final String traceId;
        private final Map<String, Object> payload;
        private final Map<String, String> metadata;

        DefaultStandardEvent(String eventId, String eventType, String source, String entityId,
                             Instant timestamp, String traceId,
                             Map<String, Object> payload, Map<String, String> metadata) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.source = source;
            this.entityId = entityId;
            this.timestamp = timestamp;
            this.traceId = traceId;
            // Filter out null values to support Map.copyOf
            this.payload = payload != null
                    ? payload.entrySet().stream()
                            .filter(e -> e.getValue() != null)
                            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                    Map.Entry::getKey, Map.Entry::getValue))
                    : Map.of();
            this.metadata = metadata != null
                    ? metadata.entrySet().stream()
                            .filter(e -> e.getValue() != null)
                            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                    Map.Entry::getKey, Map.Entry::getValue))
                    : Map.of();
        }

        @Override public String getEventId() { return eventId; }
        @Override public String getEventType() { return eventType; }
        @Override public String getSource() { return source; }
        @Override public String getEntityId() { return entityId; }
        @Override public Instant getTimestamp() { return timestamp; }
        @Override public String getTraceId() { return traceId; }
        @Override public Map<String, Object> getPayload() { return payload; }
        @Override public Map<String, String> getMetadata() { return metadata; }

        @Override
        public String toString() {
            return "StandardEvent{eventId='" + eventId + "', eventType='" + eventType +
                    "', source='" + source + "', entityId='" + entityId +
                    "', timestamp=" + timestamp + ", traceId='" + traceId + "'}";
        }
    }
}
