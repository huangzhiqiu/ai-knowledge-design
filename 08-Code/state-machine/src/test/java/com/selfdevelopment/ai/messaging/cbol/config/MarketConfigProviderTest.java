package com.selfdevelopment.ai.messaging.cbol.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarketConfigProviderTest {

    @Test
    void testFallback() {
        MarketConfigProvider.InMemoryProvider provider = new MarketConfigProvider.InMemoryProvider();
        StateMachineMarketConfig cfg = provider.getConfig("SG");
        assertNotNull(cfg);
        assertEquals(300, cfg.customerIdleSeconds());
    }

    @Test
    void testPutAndInvalidate() {
        MarketConfigProvider.InMemoryProvider provider = new MarketConfigProvider.InMemoryProvider();
        StateMachineMarketConfig custom = StateMachineMarketConfig.builder()
                .customerIdleSeconds(600)
                .transferTimeoutSeconds(200)
                .endingGraceSeconds(100)
                .surveyEnabled(true)
                .transferEnabled(true)
                .genesysEnabled(false)
                .fallbackRoutingStrategy("DROP")
                .build();
        provider.put("MY", custom);
        assertEquals(600, provider.getConfig("MY").customerIdleSeconds());
        provider.invalidate("MY");
        assertEquals(300, provider.getConfig("MY").customerIdleSeconds());
    }
}