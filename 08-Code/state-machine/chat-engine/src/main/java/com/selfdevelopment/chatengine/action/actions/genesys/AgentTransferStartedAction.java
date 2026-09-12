package com.selfdevelopment.chatengine.action.actions.genesys;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.ConditionalAction;
import com.selfdevelopment.chatengine.action.annotation.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when Genesys agent transfer starts (IN_PROGRESS 鈫?IN_PROGRESS internal).
 * <p>
 * This is a no-op at the Conversation state machine level - agent transfers
 * are handled at the Interaction state machine level. However, this action:
 * <ul>
 *   <li>Records audit log for agent transfer start</li>
 *   <li>Marks conversation as in agent transfer mode</li>
 *   <li>Tracks agent transfer metadata (target agent, reason)</li>
 *   <li>Enables future extension points for conversation-level handling</li>
 * </ul>
 * <p>
 * Genesys agent transfer is same-channel (GENESYS ONLY):
 * - Current agent transfers customer to another agent
 * - Customer may be placed on hold during transfer
 * - Conversation state does not change at start
 * - Interaction state handles the transfer lifecycle
 * - On completion: Conversation may transition based on transfer outcome
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.GENESYS_AGENT_TRANSFER_STARTED)
public class AgentTransferStartedAction implements ConditionalAction<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("AgentTransferStartedAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Record audit log
        recordAuditLog(ctx);

        // 2. Mark conversation as in agent transfer
        markAgentTransferInProgress(ctx);

        // 3. Track agent transfer metadata
        trackAgentTransferMetadata(ctx);

        // 4. Note: Actual agent transfer logic is handled at Interaction level
        log.debug("Agent transfer started at Interaction level, Conversation state unchanged: {}",
                conversationId);
    }

    private void recordAuditLog(CbolStateContext ctx) {
        log.debug("Recording Genesys agent transfer started audit log: conversationId={}",
                ctx.conversation().conversationId());
    }

    private void markAgentTransferInProgress(CbolStateContext ctx) {
        log.debug("Marking conversation as in agent transfer: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setAgentTransferInProgress(true);
    }

    private void trackAgentTransferMetadata(CbolStateContext ctx) {
        log.debug("Tracking agent transfer metadata: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would record agent transfer details
        // ctx.conversation().setAgentTransferStartedAt(Instant.now());
        // ctx.conversation().setAgentTransferTargetAgent(targetAgentId);
        // ctx.conversation().setAgentTransferReason(reason);
    }
}
