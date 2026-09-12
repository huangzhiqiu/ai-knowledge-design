package com.selfdevelopment.chatengine.app.dto;

import com.selfdevelopment.chatengine.enums.ConversationFact;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for firing a conversation event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationEventRequest {

    /**
     * The conversation ID.
     */
    private String conversationId;

    /**
     * The event/fact to fire.
     */
    private ConversationFact fact;

    /**
     * The market (e.g., HK, SG, UK).
     */
    private String market;

    /**
     * The tenant ID.
     */
    private String tenantId;

    /**
     * Additional metadata for the event.
     */
    private java.util.Map<String, Object> metadata;
}
