package com.selfdevelopment.chatengine.routing;

import com.selfdevelopment.chatengine.config.MarketConfigRepository;
import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Routes requests to the appropriate market configuration.
 * <p>
 * Resolves market from request context and provides the corresponding config.
 * Supports market aliases and default market fallback.
 */
public class MarketRouter {

    private static final Logger log = LoggerFactory.getLogger(MarketRouter.class);

    private final MarketConfigRepository configRepository;
    private final String defaultMarket;

    public MarketRouter(MarketConfigRepository configRepository, String defaultMarket) {
        this.configRepository = configRepository;
        this.defaultMarket = defaultMarket != null ? defaultMarket.toUpperCase() : "HK";
    }

    /**
     * Route to market config based on market code.
     *
     * @param market market code (e.g., "HK", "UK", "SG")
     * @return market config
     */
    public StateMachineMarketConfig route(String market) {
        String resolvedMarket = resolveMarket(market);
        log.debug("Routing to market: {} (requested: {})", resolvedMarket, market);
        return configRepository.getConfig(resolvedMarket);
    }

    /**
     * Resolve market code, applying aliases and default fallback.
     *
     * @param market requested market
     * @return resolved market code
     */
    public String resolveMarket(String market) {
        if (market == null || market.isBlank()) {
            log.debug("No market specified, using default: {}", defaultMarket);
            return defaultMarket;
        }

        String normalized = market.trim().toUpperCase();

        // Check if market is supported
        Set<String> supportedMarkets = configRepository.getCachedMarkets();
        if (!supportedMarkets.isEmpty() && !supportedMarkets.contains(normalized)) {
            log.warn("Market {} not in cache, will attempt to resolve. Supported: {}", normalized, supportedMarkets);
        }

        return normalized;
    }

    /**
     * Get default market.
     */
    public String getDefaultMarket() {
        return defaultMarket;
    }

    /**
     * Check if a market is supported (has config).
     */
    public boolean isMarketSupported(String market) {
        if (market == null) {
            return false;
        }
        return configRepository.isCached(market) ||
                configRepository.getConfig(market) != null;
    }

    /**
     * Create router with default config repository and HK as default market.
     */
    public static MarketRouter createDefault() {
        return new MarketRouter(MarketConfigRepository.createDefault(), "HK");
    }
}
