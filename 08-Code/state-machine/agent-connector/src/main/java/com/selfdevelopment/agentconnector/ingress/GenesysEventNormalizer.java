package com.selfdevelopment.agentconnector.ingress;

import com.selfdevelopment.statemachine.event.EventNormalizer;
import com.selfdevelopment.statemachine.event.StandardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Normalizes Genesys notification events into the standard {@link StandardEvent} format.
 * <p>
 * Maps Genesys event types to CBOL conversation facts:
 * <ul>
 *   <li>conversation.started → CONVERSATION_START</li>
 *   <li>participant.joined (agent) → AGENT_JOIN</li>
 *   <li>participant.left (agent) → AGENT_LEAVE</li>
 *   <li>message.created → AGENT_MESSAGE</li>
 *   <li>conversation.ended → CONVERSATION_END</li>
 *   <li>conversation.transferred → TRANSFER_COMPLETE</li>
 * </ul>
 * <p>
 * Events with unknown types are filtered out (return empty).
 */
public class GenesysEventNormalizer implements EventNormalizer<GenesysEvent, StandardEvent> {

    private static final Logger log = LoggerFactory.getLogger(GenesysEventNormalizer.class);

    /** Genesys event type → CBOL standard event type mapping */
    private static final Map<String, String> EVENT_TYPE_MAPPING = Map.of(
            "conversation.started", "CONVERSATION_START",
            "participant.joined", "AGENT_JOIN",
            "participant.left", "AGENT_LEAVE",
            "message.created", "AGENT_MESSAGE",
            "conversation.ended", "CONVERSATION_END",
            "conversation.transferred", "TRANSFER_COMPLETE"
    );

    @Override
    public Optional<StandardEvent> normalize(GenesysEvent event) {
        if (!canNormalize(event)) {
            return Optional.empty();
        }

        String standardEventType = EVENT_TYPE_MAPPING.get(event.eventType());
        if (standardEventType == null) {
            log.debug("Filtering unknown Genesys event type: {}", event.eventType());
            return Optional.empty();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", event.conversationId());
        payload.put("participantId", event.participantId());
        if (event.agentId() != null) {
            payload.put("agentId", event.agentId());
        }
        if (event.queueId() != null) {
            payload.put("queueId", event.queueId());
        }
        if (event.messageBody() != null) {
            payload.put("message", event.messageBody());
        }
        if (event.metadata() != null) {
            payload.putAll(event.metadata());
        }

        StandardEvent standardEvent = StandardEvent.builder()
                .eventId(generateEventId(event))
                .eventType(standardEventType)
                .source("GENESYS")
                .entityId(event.conversationId())
                .timestamp(event.receivedAt())
                .traceId(UUID.randomUUID().toString())
                .payload(payload)
                .metadata(Map.of(
                        "agentId", event.agentId() != null ? event.agentId() : "",
                        "queueId", event.queueId() != null ? event.queueId() : ""
                ))
                .build();

        log.debug("Normalized Genesys event: {} -> {}", event.eventType(), standardEventType);
        return Optional.of(standardEvent);
    }

    @Override
    public String getSourceSystem() {
        return "GENESYS";
    }

    private String generateEventId(GenesysEvent event) {
        return "genesys-" + event.conversationId() + "-" + event.eventType() + "-" +
                event.receivedAt().toEpochMilli();
    }
}
