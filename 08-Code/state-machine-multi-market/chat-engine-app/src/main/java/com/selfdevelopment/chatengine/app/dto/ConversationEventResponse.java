package com.selfdevelopment.chatengine.app.dto;

import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for conversation event processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationEventResponse {

    /**
     * The conversation ID.
     */
    private String conversationId;

    /**
     * The previous state before transition.
     */
    private ConversationState fromState;

    /**
     * The new state after transition.
     */
    private ConversationState toState;

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
