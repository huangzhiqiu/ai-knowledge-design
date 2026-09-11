package com.selfdevelopment.chatengine.action.actions.genesys;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.annotation.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when Genesys consult transfer starts (ACTIVE/IN_PROGRESS → ACTIVE/IN_PROGRESS internal).
 * <p>
 * This is a no-op at the Conversation state machine level - consult transfers
 * are handled at the Interaction state machine level. However, this action:
 * <ul>
 *   <li>Records audit log for consult transfer start</li>
 *   <li>Tracks consult transfer metadata on the conversation</li>
 *   <li>Marks conversation as in consult transfer mode</li>
 *   <li>Enables future extension points for conversation-level handling</li>
 * </ul>
 * <p>
 * Genesys consult transfer is same-channel (GENESYS ONLY):
 * - Agent consults another agent or supervisor
 * - Customer remains on the line
 * - Conversation state does not change
 * - Interaction state transitions to CONSULT_TRANSFER
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED)
public class ConsultTransferStartedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("ConsultTransferStartedAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Record audit log
        recordAuditLog(ctx);

        // 2. Mark conversation as in consult transfer
        markConsultTransferInProgress(ctx);

        // 3. Track consult transfer metadata
        trackConsultTransferMetadata(ctx);

        // 4. Note: Actual consult transfer logic is handled at Interaction level
        log.debug("Consult transfer handled at Interaction level, Conversation state unchanged: {}",
                conversationId);
    }

    private void recordAuditLog(CbolStateContext ctx) {
        log.debug("Recording Genesys consult transfer started audit log: conversationId={}",
                ctx.conversation().conversationId());
    }

    private void markConsultTransferInProgress(CbolStateContext ctx) {
        log.debug("Marking conversation as in consult transfer: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setConsultTransferInProgress(true);
    }

    private void trackConsultTransferMetadata(CbolStateContext ctx) {
        log.debug("Tracking consult transfer metadata: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would record consult transfer details
        // ctx.conversation().setConsultTransferStartedAt(Instant.now());
        // ctx.conversation().setConsultTransferTargetAgent(targetAgentId);
    }
}
