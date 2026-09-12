package com.selfdevelopment.chatengine.action.strategy;

import com.selfdevelopment.chatengine.context.CbolStateContext;

/**
 * Strategy interface for market-specific transfer logic.
 * <p>
 * Different markets may use different transfer mechanisms:
 * - GENESYS: Genesys cloud transfer
 * - INTERNAL_QUEUE: Internal queue-based transfer
 * - AIBOT: AI bot handoff
 * <p>
 * Implementations are registered in TransferStrategyRegistry and
 * resolved based on market config's transferTarget.
 */
public interface TransferStrategy {

    /**
     * Get the transfer target this strategy handles.
     *
     * @return transfer target (e.g., "GENESYS", "INTERNAL_QUEUE", "AIBOT")
     */
    String getTransferTarget();

    /**
     * Execute the transfer logic.
     *
     * @param context state context
     */
    void execute(CbolStateContext context);

    /**
     * Check if this strategy is available for the given context.
     *
     * @param context state context
     * @return true if strategy can be executed
     */
    default boolean isAvailable(CbolStateContext context) {
        return true;
    }
}
