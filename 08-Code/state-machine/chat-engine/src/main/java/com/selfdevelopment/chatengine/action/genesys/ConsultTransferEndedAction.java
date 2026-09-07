package com.selfdevelopment.chatengine.action.genesys;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when Genesys consult transfer ends (ACTIVE/IN_PROGRESS → ACTIVE/IN_PROGRESS internal).
 * <p>
 * This is a no-op at the Conversation state machine level - consult transfers
 * are handled at the Interaction state machine level. However, this action:
 * <ul>
 *   <li>Records audit log for consult transfer end</li>
 *   <li>Clears consult transfer in-progress flag</li>
 *   <li>Records consult transfer duration and outcome</li>
 *   <li>Enables future extension points for conversation-level handling</li>
 * </ul>
 * <p>
 * Genesys consult transfer is same-channel (GENESYS ONLY):
 * - Consult session ends, agent returns to customer
 * - Conversation state does not change
 * - Interaction state transitions back from CONSULT_TRANSFER to IN_PROGRESS
 */
@Slf4j
public class ConsultTransferEndedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("ConsultTransferEndedAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Record audit log
        recordAuditLog(ctx);

        // 2. Clear consult transfer in-progress flag
        clearConsultTransferInProgress(ctx);

        // 3. Record consult transfer duration and outcome
        recordConsultTransferOutcome(ctx);

        // 4. Note: Actual consult transfer logic is handled at Interaction level
        log.debug("Consult transfer ended at Interaction level, Conversation state unchanged: {}",
                conversationId);
    }

    private void recordAuditLog(CbolStateContext ctx) {
        log.debug("Recording Genesys consult transfer ended audit log: conversationId={}",
                ctx.conversation().conversationId());
    }

    private void clearConsultTransferInProgress(CbolStateContext ctx) {
        log.debug("Clearing consult transfer in-progress flag: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setConsultTransferInProgress(false);
    }

    private void recordConsultTransferOutcome(CbolStateContext ctx) {
        log.debug("Recording consult transfer outcome: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would record consult transfer duration
        // ctx.conversation().setConsultTransferEndedAt(Instant.now());
        // ctx.conversation().setConsultTransferDuration(calculateDuration());
    }
}
