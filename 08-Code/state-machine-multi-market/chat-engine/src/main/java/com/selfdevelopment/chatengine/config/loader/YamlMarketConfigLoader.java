package com.selfdevelopment.chatengine.config.loader;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads market configuration from YAML files.
 * <p>
 * Supports loading from classpath or filesystem paths.
 * YAML structure:
 * <pre>
 * market: HK
 * profile: hk-standard
 * timeouts:
 *   customerIdleSeconds: 300
 *   transferTimeoutSeconds: 180
 * features:
 *   surveyEnabled: true
 *   transferEnabled: true
 * businessRules:
 *   fallbackRoutingStrategy: REQUEUE
 *   surveyType: CSAT
 *   transferTarget: GENESYS
 * retry:
 *   maxRetries: 3
 *   retryBaseDelayMs: 1000
 * connectors:
 *   aibotEndpoint: https://aibot.hk.example.com
 *   genesysOrgId: hk-org-001
 * extensions:
 *   - HKRegulatoryAudit
 * </pre>
 */
public class YamlMarketConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlMarketConfigLoader.class);

    private final Yaml yaml = new Yaml();

    /**
     * Load config from a file path.
     *
     * @param path path to YAML file
     * @return loaded config, or default config if file not found
     */
    public StateMachineMarketConfig loadFromFile(Path path) {
        if (!Files.exists(path)) {
            log.warn("Config file not found: {}, using default config", path);
            return StateMachineMarketConfig.defaultConfig();
        }
        try (InputStream is = Files.newInputStream(path)) {
            return loadFromInputStream(is);
        } catch (IOException e) {
            log.error("Failed to load config from file: {}", path, e);
            return StateMachineMarketConfig.defaultConfig();
        }
    }

    /**
     * Load config from classpath.
     *
     * @param classpathResource classpath resource path
     * @return loaded config, or default config if resource not found
     */
    public StateMachineMarketConfig loadFromClasspath(String classpathResource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            if (is == null) {
                log.warn("Config resource not found on classpath: {}, using default config", classpathResource);
                return StateMachineMarketConfig.defaultConfig();
            }
            return loadFromInputStream(is);
        } catch (IOException e) {
            log.error("Failed to load config from classpath: {}", classpathResource, e);
            return StateMachineMarketConfig.defaultConfig();
        }
    }

    /**
     * Load config from an InputStream.
     *
     * @param is input stream
     * @return loaded config
     */
    @SuppressWarnings("unchecked")
    public StateMachineMarketConfig loadFromInputStream(InputStream is) {
        Map<String, Object> data = yaml.load(is);
        if (data == null) {
            log.warn("Empty YAML config, using default config");
            return StateMachineMarketConfig.defaultConfig();
        }
        return parseConfig(data);
    }

    /**
     * Parse YAML map into StateMachineMarketConfig.
     */
    @SuppressWarnings("unchecked")
    private StateMachineMarketConfig parseConfig(Map<String, Object> data) {
        StateMachineMarketConfig.StateMachineMarketConfigBuilder builder = StateMachineMarketConfig.builder();

        // Timeouts
        Map<String, Object> timeouts = (Map<String, Object>) data.get("timeouts");
        if (timeouts != null) {
            builder.customerIdleSeconds(getLong(timeouts, "customerIdleSeconds", 300));
            builder.transferTimeoutSeconds(getLong(timeouts, "transferTimeoutSeconds", 180));
            builder.endingGraceSeconds(getLong(timeouts, "endingGraceSeconds", 120));
            builder.surveyTimeoutSeconds(getLong(timeouts, "surveyTimeoutSeconds", 120));
        }

        // Features
        Map<String, Object> features = (Map<String, Object>) data.get("features");
        if (features != null) {
            builder.surveyEnabled(getBoolean(features, "surveyEnabled", false));
            builder.transferEnabled(getBoolean(features, "transferEnabled", true));
            builder.genesysEnabled(getBoolean(features, "genesysEnabled", false));
            builder.aibotEnabled(getBoolean(features, "aibotEnabled", true));
            builder.regulatoryAuditEnabled(getBoolean(features, "regulatoryAuditEnabled", false));
        }

        // Business rules
        Map<String, Object> businessRules = (Map<String, Object>) data.get("businessRules");
        if (businessRules != null) {
            builder.fallbackRoutingStrategy(getString(businessRules, "fallbackRoutingStrategy", "DROP"));
            builder.surveyType(getString(businessRules, "surveyType", "CSAT"));
            builder.transferTarget(getString(businessRules, "transferTarget", "INTERNAL_QUEUE"));
        }

        // Retry
        Map<String, Object> retry = (Map<String, Object>) data.get("retry");
        if (retry != null) {
            builder.maxRetries(getInt(retry, "maxRetries", 3));
            builder.retryBaseDelayMs(getLong(retry, "retryBaseDelayMs", 1000));
            builder.retryMaxDelayMs(getLong(retry, "retryMaxDelayMs", 30000));
        }

        // Connectors
        Map<String, Object> connectors = (Map<String, Object>) data.get("connectors");
        if (connectors != null) {
            builder.aibotEndpoint(getString(connectors, "aibotEndpoint", null));
            builder.genesysOrgId(getString(connectors, "genesysOrgId", null));
            builder.websocketEndpoint(getString(connectors, "websocketEndpoint", null));
        }

        // Extensions
        Object extensions = data.get("extensions");
        if (extensions instanceof java.util.List) {
            builder.enabledExtensions((java.util.List<String>) extensions);
        } else {
            builder.enabledExtensions(java.util.List.of());
        }

        return builder.build();
    }

    private long getLong(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }
}
