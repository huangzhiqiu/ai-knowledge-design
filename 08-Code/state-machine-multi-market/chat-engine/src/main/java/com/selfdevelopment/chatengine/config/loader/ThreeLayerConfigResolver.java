package com.selfdevelopment.chatengine.config.loader;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

/**
 * Three-layer config inheritance resolver.
 * <p>
 * Config inheritance model:
 * <pre>
 * Layer 1: base-profile.yaml (global defaults)
 *    ↓ inherits
 * Layer 2: regional-profiles/{region}.yaml (regional overrides)
 *    ↓ inherits
 * Layer 3: market-profiles/{market}.yaml (market-specific overrides)
 * </pre>
 * <p>
 * Merge priority: market override > regional override > base default
 * <p>
 * Directory structure:
 * <pre>
 * config/
 * ├── base-profile.yaml
 * ├── regional-profiles/
 * │   ├── apac.yaml
 * │   ├── emea.yaml
 * │   └── amer.yaml
 * └── market-profiles/
 *     ├── hk.yaml
 *     ├── sg.yaml
 *     ├── uk.yaml
 *     └── us.yaml
 * </pre>
 */
public class ThreeLayerConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(ThreeLayerConfigResolver.class);

    private final YamlMarketConfigLoader loader;
    private final Path configRoot;

    // Market to region mapping
    private static final Map<String, String> MARKET_TO_REGION = Map.of(
            "HK", "apac",
            "SG", "apac",
            "JP", "apac",
            "AU", "apac",
            "UK", "emea",
            "DE", "emea",
            "FR", "emea",
            "US", "amer",
            "CA", "amer",
            "BR", "amer"
    );

    public ThreeLayerConfigResolver(YamlMarketConfigLoader loader, Path configRoot) {
        this.loader = loader;
        this.configRoot = configRoot;
    }

    /**
     * Resolve config for a market using three-layer inheritance.
     *
     * @param market market code (e.g., "HK", "UK", "SG")
     * @return merged config
     */
    public StateMachineMarketConfig resolve(String market) {
        log.info("Resolving config for market: {}", market);

        // Layer 1: Base profile
        StateMachineMarketConfig baseConfig = loadBaseProfile();
        log.debug("Base config loaded: {}", baseConfig);

        // Layer 2: Regional profile
        String region = getRegionForMarket(market);
        StateMachineMarketConfig regionalConfig = loadRegionalProfile(region);
        StateMachineMarketConfig mergedWithRegional = baseConfig.mergeWith(regionalConfig);
        log.debug("Regional config merged (region={}): {}", region, mergedWithRegional);

        // Layer 3: Market profile
        StateMachineMarketConfig marketConfig = loadMarketProfile(market);
        StateMachineMarketConfig finalConfig = mergedWithRegional.mergeWith(marketConfig);
        log.info("Final config for market {}: {}", market, finalConfig);

        return finalConfig;
    }

    /**
     * Get all supported markets.
     */
    public Set<String> getSupportedMarkets() {
        return MARKET_TO_REGION.keySet();
    }

    /**
     * Get region for a market.
     */
    public String getRegionForMarket(String market) {
        return MARKET_TO_REGION.getOrDefault(market.toUpperCase(), "apac");
    }

    private StateMachineMarketConfig loadBaseProfile() {
        Path basePath = configRoot.resolve("base-profile.yaml");
        return loader.loadFromFile(basePath);
    }

    private StateMachineMarketConfig loadRegionalProfile(String region) {
        if (region == null) {
            return StateMachineMarketConfig.defaultConfig();
        }
        Path regionalPath = configRoot.resolve("regional-profiles").resolve(region + ".yaml");
        return loader.loadFromFile(regionalPath);
    }

    private StateMachineMarketConfig loadMarketProfile(String market) {
        if (market == null) {
            return StateMachineMarketConfig.defaultConfig();
        }
        Path marketPath = configRoot.resolve("market-profiles").resolve(market.toLowerCase() + ".yaml");
        return loader.loadFromFile(marketPath);
    }

    /**
     * Create resolver with default config root (src/main/resources/config).
     */
    public static ThreeLayerConfigResolver createDefault() {
        YamlMarketConfigLoader loader = new YamlMarketConfigLoader();
        Path configRoot = Paths.get("src/main/resources/config");
        return new ThreeLayerConfigResolver(loader, configRoot);
    }

    /**
     * Create resolver with custom config root.
     */
    public static ThreeLayerConfigResolver create(Path configRoot) {
        YamlMarketConfigLoader loader = new YamlMarketConfigLoader();
        return new ThreeLayerConfigResolver(loader, configRoot);
    }
}
