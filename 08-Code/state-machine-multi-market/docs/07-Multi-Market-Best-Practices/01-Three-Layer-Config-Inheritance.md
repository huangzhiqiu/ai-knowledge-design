# 01. Three-Layer Config Inheritance

> Version: 1.0 | Last Updated: 2026-09-01
> Priority: P0 | Estimated Effort: 2-3 days

## 1. Problem Statement

### 1.1 Current Issue
Each market has an independent `StateMachineMarketConfig` with no inheritance relationship. When a global default changes (e.g., idle timeout from 300s to 600s), every market config must be manually updated. Markets that are missed silently drift from the intended baseline.

### 1.2 Symptoms
- **Config duplication**: 80% of config values are identical across markets but repeated in each file
- **Drift risk**: Global changes require N manual updates; missed markets behave unexpectedly
- **No audit trail**: Hard to answer "why does HK have this value? Is it intentional or inherited?"
- **Change impact unknown**: Modifying base config requires manually checking every market

### 1.3 Goals
- Define config once at the appropriate level (base/regional/market)
- Allow lower levels to override higher levels explicitly
- Every final config value is traceable to its source (base / regional / market override)
- Global changes propagate automatically unless explicitly overridden

---

## 2. Design Overview

### 2.1 Three-Layer Model

```
┌─────────────────────────────────────────────────────┐
│  Layer 1: base-profile.yaml                          │
│  Global defaults for all markets                      │
│  (timeouts, feature toggles, fallback strategies)    │
└──────────────────────┬──────────────────────────────┘
                       │ inherits
┌──────────────────────▼──────────────────────────────┐
│  Layer 2: regional-profiles/                         │
│    apac.yaml  (HK, SG, JP, AU)                      │
│    emea.yaml  (UK, DE, FR)                           │
│    amer.yaml  (US, CA, BR)                           │
│  Regional overrides: regulatory, timezone, language  │
└──────────────────────┬──────────────────────────────┘
                       │ inherits
┌──────────────────────▼──────────────────────────────┐
│  Layer 3: market-profiles/                           │
│    hk.yaml  sg.yaml  uk.yaml  us.yaml  ...          │
│  Market-specific overrides: thresholds, connectors   │
└─────────────────────────────────────────────────────┘
```

### 2.2 Merge Priority
```
Final value = market override > regional override > base default
```

- If a key exists in all three layers, the **market** value wins
- If a key exists in base and regional but not market, the **regional** value wins
- If a key only exists in base, the **base** value is used

### 2.3 Merge Rules by Type

| Type | Merge Strategy | Example |
|------|---------------|---------|
| Scalar (string, number, boolean) | Direct override | `customerIdleSeconds: 300` → `600` |
| Map / Object | Deep merge (key-level override) | `connectors: {aibot: {...}}` merges key by key |
| List (default) | Replace | `enabledExtensions: [X]` → `[Y]` (not `[X,Y]`) |
| List (append mode) | Concatenate | `enabledExtensions: [X]` + `[Y]` → `[X,Y]` |
| Null | Explicit reset to null (override with "not set") | `genesysOrgId: null` removes inherited value |

---

## 3. Detailed Design

### 3.1 Config File Format

#### base-profile.yaml
```yaml
# Global defaults — all markets inherit these values
profile: base
version: "2.0"

timeouts:
  customerIdleSeconds: 300
  transferTimeoutSeconds: 180
  endingGraceSeconds: 120
  surveyTimeoutSeconds: 300

features:
  surveyEnabled: false
  transferEnabled: true
  genesysEnabled: false
  aibotEnabled: true
  regulatoryAuditEnabled: false

businessRules:
  fallbackRoutingStrategy: DROP
  surveyType: CSAT
  transferTarget: AIBOT
  maxTransferRetries: 1

connectors:
  aibotEndpoint: https://aibot.default.example.com
  websocketEndpoint: wss://ws.default.example.com

extensions: []
```

