package com.selfdevelopment.agentconnector.app.dto;

import com.selfdevelopment.agentconnector.enums.InteractionFact;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for firing an interaction event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionEventRequest {

    /**
     * The interaction ID.
     */
    private String interactionId;

    /**
     * The conversation ID associated with this interaction.
     */
    private String conversationId;

    /**
     * The event/fact to fire.
     */
    private InteractionFact fact;

    /**
     * The channel type (e.g., GENESYS, AIBOT).
     */
    private String channelType;

    /**
     * Additional metadata for the event.
     */
    private Map<String, Object> metadata;
}
