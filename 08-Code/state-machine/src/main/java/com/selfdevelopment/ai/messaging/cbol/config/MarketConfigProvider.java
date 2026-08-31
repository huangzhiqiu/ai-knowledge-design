package com.selfdevelopment.ai.messaging.cbol.config;

import java.util.concurrent.ConcurrentHashMap;

public interface MarketConfigProvider {
    StateMachineMarketConfig getConfig(String market);
    void invalidate(String market);

    class InMemoryProvider implements MarketConfigProvider {
        private final ConcurrentHashMap<String, StateMachineMarketConfig> cache = new ConcurrentHashMap<>();
        private final StateMachineMarketConfig fallback = StateMachineMarketConfig.defaultConfig();

        @Override
        public StateMachineMarketConfig getConfig(String market) {
            return cache.getOrDefault(market, fallback);
        }

        @Override
        public void invalidate(String market) {
            cache.remove(market);
        }

        public void put(String market, StateMachineMarketConfig config) {
            cache.put(market, config);
        }
    }
}