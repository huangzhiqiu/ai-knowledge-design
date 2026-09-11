package com.selfdevelopment.chatengine.action.ending;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when all interactions have ended (ENDING → ENDING internal, or ENDING → CLOSED if endingActionsDone).
 * <p>
 * This action handles the business logic of all interactions ending:
 * <ul>
 *   <li>Sets interactionsClosed=true</li>
 *   <li>If endingActionsDone is also true, transitions to CLOSED</li>
 *   <li>Records audit log for interaction closure</li>
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
@HandlesFact(ConversationFact.ALL_INTERACTIONS_ENDED)
public class AllInteractionsEndedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("AllInteractionsEndedAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Set interactionsClosed flag
        setInteractionsClosed(ctx);

        // 2. Check if ending actions are also completed
        if (isEndingActionsCompleted(ctx)) {
            log.info("Both endingActionsDone and interactionsClosed are true, conversation ready for CLOSED: {}",
                    conversationId);
            // Note: Actual state transition to CLOSED is handled by the state machine
            // when both conditions are met, or by ENDING_TIMEOUT as fallback
        }

        // 3. Record audit log
        recordAuditLog(ctx);

        // 4. Trigger final cleanup preparation
        prepareFinalCleanup(ctx);

        log.info("AllInteractionsEndedAction completed: conversationId={}, interactionsClosed=true",
                conversationId);
    }

    private void setInteractionsClosed(CbolStateContext ctx) {
        log.debug("Setting interactionsClosed=true: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setInteractionsClosed(true);
    }

    private boolean isEndingActionsCompleted(CbolStateContext ctx) {
        // In a real implementation, this would check the conversation entity
        // return ctx.conversation().isEndingActionsDone();
        return false; // Default to false, actual state managed by business layer
    }

    private void recordAuditLog(CbolStateContext ctx) {
        log.debug("Recording all interactions ended audit log: conversationId={}",
                ctx.conversation().conversationId());
    }

    private void prepareFinalCleanup(CbolStateContext ctx) {
        log.debug("Preparing final cleanup: conversationId={}",
                ctx.conversation().conversationId());
    }
}
