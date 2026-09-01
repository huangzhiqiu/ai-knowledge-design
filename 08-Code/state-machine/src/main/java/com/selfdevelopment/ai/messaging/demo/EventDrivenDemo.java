package com.selfdevelopment.ai.messaging.demo;

import com.selfdevelopment.ai.messaging.statemachine.event.EventDispatcher;
import com.selfdevelopment.ai.messaging.statemachine.event.EventNormalizer;
import com.selfdevelopment.ai.messaging.statemachine.event.StandardEvent;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Event-driven architecture demo.
 * <p>
 * Demonstrates the standard event-driven infrastructure:
 * <ul>
 *   <li>StandardEvent — unified event contract (eventId, eventType, source, entityId, payload, traceId)</li>
 *   <li>EventNormalizer — converts external events to StandardEvent (returns Optional for filtering)</li>
 *   <li>EventDispatcher — routes events to handlers with named interceptor chain</li>
 *   <li>Interceptor pattern — logging, metrics, validation before/after handler</li>
 * </ul>
 */
public class EventDrivenDemo {

    // --- External event types ---

    record AibotWebhookEvent(
            String botId,
            String sessionId,
            String messageType,
            String content,
            long timestamp
    ) {}

    record GenesysNotification(
            String orgId,
            String conversationId,
            String topic,
            Map<String, Object> data,
            String eventTime
    ) {}

    // --- Standard event types ---
    static final String TYPE_USER_MESSAGE = "USER_MESSAGE";
    static final String TYPE_AI_RESPONSE = "AI_RESPONSE";
    static final String TYPE_TRANSFER_REQUEST = "TRANSFER_REQUEST";
    static final String TYPE_CONVERSATION_END = "CONVERSATION_END";
    static final String TYPE_AGENT_JOIN = "AGENT_JOIN";
    static final String TYPE_AGENT_MESSAGE = "AGENT_MESSAGE";

    public static void main(String[] args) {
        System.out.println("=== Event-Driven Architecture Demo ===\n");

        System.out.println("--- 1. StandardEvent (Unified Event Contract) ---");
        demoStandardEvent();

        System.out.println("\n--- 2. EventNormalizer (AIBot Webhook → StandardEvent) ---");
        demoAibotNormalizer();

        System.out.println("\n--- 3. EventNormalizer (Genesys Notification → StandardEvent) ---");
        demoGenesysNormalizer();

        System.out.println("\n--- 4. EventDispatcher (Routing + Handlers) ---");
        demoDispatcher();

        System.out.println("\n--- 5. Interceptor Chain (Logging + Validation) ---");
        demoInterceptors();

        System.out.println("\n--- 6. Full Pipeline (External → Normalize → Dispatch → Handle) ---");
        demoFullPipeline();

        System.out.println("\n=== Demo complete ===");
    }

    private static void demoStandardEvent() {
        StandardEvent event = StandardEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(TYPE_USER_MESSAGE)
                .source("aibot")
                .entityId("conv-001")
                .payload(Map.of("text", "Hello, I need help", "userId", "user-123"))
                .traceId("trace-" + System.currentTimeMillis())
                .timestamp(Instant.now())
                .build();

        System.out.println("  eventId: " + event.getEventId());
        System.out.println("  eventType: " + event.getEventType());
        System.out.println("  source: " + event.getSource());
        System.out.println("  entityId: " + event.getEntityId());
        System.out.println("  traceId: " + event.getTraceId());
        System.out.println("  payload: " + event.getPayload());
    }

