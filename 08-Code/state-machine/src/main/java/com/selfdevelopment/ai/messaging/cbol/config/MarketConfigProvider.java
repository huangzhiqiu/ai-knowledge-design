package com.selfdevelopment.ai.messaging.cbol.config;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public interface MarketConfigProvider {
    StateMachineMarketConfig getConfig(String market);
    void invalidate(String market);

    class InMemoryProvider implements MarketConfigProvider {
        private final ConcurrentHashMap<String, StateMachineMarketConfig> cache = new ConcurrentHashMap<>();
        private final StateMachineMarketConfig fallback = StateMachineMarketConfig.defaultConfig();

        @Override
        public StateMachineMarketConfig getConfig(String market) {
            Objects.requireNonNull(market, "market must not be null");
            return cache.getOrDefault(market, fallback);
        }

        @Override
        public void invalidate(String market) {
            Objects.requireNonNull(market, "market must not be null");
            cache.remove(market);
        }

        public void put(String market, StateMachineMarketConfig config) {
            Objects.requireNonNull(market, "market must not be null");
            Objects.requireNonNull(config, "config must not be null");
            cache.put(market, config);
        }
    }
}