package com.selfdevelopment.agentconnector.app.dto;

import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for interaction event processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionEventResponse {

    /**
     * The interaction ID.
     */
    private String interactionId;

    /**
     * The previous state before transition.
     */
    private InteractionState fromState;

    /**
     * The new state after transition.
     */
    private InteractionState toState;

    /**
     * The event/fact that was fired.
     */
    private String fact;

    /**
     * Whether the transition was successful.
     */
    private boolean success;

    /**
     * Error message if transition failed.
     */
    private String errorMessage;

    /**
     * The trace ID for this request.
     */
    private String traceId;

    /**
     * The timestamp of the response.
     */
    private Instant timestamp;
}