    private static void demoAibotNormalizer() {
        EventNormalizer<AibotWebhookEvent, StandardEvent> normalizer = new AibotNormalizer();

        AibotWebhookEvent webhook = new AibotWebhookEvent(
                "bot-prod-01", "sess-abc123", "message_received",
                "I want to reset my password", System.currentTimeMillis());

        Optional<StandardEvent> standard = normalizer.normalize(webhook);
        standard.ifPresentOrElse(
                evt -> {
                    System.out.println("  AIBot message_type: " + webhook.messageType());
                    System.out.println("  → StandardEvent eventType: " + evt.getEventType());
                    System.out.println("  → entityId (sessionId): " + evt.getEntityId());
                    System.out.println("  → source: " + evt.getSource());
                },
                () -> System.out.println("  Event filtered out"));

        AibotWebhookEvent handoff = new AibotWebhookEvent(
                "bot-prod-01", "sess-abc123", "handoff", "transfer to human", System.currentTimeMillis());
        normalizer.normalize(handoff).ifPresent(
                evt -> System.out.println("  AIBot message_type: handoff → eventType: " + evt.getEventType()));
    }

    private static void demoGenesysNormalizer() {
        EventNormalizer<GenesysNotification, StandardEvent> normalizer = new GenesysNormalizer();

        GenesysNotification notification = new GenesysNotification(
                "org-hk-001", "conv-genesys-789", "conversation.started",
                Map.of("queueName", "Support_HK", "mediaType", "chat"), "2026-09-01T10:30:00Z");

        normalizer.normalize(notification).ifPresent(evt -> {
            System.out.println("  Genesys topic: " + notification.topic());
            System.out.println("  → StandardEvent eventType: " + evt.getEventType());
            System.out.println("  → entityId (conversationId): " + evt.getEntityId());
            System.out.println("  → source: " + evt.getSource());
        });

        GenesysNotification agentJoin = new GenesysNotification(
                "org-hk-001", "conv-genesys-789", "participant.joined",
                Map.of("agentName", "John Doe"), "2026-09-01T10:31:00Z");
        normalizer.normalize(agentJoin).ifPresent(
                evt -> System.out.println("  Genesys topic: participant.joined → eventType: " + evt.getEventType()));
    }

    private static void demoDispatcher() {
        EventDispatcher dispatcher = new EventDispatcher();
        AtomicInteger userMessageCount = new AtomicInteger(0);
        AtomicInteger transferCount = new AtomicInteger(0);

        dispatcher.registerHandler(TYPE_USER_MESSAGE, event -> {
            System.out.println("  [Handler:UserMessage] Processing: " + event.getPayload().get("text"));
            userMessageCount.incrementAndGet();
        });

        dispatcher.registerHandler(TYPE_TRANSFER_REQUEST, event -> {
            System.out.println("  [Handler:TransferRequest] Routing to agent queue: " + event.getEntityId());
            transferCount.incrementAndGet();
        });

        StandardEvent msgEvent = StandardEvent.builder()
                .eventId("e-001").eventType(TYPE_USER_MESSAGE).source("aibot")
                .entityId("conv-001").payload(Map.of("text", "Help me!")).build();
        dispatcher.dispatch(msgEvent);

        StandardEvent transferEvent = StandardEvent.builder()
                .eventId("e-002").eventType(TYPE_TRANSFER_REQUEST).source("aibot")
                .entityId("conv-001").payload(Map.of("queue", "support")).build();
        dispatcher.dispatch(transferEvent);

        System.out.println("  User messages handled: " + userMessageCount.get());
        System.out.println("  Transfers handled: " + transferCount.get());
    }

    private static void demoInterceptors() {
        EventDispatcher dispatcher = new EventDispatcher();

        // Interceptor 1: Logging
        dispatcher.registerInterceptor("logging", new EventDispatcher.EventInterceptor() {
            @Override
            public void beforeDispatch(StandardEvent event) {
                System.out.println("  [Interceptor:Logging] Before: " + event.getEventId() + " type=" + event.getEventType());
            }

            @Override
            public void afterDispatch(StandardEvent event) {
                System.out.println("  [Interceptor:Logging] After: " + event.getEventId() + " success");
            }
        });

        // Interceptor 2: Validation (logs warning for missing entityId)
        dispatcher.registerInterceptor("validation", new EventDispatcher.EventInterceptor() {
            @Override
            public void beforeDispatch(StandardEvent event) {
                if (event.getEntityId() == null || event.getEntityId().isBlank()) {
                    System.out.println("  [Interceptor:Validation] WARNING: missing entityId for event " + event.getEventId());
                }
            }
        });

        dispatcher.registerHandler(TYPE_USER_MESSAGE, event ->
                System.out.println("  [Handler] Processing event: " + event.getEventId()));

        StandardEvent valid = StandardEvent.builder()
                .eventId("e-valid").eventType(TYPE_USER_MESSAGE).source("test")
                .entityId("conv-001").payload(Map.of()).build();
        dispatcher.dispatch(valid);

        StandardEvent invalid = StandardEvent.builder()
                .eventId("e-invalid").eventType(TYPE_USER_MESSAGE).source("test")
                .entityId("").payload(Map.of()).build();
        dispatcher.dispatch(invalid);
    }

