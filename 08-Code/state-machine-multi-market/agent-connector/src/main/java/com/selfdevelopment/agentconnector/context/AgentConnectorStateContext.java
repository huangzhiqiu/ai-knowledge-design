package com.selfdevelopment.agentconnector.context;

import com.selfdevelopment.agentconnector.model.InteractionInstance;
import lombok.Builder;

/**
 * Context for agent connector state machine execution.
 * <p>
 * Holds the interaction instance and any additional context needed for
 * interaction state transitions.
 */
@Builder
public record AgentConnectorStateContext(
        InteractionInstance interaction,
        String market,
        String traceId
) {
}
