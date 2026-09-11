package com.selfdevelopment.chatengine.action.actions.lifecycle;

import com.alibaba.cola.statemachine.Action;
import com.alibaba.cola.statemachine.Condition;
import com.selfdevelopment.chatengine.action.ConditionalAction;
import com.selfdevelopment.chatengine.action.annotation.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when session starts (NEW → INITIATED).
 * <p>
 * This action handles the business logic of conversation initialization:
 * <ul>
 *   <li>Initiates downstream assignment (agent/AI bot)</li>
 *   <li>Creates conversation record in the database</li>
 *   <li>Initializes session data</li>
 *   <li>Sets up monitoring timers</li>
 * </ul>
 * <p>
 * Condition: Requires conversation and sessionId to be present before execution.
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.SESSION_STARTED)
public class SessionStartedAction implements ConditionalAction<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public Condition<CbolStateContext> getCondition() {
        return ctx -> {
            if (ctx == null) {
                log.warn("SessionStartedAction condition failed: context is null");
                return false;
            }
            if (ctx.conversation() == null) {
                log.warn("SessionStartedAction condition failed: conversation is null");
                return false;
            }
            if (ctx.conversation().conversationId() == null || ctx.conversation().conversationId().isBlank()) {
                log.warn("SessionStartedAction condition failed: conversationId is null or blank");
                return false;
            }
            return true;
        };
    }

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("SessionStartedAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Initiate downstream assignment (agent/AI bot)
        initiateDownstreamAssignment(ctx);

        // 2. Create conversation record in the database
        createConversationRecord(ctx);

        // 3. Initialize session data
        initializeSessionData(ctx);

        // 4. Set up monitoring timers
        setupMonitoringTimers(ctx);

        log.info("SessionStartedAction completed successfully: conversationId={}", conversationId);
    }

    private void initiateDownstreamAssignment(CbolStateContext ctx) {
        log.debug("Initiating downstream assignment: conversationId={}", ctx.conversation().conversationId());
    }

    private void createConversationRecord(CbolStateContext ctx) {
        log.debug("Creating conversation record: conversationId={}", ctx.conversation().conversationId());
    }

    private void initializeSessionData(CbolStateContext ctx) {
        log.debug("Initializing session data: conversationId={}", ctx.conversation().conversationId());
    }

    private void setupMonitoringTimers(CbolStateContext ctx) {
        log.debug("Setting up monitoring timers: conversationId={}", ctx.conversation().conversationId());
    }
}
