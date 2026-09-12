package com.selfdevelopment.agentconnector.app.controller;

import com.selfdevelopment.agentconnector.app.dto.InteractionEventRequest;
import com.selfdevelopment.agentconnector.app.dto.InteractionEventResponse;
import com.selfdevelopment.agentconnector.app.service.AgentConnectorRestService;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for agent connector state machine operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/agent-connector")
@RequiredArgsConstructor
public class AgentConnectorController {

    private final AgentConnectorRestService agentConnectorRestService;

    /**
     * Health check endpoint.
     *
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "agent-connector"
        ));
    }

    /**
     * Get all available interaction states.
     *
     * @return list of interaction states
     */
    @GetMapping("/states")
    public ResponseEntity<List<String>> getStates() {
        List<String> states = Arrays.stream(InteractionState.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(states);
    }

    /**
     * Get all available interaction facts/events.
     *
     * @return list of interaction facts
     */
    @GetMapping("/facts")
    public ResponseEntity<List<String>> getFacts() {
        List<String> facts = Arrays.stream(InteractionFact.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(facts);
    }

    /**
     * Fire an interaction event.
     *
     * @param request the event request
     * @return the event response with transition result
     */
    @PostMapping("/events")
    public ResponseEntity<InteractionEventResponse> fireEvent(
            @RequestBody InteractionEventRequest request) {
        log.info("Received event request: interactionId={}, fact={}",
                request.getInteractionId(), request.getFact());

        InteractionEventResponse response = agentConnectorRestService.processEvent(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Fire an interaction event for a specific interaction.
     *
     * @param interactionId the interaction ID
     * @param fact the event/fact to fire
     * @param channelType the channel type (optional)
     * @return the event response with transition result
     */
    @PostMapping("/interactions/{interactionId}/events/{fact}")
    public ResponseEntity<InteractionEventResponse> fireEventForInteraction(
            @PathVariable String interactionId,
            @PathVariable InteractionFact fact,
            @RequestParam(required = false) String channelType) {

        InteractionEventRequest request = InteractionEventRequest.builder()
                .interactionId(interactionId)
                .fact(fact)
                .channelType(channelType)
                .build();

        InteractionEventResponse response = agentConnectorRestService.processEvent(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
