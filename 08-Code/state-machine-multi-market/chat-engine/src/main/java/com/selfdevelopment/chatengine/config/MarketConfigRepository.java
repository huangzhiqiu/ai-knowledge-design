package com.selfdevelopment.chatengine.config;

import com.selfdevelopment.chatengine.config.loader.ThreeLayerConfigResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for market configurations.
 * <p>
 * Caches resolved configs per market with support for hot-reload.
 * Uses three-layer config inheritance (base → regional → market).
 */
public class MarketConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(MarketConfigRepository.class);

    private final ThreeLayerConfigResolver resolver;
    private final Map<String, StateMachineMarketConfig> configCache = new ConcurrentHashMap<>();

    public MarketConfigRepository(ThreeLayerConfigResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Get config for a market. Uses cached config if available.
     *
     * @param market market code
     * @return market config
     */
    public StateMachineMarketConfig getConfig(String market) {
        if (market == null) {
            log.warn("Null market provided, using default config");
            return StateMachineMarketConfig.defaultConfig();
        }
        return configCache.computeIfAbsent(market.toUpperCase(), this::resolveAndCache);
    }

    /**
     * Get config for a market, forcing a fresh load (bypasses cache).
     *
     * @param market market code
     * @return fresh market config
     */
    public StateMachineMarketConfig getConfigFresh(String market) {
        if (market == null) {
            return StateMachineMarketConfig.defaultConfig();
        }
        StateMachineMarketConfig config = resolver.resolve(market.toUpperCase());
        configCache.put(market.toUpperCase(), config);
        return config;
    }

    /**
     * Refresh all cached configs (hot-reload).
     */
    public void refreshAll() {
        log.info("Refreshing all market configs...");
        Set<String> markets = configCache.keySet();
        for (String market : markets) {
            try {
                StateMachineMarketConfig newConfig = resolver.resolve(market);
                configCache.put(market, newConfig);
                log.info("Refreshed config for market: {}", market);
            } catch (Exception e) {
                log.error("Failed to refresh config for market: {}", market, e);
            }
        }
        log.info("All market configs refreshed. Total: {}", markets.size());
    }

    /**
     * Refresh config for a specific market.
     *
     * @param market market code
     */
    public void refresh(String market) {
        if (market == null) {
            return;
        }
        log.info("Refreshing config for market: {}", market);
        StateMachineMarketConfig newConfig = resolver.resolve(market.toUpperCase());
        configCache.put(market.toUpperCase(), newConfig);
    }

    /**
     * Get all cached markets.
     */
    public Set<String> getCachedMarkets() {
        return Set.copyOf(configCache.keySet());
    }

    /**
     * Check if a market config is cached.
     */
    public boolean isCached(String market) {
        return market != null && configCache.containsKey(market.toUpperCase());
    }

    /**
     * Clear all cached configs.
     */
    public void clearCache() {
        log.info("Clearing all market config cache");
        configCache.clear();
    }

    private StateMachineMarketConfig resolveAndCache(String market) {
        log.info("Resolving config for market (not cached): {}", market);
        return resolver.resolve(market);
    }

    /**
     * Create repository with default resolver.
     */
    public static MarketConfigRepository createDefault() {
        return new MarketConfigRepository(ThreeLayerConfigResolver.createDefault());
    }
}
