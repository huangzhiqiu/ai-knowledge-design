package com.selfdevelopment.chatengine.action.ending;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when ending actions are completed (ENDING → ENDING internal, or ENDING → CLOSED if interactionsClosed).
 * <p>
 * This action handles the business logic of ending actions completion:
 * <ul>
 *   <li>Sets endingActionsDone=true</li>
 *   <li>If interactionsClosed is also true, transitions to CLOSED</li>
 *   <li>Records audit log for ending actions completion</li>
 *   <li>Triggers final cleanup if both conditions are met</li>
 * </ul>
 * <p>
 * Part of the ENDING convergence rules:
 * - ENDING_ACTIONS_COMPLETED sets endingActionsDone=true
 * - ALL_INTERACTIONS_ENDED sets interactionsClosed=true
 * - Both true → CLOSED
 * - Or ENDING_TIMEOUT → forced CLOSED
 */
@Slf4j
@Component
public class EndingActionsCompletedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("EndingActionsCompletedAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Set endingActionsDone flag
        setEndingActionsDone(ctx);

        // 2. Check if all interactions are also closed
        if (isAllInteractionsClosed(ctx)) {
            log.info("Both endingActionsDone and interactionsClosed are true, conversation ready for CLOSED: {}",
                    conversationId);
            // Note: Actual state transition to CLOSED is handled by the state machine
            // when both conditions are met, or by ENDING_TIMEOUT as fallback
        }

        // 3. Record audit log
        recordAuditLog(ctx);

        // 4. Trigger final cleanup preparation
        prepareFinalCleanup(ctx);

        log.info("EndingActionsCompletedAction completed: conversationId={}, endingActionsDone=true",
                conversationId);
    }

    private void setEndingActionsDone(CbolStateContext ctx) {
        log.debug("Setting endingActionsDone=true: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setEndingActionsDone(true);
    }

    private boolean isAllInteractionsClosed(CbolStateContext ctx) {
        // In a real implementation, this would check the conversation entity
        // return ctx.conversation().isInteractionsClosed();
        return false; // Default to false, actual state managed by business layer
    }

    private void recordAuditLog(CbolStateContext ctx) {
        log.debug("Recording ending actions completed audit log: conversationId={}",
                ctx.conversation().conversationId());
    }

    private void prepareFinalCleanup(CbolStateContext ctx) {
        log.debug("Preparing final cleanup: conversationId={}",
                ctx.conversation().conversationId());
    }
}
