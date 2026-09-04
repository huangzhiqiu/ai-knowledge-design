package com.selfdevelopment.chatengine.action.impl;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when transitioning from NEW to INITIATED.
 * <p>
 * This action performs conversation initialization preparation:
 * <ul>
 *   <li>Validate conversation configuration and market settings</li>
 *   <li>Initialize conversation metadata and timestamps</li>
 *   <li>Allocate necessary resources (session, channels)</li>
 *   <li>Set up routing rules based on market configuration</li>
 *   <li>Initialize audit logging for the conversation</li>
 * </ul>
 * <p>
 * After this action completes successfully, the conversation enters INITIATED state,
 * ready for customer connection (CUSTOMER_CONNECT → IN_PROGRESS).
 */
@Slf4j
public class ConversationInitAction
        implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(StateContext<ConversationState, ConversationFact, CbolStateContext> context) {
        CbolStateContext ctx = context.getBusinessContext();

        // 1. Extract data from context
        String conversationId = ctx.conversation().conversationId();
        String market = ctx.conversation().market();

        // 2. Validate conversation configuration
        validateConversationConfig(ctx);

        // 3. Initialize conversation metadata
        initializeConversationMetadata(ctx);

        // 4. Allocate resources (simulated in demo, real implementation calls services)
        allocateResources(ctx);

        // 5. Set up routing rules
        setupRoutingRules(ctx);

        // 6. Log completion
        log.info("ConversationInitAction completed: conversationId={}, market={}, " +
                        "conversation initialized and ready for customer connection",
                conversationId, market);
    }

    /**
     * Validates conversation configuration and market settings.
     * Throws IllegalStateException if configuration is invalid.
     */
    private void validateConversationConfig(CbolStateContext ctx) {
        if (ctx.conversation().conversationId() == null || ctx.conversation().conversationId().isBlank()) {
            throw new IllegalStateException("Conversation ID must not be null or blank");
        }
        if (ctx.conversation().market() == null || ctx.conversation().market().isBlank()) {
            throw new IllegalStateException("Market must not be null or blank for conversation: "
                    + ctx.conversation().conversationId());
        }
        if (ctx.marketConfig() == null) {
            throw new IllegalStateException("Market configuration must not be null for conversation: "
                    + ctx.conversation().conversationId());
        }
        log.debug("Conversation configuration validated: conversationId={}",
                ctx.conversation().conversationId());
    }

    /**
     * Initializes conversation metadata and timestamps.
     */
    private void initializeConversationMetadata(CbolStateContext ctx) {
        // In real implementation, this would update the conversation record with:
        // - initialization timestamp
        // - session ID
        // - initial state history entry
        log.debug("Conversation metadata initialized: conversationId={}",
                ctx.conversation().conversationId());
    }

    /**
     * Allocates necessary resources for the conversation.
     */
    private void allocateResources(CbolStateContext ctx) {
        // In real implementation, this would:
        // - create a session in the session store
        // - initialize message channel
        // - set up WebSocket connection context
        log.debug("Resources allocated for conversation: conversationId={}",
                ctx.conversation().conversationId());
    }

    /**
     * Sets up routing rules based on market configuration.
     */
    private void setupRoutingRules(CbolStateContext ctx) {
        // In real implementation, this would:
        // - load market-specific routing rules
        // - configure agent assignment strategy
        // - set up fallback routing (AI bot, queue, etc.)
        log.debug("Routing rules set up for conversation: conversationId={}, market={}",
                ctx.conversation().conversationId(), ctx.conversation().market());
    }
}
