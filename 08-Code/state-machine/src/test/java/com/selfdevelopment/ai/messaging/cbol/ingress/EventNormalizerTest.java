package com.selfdevelopment.ai.messaging.cbol.ingress;

import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;

import com.selfdevelopment.ai.messaging.statemachine.event.StandardEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AibotEventNormalizer and GenesysEventNormalizer.
 */
class EventNormalizerTest {

    private AibotEventNormalizer aibotNormalizer;
    private GenesysEventNormalizer genesysNormalizer;

    @BeforeEach
    void setUp() {
        aibotNormalizer = new AibotEventNormalizer();
        genesysNormalizer = new GenesysEventNormalizer();
    }

    // ===== AibotEventNormalizer tests =====

    @Test
    void shouldNormalizeAibotMessageReceived() {
        AibotEvent event = AibotEvent.builder()
                .botId("bot-1")
                .sessionId("conv-123")
                .eventType("MESSAGE_RECEIVED")
                .userId("user-1")
                .message("hello")
                .receivedAt(Instant.now())
                .build();

        Optional<StandardEvent> result = aibotNormalizer.normalize(event);

        assertTrue(result.isPresent());
        assertEquals("USER_MESSAGE", result.get().getEventType());
        assertEquals("AIBOT", result.get().getSource());
        assertEquals("conv-123", result.get().getEntityId());
        assertEquals("hello", result.get().getPayload().get("message"));
    }

    @Test
    void shouldNormalizeAibotHandoff() {
        AibotEvent event = AibotEvent.builder()
                .botId("bot-1")
                .sessionId("conv-123")
                .eventType("HANDOFF")
                .userId("user-1")
                .build();

        Optional<StandardEvent> result = aibotNormalizer.normalize(event);

        assertTrue(result.isPresent());
        assertEquals("TRANSFER_REQUEST", result.get().getEventType());
    }

    @Test
    void shouldNormalizeAibotSessionEnded() {
        AibotEvent event = AibotEvent.builder()
                .botId("bot-1")
                .sessionId("conv-123")
                .eventType("SESSION_ENDED")
                .build();

        Optional<StandardEvent> result = aibotNormalizer.normalize(event);

        assertTrue(result.isPresent());
        assertEquals("CONVERSATION_END", result.get().getEventType());
    }

    @Test
    void shouldFilterUnknownAibotEventType() {
        AibotEvent event = AibotEvent.builder()
                .botId("bot-1")
                .sessionId("conv-123")
                .eventType("UNKNOWN_TYPE")
                .build();

        Optional<StandardEvent> result = aibotNormalizer.normalize(event);
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnAibotSourceSystem() {
        assertEquals("AIBOT", aibotNormalizer.getSourceSystem());
    }

    @Test
    void shouldGenerateDeterministicEventId() {
        AibotEvent event1 = AibotEvent.builder()
                .botId("bot-1")
                .sessionId("conv-123")
                .eventType("MESSAGE_RECEIVED")
                .receivedAt(Instant.ofEpochMilli(1000))
                .build();

        AibotEvent event2 = AibotEvent.builder()
                .botId("bot-1")
                .sessionId("conv-123")
                .eventType("MESSAGE_RECEIVED")
                .receivedAt(Instant.ofEpochMilli(1000))
                .build();

        assertEquals(aibotNormalizer.normalize(event1).get().getEventId(),
                aibotNormalizer.normalize(event2).get().getEventId());
    }

    // ===== GenesysEventNormalizer tests =====

    @Test
    void shouldNormalizeGenesysConversationStarted() {
        GenesysEvent event = GenesysEvent.builder()
                .conversationId("conv-456")
                .eventType("conversation.started")
                .build();

        Optional<StandardEvent> result = genesysNormalizer.normalize(event);

        assertTrue(result.isPresent());
        assertEquals("CONVERSATION_START", result.get().getEventType());
        assertEquals("GENESYS", result.get().getSource());
        assertEquals("conv-456", result.get().getEntityId());
    }

    @Test
    void shouldNormalizeGenesysParticipantJoined() {
        GenesysEvent event = GenesysEvent.builder()
                .conversationId("conv-456")
                .eventType("participant.joined")
                .agentId("agent-1")
                .build();

        Optional<StandardEvent> result = genesysNormalizer.normalize(event);

        assertTrue(result.isPresent());
        assertEquals("AGENT_JOIN", result.get().getEventType());
        assertEquals("agent-1", result.get().getPayload().get("agentId"));
    }

    @Test
    void shouldNormalizeGenesysMessageCreated() {
        GenesysEvent event = GenesysEvent.builder()
                .conversationId("conv-456")
                .eventType("message.created")
                .messageBody("agent message")
                .build();

        Optional<StandardEvent> result = genesysNormalizer.normalize(event);

        assertTrue(result.isPresent());
        assertEquals("AGENT_MESSAGE", result.get().getEventType());
        assertEquals("agent message", result.get().getPayload().get("message"));
    }

    @Test
    void shouldNormalizeGenesysConversationTransferred() {
        GenesysEvent event = GenesysEvent.builder()
                .conversationId("conv-456")
                .eventType("conversation.transferred")
                .build();

        Optional<StandardEvent> result = genesysNormalizer.normalize(event);

        assertTrue(result.isPresent());
        assertEquals("TRANSFER_COMPLETE", result.get().getEventType());
    }

    @Test
    void shouldFilterUnknownGenesysEventType() {
        GenesysEvent event = GenesysEvent.builder()
                .conversationId("conv-456")
                .eventType("unknown.event")
                .build();

        Optional<StandardEvent> result = genesysNormalizer.normalize(event);
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnGenesysSourceSystem() {
        assertEquals("GENESYS", genesysNormalizer.getSourceSystem());
    }
}
