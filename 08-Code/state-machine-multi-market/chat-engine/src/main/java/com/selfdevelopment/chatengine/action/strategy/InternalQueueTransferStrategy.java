package com.selfdevelopment.chatengine.action.strategy;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal queue-based transfer strategy.
 * <p>
 * Handles transfer to internal agent queue.
 * Default transfer target when no external integration is configured.
 */
public class InternalQueueTransferStrategy implements TransferStrategy {

    private static final Logger log = LoggerFactory.getLogger(InternalQueueTransferStrategy.class);

    @Override
    public String getTransferTarget() {
        return "INTERNAL_QUEUE";
    }

    @Override
    public void execute(CbolStateContext context) {
        log.info("Executing internal queue transfer for conversation: {}",
                context.conversation() != null ? context.conversation().conversationId() : "unknown");

        // TODO: Implement actual internal queue transfer logic
        log.info("Conversation queued for internal agent assignment");
    }

    @Override
    public boolean isAvailable(CbolStateContext context) {
        return context.marketConfig() != null
                && context.marketConfig().transferEnabled();
    }
}
