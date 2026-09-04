package com.selfdevelopment.chatengine.ingress;

import com.selfdevelopment.chatengine.enums.ConversationFact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Normalizes AIBot webhook events into CBOL conversation facts.
 * <p>
 * <b>RESERVED CODE - Currently not used in production flow.</b>
 * <p>
 * Maps AIBot event types to CBOL conversation facts:
 * <ul>
 *   <li>MESSAGE_RECEIVED → CUSTOMER_CONNECT (simplified mapping)</li>
 *   <li>HANDOFF → TRANSFER_REQUEST</li>
 *   <li>SESSION_ENDED → CUSTOMER_CLOSE</li>
 * </ul>
 * <p>
 * Events with unknown types are filtered out (return empty).
 */
public class AibotEventNormalizer {

    private static final Logger log = LoggerFactory.getLogger(AibotEventNormalizer.class);

    /** AIBot event type → CBOL conversation fact mapping */
    private static final Map<String, ConversationFact> EVENT_TYPE_MAPPING = Map.of(
            "MESSAGE_RECEIVED", ConversationFact.CUSTOMER_CONNECT,
            "HANDOFF", ConversationFact.TRANSFER_REQUEST,
            "SESSION_ENDED", ConversationFact.CUSTOMER_CLOSE
    );

    /**
     * Normalizes an AIBot event into a CBOL conversation fact.
     *
     * @param event the AIBot event to normalize
     * @return Optional containing the mapped conversation fact, or empty if unknown
     */
    public Optional<NormalizedEvent> normalize(AibotEvent event) {
        if (event == null || event.eventType() == null) {
            return Optional.empty();
        }

        ConversationFact fact = EVENT_TYPE_MAPPING.get(event.eventType());
        if (fact == null) {
            log.debug("Filtering unknown AIBot event type: {}", event.eventType());
            return Optional.empty();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("botId", event.botId());
        payload.put("userId", event.userId());
        if (event.message() != null) {
            payload.put("message", event.message());
        }
        if (event.metadata() != null) {
            payload.putAll(event.metadata());
        }

        NormalizedEvent normalized = new NormalizedEvent(
                fact,
                event.sessionId(),
                event.receivedAt() != null ? event.receivedAt().toEpochMilli() : System.currentTimeMillis(),
                payload
        );

        log.debug("Normalized AIBot event: {} -> {}", event.eventType(), fact);
        return Optional.of(normalized);
    }

    /**
     * Returns the source system identifier.
     */
    public String getSourceSystem() {
        return "AIBOT";
    }

    /**
     * Simple normalized event record.
     */
    public record NormalizedEvent(
            ConversationFact fact,
            String sessionId,
            long timestamp,
            Map<String, Object> payload
    ) {}
}