#### regional-profiles/apac.yaml
```yaml
profile: apac
inherits: base
version: "2.0"

# APAC regulatory: audit required for financial markets
features:
  regulatoryAuditEnabled: true

# APAC generally shorter timeouts due to higher traffic
timeouts:
  customerIdleSeconds: 240

businessRules:
  fallbackRoutingStrategy: REQUEUE
```

#### market-profiles/hk.yaml
```yaml
market: HK
inherits: [base, apac]    # inherit from base first, then apac overrides
version: "2.0"

# HK-specific: Genesys integration, CSAT survey
features:
  genesysEnabled: true
  surveyEnabled: true

businessRules:
  transferTarget: GENESYS
  maxTransferRetries: 3

connectors:
  aibotEndpoint: https://aibot.hk.example.com
  genesysOrgId: hk-org-001

extensions:
  - HKRegulatoryAudit
```

### 3.2 Config Merge Engine

```java
public class ConfigMergeEngine {

    /**
     * Merges config layers from base → regional → market.
     * Returns a fully resolved, immutable StateMachineMarketConfig.
     */
    public MergedMarketConfig merge(String market) {
        MarketProfile marketProfile = loader.loadMarket(market);
        List<String> inheritanceChain = resolveInheritanceChain(marketProfile);

        // Start with empty config, apply each layer in order (base first, market last)
        Map<String, Object> merged = new HashMap<>();
        for (String layer : inheritanceChain) {
            Map<String, Object> layerConfig = loader.loadLayer(layer);
            deepMerge(merged, layerConfig);
        }

        // Validate and convert to typed config
        return validateAndConvert(merged, market);
    }

    /**
     * Resolves the inheritance chain in order of application.
     * Example: HK inherits [base, apac] → ["base", "apac", "hk"]
     */
    private List<String> resolveInheritanceChain(MarketProfile profile) {
        List<String> chain = new ArrayList<>();
        collectInherited(profile.inherits(), chain, new HashSet<>());
        chain.add(profile.name());
        return chain;
    }

    /**
     * Deep merge: source overrides target for scalars,
     * recursively merges maps, replaces lists (unless append mode).
     */
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                target.remove(key);  // explicit null = reset
            } else if (value instanceof Map && target.get(key) instanceof Map) {
                deepMerge((Map<String, Object>) target.get(key), (Map<String, Object>) value);
            } else {
                target.put(key, value);  // scalar or list = replace
            }
        }
    }
}
```

### 3.3 Merged Config with Source Tracking

Every value in the final config should be traceable to its source layer:

```java
public record MergedMarketConfig(
    String market,
    List<String> inheritanceChain,    // ["base", "apac", "hk"]
    ConfigValue<Long> customerIdleSeconds,
    ConfigValue<Boolean> surveyEnabled,
    ConfigValue<String> transferTarget,
    // ... all other fields
) {
    // ConfigValue wraps the value with its source layer
    public record ConfigValue<T>(T value, String sourceLayer, boolean isOverride) {}
}

// Usage
MergedMarketConfig config = mergeEngine.merge("HK");
config.customerIdleSeconds().value();      // 240
config.customerIdleSeconds().sourceLayer(); // "apac" (inherited, not HK override)
config.surveyEnabled().value();             // true
config.surveyEnabled().sourceLayer();       // "hk" (explicitly overridden)
```

### 3.4 Config Snapshot Export

For auditing and debugging, export the final merged config with source annotations:

```yaml
# Exported snapshot for HK (auto-generated)
market: HK
inheritanceChain: [base, apac, hk]

timeouts:
  customerIdleSeconds:
    value: 240
    source: apac           # inherited from apac, not overridden by HK
  transferTimeoutSeconds:
    value: 180
    source: base           # inherited from base
  endingGraceSeconds:
    value: 120
    source: base

features:
  surveyEnabled:
    value: true
    source: hk             # explicitly overridden by HK
    overrides: base(false) # shows what it overrides
  genesysEnabled:
    value: true
    source: hk
    overrides: base(false)
```