    private static void demoFullPipeline() {
        EventNormalizer<AibotWebhookEvent, StandardEvent> normalizer = new AibotNormalizer();
        EventDispatcher dispatcher = new EventDispatcher();

        dispatcher.registerHandler(TYPE_USER_MESSAGE, event ->
                System.out.println("  [Pipeline] User message handled: " + event.getPayload().get("content")));
        dispatcher.registerHandler(TYPE_TRANSFER_REQUEST, event ->
                System.out.println("  [Pipeline] Transfer request routed: " + event.getEntityId()));
        dispatcher.registerHandler(TYPE_CONVERSATION_END, event ->
                System.out.println("  [Pipeline] Conversation ended: " + event.getEntityId()));

        AibotWebhookEvent[] webhooks = {
                new AibotWebhookEvent("bot-1", "conv-100", "message_received", "Hi, I have a question", System.currentTimeMillis()),
                new AibotWebhookEvent("bot-1", "conv-100", "handoff", "user wants human agent", System.currentTimeMillis()),
                new AibotWebhookEvent("bot-1", "conv-100", "session_ended", "user disconnected", System.currentTimeMillis())
        };

        for (AibotWebhookEvent webhook : webhooks) {
            normalizer.normalize(webhook).ifPresent(dispatcher::dispatch);
        }

        System.out.println("  Pipeline processed " + webhooks.length + " external events");
    }

    // --- Normalizer implementations ---

    static class AibotNormalizer implements EventNormalizer<AibotWebhookEvent, StandardEvent> {
        @Override
        public Optional<StandardEvent> normalize(AibotWebhookEvent source) {
            String type = switch (source.messageType()) {
                case "message_received" -> TYPE_USER_MESSAGE;
                case "bot_reply" -> TYPE_AI_RESPONSE;
                case "handoff" -> TYPE_TRANSFER_REQUEST;
                case "session_ended" -> TYPE_CONVERSATION_END;
                default -> "UNKNOWN";
            };

            if ("UNKNOWN".equals(type)) {
                return Optional.empty(); // filter out unknown event types
            }

            return Optional.of(StandardEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(type)
                    .source("aibot")
                    .entityId(source.sessionId())
                    .payload(Map.of(
                            "botId", source.botId(),
                            "content", source.content()
                    ))
                    .traceId("trace-" + source.timestamp())
                    .timestamp(Instant.ofEpochMilli(source.timestamp()))
                    .build());
        }
    }

    static class GenesysNormalizer implements EventNormalizer<GenesysNotification, StandardEvent> {
        @Override
        public Optional<StandardEvent> normalize(GenesysNotification source) {
            String type = switch (source.topic()) {
                case "conversation.started" -> "CONVERSATION_START";
                case "participant.joined" -> TYPE_AGENT_JOIN;
                case "participant.left" -> "AGENT_LEAVE";
                case "message.created" -> TYPE_AGENT_MESSAGE;
                case "conversation.ended" -> TYPE_CONVERSATION_END;
                case "conversation.transferred" -> "TRANSFER_COMPLETE";
                default -> "UNKNOWN";
            };

            if ("UNKNOWN".equals(type)) {
                return Optional.empty();
            }

            return Optional.of(StandardEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(type)
                    .source("genesys")
                    .entityId(source.conversationId())
                    .payload(source.data())
                    .traceId("trace-" + source.eventTime())
                    .timestamp(Instant.now())
                    .build());
        }
    }
}
