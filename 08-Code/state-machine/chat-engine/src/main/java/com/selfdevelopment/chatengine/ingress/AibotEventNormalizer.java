package com.selfdevelopment.chatengine.ingress;

import com.selfdevelopment.statemachine.event.EventNormalizer;
import com.selfdevelopment.statemachine.event.StandardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Normalizes AIBot webhook events into the standard {@link StandardEvent} format.
 * <p>
 * Maps AIBot event types to CBOL conversation facts:
 * <ul>
 *   <li>MESSAGE_RECEIVED → USER_MESSAGE</li>
 *   <li>BOT_REPLY → AI_RESPONSE</li>
 *   <li>HANDOFF → TRANSFER_REQUEST</li>
 *   <li>SESSION_ENDED → CONVERSATION_END</li>
 * </ul>
 * <p>
 * Events with unknown types are filtered out (return empty).
 */
public class AibotEventNormalizer implements EventNormalizer<AibotEvent, StandardEvent> {

    private static final Logger log = LoggerFactory.getLogger(AibotEventNormalizer.class);

    /** AIBot event type → CBOL standard event type mapping */
    private static final Map<String, String> EVENT_TYPE_MAPPING = Map.of(
            "MESSAGE_RECEIVED", "USER_MESSAGE",
            "BOT_REPLY", "AI_RESPONSE",
            "HANDOFF", "TRANSFER_REQUEST",
            "SESSION_ENDED", "CONVERSATION_END"
    );

    @Override
    public Optional<StandardEvent> normalize(AibotEvent event) {
        if (!canNormalize(event)) {
            return Optional.empty();
        }

        String standardEventType = EVENT_TYPE_MAPPING.get(event.eventType());
        if (standardEventType == null) {
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

        StandardEvent standardEvent = StandardEvent.builder()
                .eventId(generateEventId(event))
                .eventType(standardEventType)
                .source("AIBOT")
                .entityId(event.sessionId())
                .timestamp(event.receivedAt())
                .traceId(UUID.randomUUID().toString())
                .payload(payload)
                .metadata(Map.of(
                        "botId", event.botId() != null ? event.botId() : "",
                        "userId", event.userId() != null ? event.userId() : ""
                ))
                .build();

        log.debug("Normalized AIBot event: {} -> {}", event.eventType(), standardEventType);
        return Optional.of(standardEvent);
    }

    @Override
    public String getSourceSystem() {
        return "AIBOT";
    }

    private String generateEventId(AibotEvent event) {
        // Use botId + sessionId + eventType + timestamp as deterministic ID for idempotency
        return "aibot-" + event.botId() + "-" + event.sessionId() + "-" +
                event.eventType() + "-" + event.receivedAt().toEpochMilli();
    }
}
