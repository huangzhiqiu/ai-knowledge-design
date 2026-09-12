package com.selfdevelopment.chatengine.action.strategy;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Genesys cloud transfer strategy.
 * <p>
 * Handles transfer to Genesys contact center.
 * Requires genesysEnabled=true and genesysOrgId configured.
 */
public class GenesysTransferStrategy implements TransferStrategy {

    private static final Logger log = LoggerFactory.getLogger(GenesysTransferStrategy.class);

    @Override
    public String getTransferTarget() {
        return "GENESYS";
    }

    @Override
    public void execute(CbolStateContext context) {
        log.info("Executing Genesys transfer for conversation: {}",
                context.conversation() != null ? context.conversation().conversationId() : "unknown");

        if (context.marketConfig() == null || !context.marketConfig().genesysEnabled()) {
            log.warn("Genesys transfer requested but genesysEnabled is false");
            return;
        }

        String genesysOrgId = context.marketConfig().genesysOrgId();
        if (genesysOrgId == null || genesysOrgId.isBlank()) {
            log.error("Genesys transfer failed: genesysOrgId not configured");
            return;
        }

        // TODO: Implement actual Genesys API call
        log.info("Transferring to Genesys org: {}", genesysOrgId);
    }

    @Override
    public boolean isAvailable(CbolStateContext context) {
        return context.marketConfig() != null
                && context.marketConfig().genesysEnabled()
                && context.marketConfig().genesysOrgId() != null
                && !context.marketConfig().genesysOrgId().isBlank();
    }
}
