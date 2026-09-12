package com.selfdevelopment.chatengine.action.strategy;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI bot transfer strategy.
 * <p>
 * Handles transfer/handoff to AI bot.
 * Requires aibotEnabled=true and aibotEndpoint configured.
 */
public class AibotTransferStrategy implements TransferStrategy {

    private static final Logger log = LoggerFactory.getLogger(AibotTransferStrategy.class);

    @Override
    public String getTransferTarget() {
        return "AIBOT";
    }

    @Override
    public void execute(CbolStateContext context) {
        log.info("Executing AI bot transfer for conversation: {}",
                context.conversation() != null ? context.conversation().conversationId() : "unknown");

        if (context.marketConfig() == null || !context.marketConfig().aibotEnabled()) {
            log.warn("AI bot transfer requested but aibotEnabled is false");
            return;
        }

        String aibotEndpoint = context.marketConfig().aibotEndpoint();
        if (aibotEndpoint == null || aibotEndpoint.isBlank()) {
            log.error("AI bot transfer failed: aibotEndpoint not configured");
            return;
        }

        // TODO: Implement actual AI bot API call
        log.info("Transferring to AI bot endpoint: {}", aibotEndpoint);
    }

    @Override
    public boolean isAvailable(CbolStateContext context) {
        return context.marketConfig() != null
                && context.marketConfig().aibotEnabled()
                && context.marketConfig().aibotEndpoint() != null
                && !context.marketConfig().aibotEndpoint().isBlank();
    }
}