---

## 4. Change Impact Analysis

### 4.1 Impact Analysis Tool

When a base or regional config changes, automatically determine affected markets:

```java
public class ConfigImpactAnalyzer {

    /**
     * Given a changed config key and its new value,
     * returns the list of markets whose final config would change.
     */
    public ImpactReport analyzeImpact(String changedLayer, String key, Object newValue) {
        List<String> allMarkets = loader.listAllMarkets();
        List<MarketImpact> affected = new ArrayList<>();

        for (String market : allMarkets) {
            MergedMarketConfig before = mergeEngine.merge(market);
            // Simulate the change
            MergedMarketConfig after = simulateChange(before, changedLayer, key, newValue);

            if (!before.equals(after)) {
                affected.add(new MarketImpact(market, before, after, findDifference(before, after)));
            }
        }
        return new ImpactReport(changedLayer, key, newValue, affected);
    }
}
```

### 4.2 Impact Report Example

```
=== Config Change Impact Report ===
Changed: base.features.surveyEnabled: false → true
Affected markets: 3 of 5

  Market | Before | After  | Source Change
  -------|--------|--------|--------------
  SG     | false  | true   | base (was inherited, now changes)
  UK     | false  | true   | base (was inherited, now changes)
  US     | false  | true   | base (was inherited, now changes)

Not affected:
  HK     | true   | true   | already overrides base (explicitly true)
  JP     | false  | false  | explicitly overrides base to false
```

---

## 5. Implementation Roadmap

### Phase 1: Core Merge Engine (1 day)
- [ ] Define `MarketProfile` record with `inherits` field
- [ ] Implement `ConfigMergeEngine` with deep merge logic
- [ ] Implement inheritance chain resolution (with cycle detection)
- [ ] Unit tests for merge scenarios (override, deep merge, null reset, list replace)

### Phase 2: Source Tracking & Snapshot (0.5 day)
- [ ] Implement `ConfigValue<T>` wrapper with source layer tracking
- [ ] Implement config snapshot export (YAML with source annotations)
- [ ] Unit tests for source tracking accuracy

### Phase 3: Impact Analysis Tool (0.5 day)
- [ ] Implement `ConfigImpactAnalyzer`
- [ ] Implement diff report generation
- [ ] Integrate with CI: run impact analysis on every config PR

### Phase 4: Migration (1 day)
- [ ] Convert existing market configs to three-layer format
- [ ] Extract common values into base-profile.yaml
- [ ] Group markets into regional profiles (apac/emea/amer)
- [ ] Verify merged configs match current behavior (regression test)

---

## 6. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Inheritance cycle (A inherits B, B inherits A) | Low | High | Cycle detection in chain resolution, fail fast on startup |
| Deep merge produces unexpected results for nested maps | Medium | Medium | Clear merge rules documented, snapshot export for verification |
| Market override accidentally removes intended base value | Medium | High | Null = explicit reset is documented; impact analysis shows removed values |
| List merge confusion (replace vs append) | Medium | Medium | Default = replace; append mode requires explicit `__append: true` marker |
| Migration breaks existing behavior | Medium | High | Regression test: merged config must equal current config for all markets |
| Performance: merge on every request | Low | Low | Merge once at startup / config refresh, cache immutable result |

---

## 7. Success Criteria

- [ ] Every market config value is traceable to its source layer (base/regional/market)
- [ ] Changing a base value automatically propagates to all non-overriding markets
- [ ] Config PRs automatically include an impact analysis report
- [ ] No market config contains values that are identical to base (redundant overrides flagged)
- [ ] Inheritance cycles are detected and rejected at startup
- [ ] Migration: all existing markets' merged configs match current behavior exactly
