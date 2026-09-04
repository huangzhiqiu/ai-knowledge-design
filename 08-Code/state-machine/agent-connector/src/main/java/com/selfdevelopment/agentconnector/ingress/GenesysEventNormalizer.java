package com.selfdevelopment.agentconnector.ingress;

import com.selfdevelopment.agentconnector.enums.InteractionFact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Normalizes Genesys notification events into interaction facts.
 * <p>
 * <b>RESERVED CODE - Currently not used in production flow.</b>
 * <p>
 * Maps Genesys event types to interaction facts:
 * <ul>
 *   <li>conversation.started → CONNECTION_ESTABLISHED</li>
 *   <li>conversation.ended → CLOSE_REQUEST</li>
 *   <li>conversation.transferred → TRANSFER_COMPLETE</li>
 * </ul>
 * <p>
 * Events with unknown types are filtered out (return empty).
 */
public class GenesysEventNormalizer {

    private static final Logger log = LoggerFactory.getLogger(GenesysEventNormalizer.class);

    /** Genesys event type → interaction fact mapping */
    private static final Map<String, InteractionFact> EVENT_TYPE_MAPPING = Map.of(
            "conversation.started", InteractionFact.CONNECTION_ESTABLISHED,
            "conversation.ended", InteractionFact.CLOSE_REQUEST,
            "conversation.transferred", InteractionFact.TRANSFER_COMPLETE
    );

    /**
     * Normalizes a Genesys event into an interaction fact.
     *
     * @param event the Genesys event to normalize
     * @return Optional containing the mapped interaction fact, or empty if unknown
     */
    public Optional<NormalizedEvent> normalize(GenesysEvent event) {
        if (event == null || event.eventType() == null) {
            return Optional.empty();
        }

        InteractionFact fact = EVENT_TYPE_MAPPING.get(event.eventType());
        if (fact == null) {
            log.debug("Filtering unknown Genesys event type: {}", event.eventType());
            return Optional.empty();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", event.conversationId());
        if (event.metadata() != null) {
            payload.putAll(event.metadata());
        }

        NormalizedEvent normalized = new NormalizedEvent(
                fact,
                event.conversationId(),
                event.receivedAt() != null ? event.receivedAt().toEpochMilli() : System.currentTimeMillis(),
                payload
        );

        log.debug("Normalized Genesys event: {} -> {}", event.eventType(), fact);
        return Optional.of(normalized);
    }

    /**
     * Returns the source system identifier.
     */
    public String getSourceSystem() {
        return "GENESYS";
    }

    /**
     * Simple normalized event record.
     */
    public record NormalizedEvent(
            InteractionFact fact,
            String conversationId,
            long timestamp,
            Map<String, Object> payload
    ) {}
}
