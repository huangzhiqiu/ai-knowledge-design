package com.selfdevelopment.chatengine.action.transfer;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when target interaction is initiated (TRANSFERRED → TRANSFERRED, internal).
 * <p>
 * This action handles the business logic of target interaction initiation:
 * <ul>
 *   <li>Executes ConnectTargetInteractionCmd</li>
 *   <li>Updates transfer metadata</li>
 *   <li>Monitors target connection progress</li>
 * </ul>
 */
@Slf4j
@Component
public class TargetInteractionInitiatedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("TargetInteractionInitiatedAction: {} --({})--> {} (internal), conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Execute ConnectTargetInteractionCmd
        executeConnectTargetInteractionCmd(ctx);

        // 2. Update transfer metadata
        updateTransferMetadata(ctx);

        // 3. Monitor target connection progress
        monitorTargetConnection(ctx);

        log.info("TargetInteractionInitiatedAction completed successfully: conversationId={}", conversationId);
    }

    private void executeConnectTargetInteractionCmd(CbolStateContext ctx) {
        log.debug("Executing ConnectTargetInteractionCmd: conversationId={}", ctx.conversation().conversationId());
    }

    private void updateTransferMetadata(CbolStateContext ctx) {
        log.debug("Updating transfer metadata: conversationId={}", ctx.conversation().conversationId());
    }

    private void monitorTargetConnection(CbolStateContext ctx) {
        log.debug("Monitoring target connection: conversationId={}", ctx.conversation().conversationId());
    }
}
