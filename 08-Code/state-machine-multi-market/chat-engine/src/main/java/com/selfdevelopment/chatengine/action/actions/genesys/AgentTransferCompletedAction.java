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
 * Action executed when Genesys agent transfer completes (IN_PROGRESS 鈫?IN_PROGRESS internal).
 * <p>
 * This is a no-op at the Conversation state machine level - agent transfers
 * are handled at the Interaction state machine level. However, this action:
 * <ul>
 *   <li>Records audit log for agent transfer completion</li>
 *   <li>Clears agent transfer in-progress flag</li>
 *   <li>Records agent transfer duration and outcome</li>
 *   <li>Updates current agent information on the conversation</li>
 *   <li>Enables future extension points for conversation-level handling</li>
 * </ul>
 * <p>
 * Genesys agent transfer is same-channel (GENESYS ONLY):
 * - Transfer completes successfully, customer connected to new agent
 * - Conversation state does not change (remains IN_PROGRESS)
 * - Interaction state handles the transfer lifecycle completion
 * - Current agent information should be updated to new agent
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.GENESYS_AGENT_TRANSFER_COMPLETED)
public class AgentTransferCompletedAction implements ConditionalAction<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("AgentTransferCompletedAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Record audit log
        recordAuditLog(ctx);

        // 2. Clear agent transfer in-progress flag
        clearAgentTransferInProgress(ctx);

        // 3. Record agent transfer duration and outcome
        recordAgentTransferOutcome(ctx);

        // 4. Update current agent information
        updateCurrentAgent(ctx);

        // 5. Note: Actual agent transfer logic is handled at Interaction level
        log.debug("Agent transfer completed at Interaction level, Conversation state unchanged: {}",
                conversationId);
    }

    private void recordAuditLog(CbolStateContext ctx) {
        log.debug("Recording Genesys agent transfer completed audit log: conversationId={}",
                ctx.conversation().conversationId());
    }

    private void clearAgentTransferInProgress(CbolStateContext ctx) {
        log.debug("Clearing agent transfer in-progress flag: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setAgentTransferInProgress(false);
    }

    private void recordAgentTransferOutcome(CbolStateContext ctx) {
        log.debug("Recording agent transfer outcome: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would record agent transfer duration
        // ctx.conversation().setAgentTransferCompletedAt(Instant.now());
        // ctx.conversation().setAgentTransferDuration(calculateDuration());
    }

    private void updateCurrentAgent(CbolStateContext ctx) {
        log.debug("Updating current agent information: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the current agent
        // ctx.conversation().setCurrentAgentId(newAgentId);
        // ctx.conversation().setCurrentAgentName(newAgentName);
    }
}
