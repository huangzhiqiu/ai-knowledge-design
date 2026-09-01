package com.selfdevelopment.ai.messaging.statemachine.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StandardEvent, EventNormalizer, and EventDispatcher.
 */
class EventDrivingTest {

    // ===== StandardEvent tests =====

    @Test
    void shouldBuildStandardEvent() {
        StandardEvent event = StandardEvent.builder()
                .eventId("evt-001")
                .eventType("USER_MESSAGE")
                .source("AIBOT")
                .entityId("conv-123")
                .timestamp(Instant.now())
                .traceId("trace-abc")
                .payload(Map.of("message", "hello", "userId", "u-001"))
                .metadata(Map.of("channel", "web"))
                .build();

        assertEquals("evt-001", event.getEventId());
        assertEquals("USER_MESSAGE", event.getEventType());
        assertEquals("AIBOT", event.getSource());
        assertEquals("conv-123", event.getEntityId());
        assertEquals("trace-abc", event.getTraceId());
        assertEquals("hello", event.getPayload().get("message"));
        assertEquals("web", event.getMetadata().get("channel"));
    }

    @Test
    void shouldExtractPayloadTypeSafely() {
        StandardEvent event = StandardEvent.builder()
                .eventId("evt-001")
                .eventType("TEST")
                .source("TEST")
                .entityId("e-1")
                .payload(Map.of("count", 42, "name", "test", "flag", true))
                .build();

        Optional<Integer> count = event.getPayloadAs("count", Integer.class);
        assertTrue(count.isPresent());
        assertEquals(42, count.get());

        Optional<String> name = event.getPayloadAs("name", String.class);
        assertTrue(name.isPresent());
        assertEquals("test", name.get());

        // Type mismatch returns empty
        Optional<String> wrongType = event.getPayloadAs("count", String.class);
        assertFalse(wrongType.isPresent());

        // Missing key returns empty
        Optional<String> missing = event.getPayloadAs("nonexistent", String.class);
        assertFalse(missing.isPresent());
    }

    @Test
    void shouldReturnDefaultTimestamp() {
        StandardEvent event = StandardEvent.builder()
                .eventId("evt-001")
                .eventType("TEST")
                .source("TEST")
                .entityId("e-1")
                .build();

        assertNotNull(event.getTimestamp());
    }

    @Test
    void shouldReturnEmptyPayloadAndMetadataByDefault() {
        StandardEvent event = StandardEvent.builder()
                .eventId("evt-001")
                .eventType("TEST")
                .source("TEST")
                .entityId("e-1")
                .build();

        assertTrue(event.getPayload().isEmpty());
        assertTrue(event.getMetadata().isEmpty());
    }

    // ===== EventNormalizer tests =====

    @Test
    void shouldNormalizeEvent() {
        EventNormalizer<String, StandardEvent> normalizer = source ->
                Optional.of(StandardEvent.builder()
                        .eventId("normalized-" + source)
                        .eventType("NORMALIZED")
                        .source("TEST")
                        .entityId(source)
                        .build());

        Optional<StandardEvent> result = normalizer.normalize("input-1");
        assertTrue(result.isPresent());
        assertEquals("NORMALIZED", result.get().getEventType());
        assertEquals("input-1", result.get().getEntityId());
    }

    @Test
    void shouldFilterOutEvent() {
        EventNormalizer<String, StandardEvent> normalizer = source ->
                source.contains("skip") ? Optional.empty() :
                        Optional.of(StandardEvent.builder()
                                .eventId("e-1")
                                .eventType("OK")
                                .source("TEST")
                                .entityId(source)
                                .build());

        assertTrue(normalizer.normalize("good").isPresent());
        assertFalse(normalizer.normalize("skip_this").isPresent());
    }

    @Test
    void shouldReturnSourceSystemName() {
        EventNormalizer<String, StandardEvent> normalizer = new EventNormalizer<>() {
            @Override
            public Optional<StandardEvent> normalize(String sourceEvent) {
                return Optional.empty();
            }

            @Override
            public String getSourceSystem() {
                return "CUSTOM_SYSTEM";
            }
        };

        assertEquals("CUSTOM_SYSTEM", normalizer.getSourceSystem());
    }

    // ===== EventDispatcher tests =====

    @Test
    void shouldDispatchToRegisteredHandler() {
        EventDispatcher dispatcher = new EventDispatcher();
        AtomicInteger callCount = new AtomicInteger(0);

        dispatcher.registerHandler("USER_MESSAGE", event -> callCount.incrementAndGet());

        StandardEvent event = StandardEvent.builder()
                .eventId("e-1")
                .eventType("USER_MESSAGE")
                .source("TEST")
                .entityId("conv-1")
                .build();

        dispatcher.dispatch(event);
        assertEquals(1, callCount.get());
    }

    @Test
    void shouldUseDefaultHandlerForUnknownEventType() {
        EventDispatcher dispatcher = new EventDispatcher();
        AtomicInteger defaultCount = new AtomicInteger(0);

        dispatcher.setDefaultHandler(event -> defaultCount.incrementAndGet());

        StandardEvent event = StandardEvent.builder()
                .eventId("e-1")
                .eventType("UNKNOWN_EVENT")
                .source("TEST")
                .entityId("conv-1")
                .build();

        dispatcher.dispatch(event);
        assertEquals(1, defaultCount.get());
    }

    @Test
    void shouldRunInterceptors() {
        EventDispatcher dispatcher = new EventDispatcher();
        AtomicInteger beforeCount = new AtomicInteger(0);
        AtomicInteger afterCount = new AtomicInteger(0);

        dispatcher.registerInterceptor("test", new EventDispatcher.EventInterceptor() {
            @Override
            public void beforeDispatch(StandardEvent event) {
                beforeCount.incrementAndGet();
            }

            @Override
            public void afterDispatch(StandardEvent event) {
                afterCount.incrementAndGet();
            }
        });

        dispatcher.registerHandler("TEST", event -> {});

        StandardEvent event = StandardEvent.builder()
                .eventId("e-1")
                .eventType("TEST")
                .source("TEST")
                .entityId("e-1")
                .build();

        dispatcher.dispatch(event);
        assertEquals(1, beforeCount.get());
        assertEquals(1, afterCount.get());
    }

    @Test
    void shouldReportHandlerCount() {
        EventDispatcher dispatcher = new EventDispatcher();
        assertEquals(0, dispatcher.getHandlerCount());

        dispatcher.registerHandler("A", event -> {});
        dispatcher.registerHandler("B", event -> {});
        assertEquals(2, dispatcher.getHandlerCount());
        assertTrue(dispatcher.hasHandler("A"));
        assertFalse(dispatcher.hasHandler("C"));
    }

    @Test
    void shouldUnregisterHandler() {
        EventDispatcher dispatcher = new EventDispatcher();
        dispatcher.registerHandler("A", event -> {});
        assertTrue(dispatcher.hasHandler("A"));

        dispatcher.unregisterHandler("A");
        assertFalse(dispatcher.hasHandler("A"));
    }

    @Test
    void shouldPropagateHandlerExceptions() {
        EventDispatcher dispatcher = new EventDispatcher();
        dispatcher.registerHandler("FAIL", event -> {
            throw new RuntimeException("Handler failed");
        });

        StandardEvent event = StandardEvent.builder()
                .eventId("e-1")
                .eventType("FAIL")
                .source("TEST")
                .entityId("e-1")
                .build();

        assertThrows(RuntimeException.class, () -> dispatcher.dispatch(event));
    }
}
