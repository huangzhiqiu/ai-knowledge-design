package com.selfdevelopment.chatengine.app.controller;

import com.selfdevelopment.chatengine.app.dto.ConversationEventRequest;
import com.selfdevelopment.chatengine.app.dto.ConversationEventResponse;
import com.selfdevelopment.chatengine.app.service.ChatEngineRestService;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for chat engine state machine operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat-engine")
@RequiredArgsConstructor
public class ChatEngineController {

    private final ChatEngineRestService chatEngineRestService;

    /**
     * Health check endpoint.
     *
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "chat-engine"
        ));
    }

    /**
     * Get all available conversation states.
     *
     * @return list of conversation states
     */
    @GetMapping("/states")
    public ResponseEntity<List<String>> getStates() {
        List<String> states = Arrays.stream(ConversationState.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(states);
    }

    /**
     * Get all available conversation facts/events.
     *
     * @return list of conversation facts
     */
    @GetMapping("/facts")
    public ResponseEntity<List<String>> getFacts() {
        List<String> facts = Arrays.stream(ConversationFact.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(facts);
    }

    /**
     * Fire a conversation event.
     *
     * @param request the event request
     * @return the event response with transition result
     */
    @PostMapping("/events")
    public ResponseEntity<ConversationEventResponse> fireEvent(
            @RequestBody ConversationEventRequest request) {
        log.info("Received event request: conversationId={}, fact={}",
                request.getConversationId(), request.getFact());

        ConversationEventResponse response = chatEngineRestService.processEvent(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Fire a conversation event for a specific conversation.
     *
     * @param conversationId the conversation ID
     * @param fact the event/fact to fire
     * @param market the market (optional)
     * @return the event response with transition result
     */
    @PostMapping("/conversations/{conversationId}/events/{fact}")
    public ResponseEntity<ConversationEventResponse> fireEventForConversation(
            @PathVariable String conversationId,
            @PathVariable ConversationFact fact,
            @RequestParam(required = false) String market) {

        ConversationEventRequest request = ConversationEventRequest.builder()
                .conversationId(conversationId)
                .fact(fact)
                .market(market)
                .build();

        ConversationEventResponse response = chatEngineRestService.processEvent(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
