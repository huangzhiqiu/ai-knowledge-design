package com.selfdevelopment.chatengine.action.strategy;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for transfer strategies.
 * <p>
 * Resolves the appropriate TransferStrategy based on market config's transferTarget.
 * Supports auto-registration of strategies and fallback to default strategy.
 */
public class TransferStrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(TransferStrategyRegistry.class);

    private final Map<String, TransferStrategy> strategies = new ConcurrentHashMap<>();
    private final TransferStrategy defaultStrategy;

    public TransferStrategyRegistry(List<TransferStrategy> strategies, TransferStrategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy != null ? defaultStrategy : new InternalQueueTransferStrategy();

        if (strategies != null) {
            for (TransferStrategy strategy : strategies) {
                register(strategy);
            }
        }

        // Always ensure default strategy is registered
        if (!this.strategies.containsKey(this.defaultStrategy.getTransferTarget())) {
            register(this.defaultStrategy);
        }
    }

    /**
     * Register a transfer strategy.
     *
     * @param strategy strategy to register
     */
    public void register(TransferStrategy strategy) {
        if (strategy == null) {
            return;
        }
        strategies.put(strategy.getTransferTarget(), strategy);
        log.info("Registered transfer strategy: {}", strategy.getTransferTarget());
    }

    /**
     * Resolve strategy for the given market config.
     *
     * @param config market config
     * @return resolved strategy, or default if not found
     */
    public TransferStrategy resolve(StateMachineMarketConfig config) {
        if (config == null || config.transferTarget() == null) {
            log.debug("No transferTarget configured, using default strategy");
            return defaultStrategy;
        }

        TransferStrategy strategy = strategies.get(config.transferTarget());
        if (strategy == null) {
            log.warn("No transfer strategy found for target: {}, using default", config.transferTarget());
            return defaultStrategy;
        }

        return strategy;
    }

    /**
     * Resolve and execute strategy for the given context.
     *
     * @param context state context
     */
    public void execute(CbolStateContext context) {
        if (context == null || context.marketConfig() == null) {
            log.warn("Cannot execute transfer: context or marketConfig is null");
            return;
        }

        TransferStrategy strategy = resolve(context.marketConfig());

        if (!strategy.isAvailable(context)) {
            log.warn("Transfer strategy {} is not available, using default", strategy.getTransferTarget());
            defaultStrategy.execute(context);
            return;
        }

        strategy.execute(context);
    }

    /**
     * Get all registered strategy targets.
     */
    public java.util.Set<String> getRegisteredTargets() {
        return strategies.keySet();
    }

    /**
     * Create registry with default strategies.
     */
    public static TransferStrategyRegistry createDefault() {
        return new TransferStrategyRegistry(
                List.of(
                        new GenesysTransferStrategy(),
                        new InternalQueueTransferStrategy(),
                        new AibotTransferStrategy()
                ),
                new InternalQueueTransferStrategy()
        );
    }
}
