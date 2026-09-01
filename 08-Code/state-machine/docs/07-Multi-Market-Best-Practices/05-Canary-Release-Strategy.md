# 05. Canary Release Strategy

> Version: 1.0 | Last Updated: 2026-09-01
> Priority: P2 | Estimated Effort: 2-3 days

## 1. Problem Statement

### 1.1 Current Issue
When a new state machine version or market config is deployed, it goes live to 100% of traffic immediately. If there's a bug or performance regression, all users in all markets are affected simultaneously. There's no safe way to test new behavior with real production traffic.

### 1.2 Scenarios
- **New state machine version**: Added a new state or modified a transition rule — will it break existing conversations?
- **New market config**: SG now enables survey — will the survey action work correctly in production?
- **Performance change**: Optimized the transition lookup — is latency actually better under real load?
- **Connector change**: Upgraded Genesys connector — does it work with real Genesys API?

### 1.3 Goals
- New config/version starts with a small percentage of traffic
- Gradually increase percentage as confidence grows
- Automatic rollback if metrics degrade
- Same conversation always uses the same version (no mid-conversation switch)
- Canary status is visible and auditable

---

## 2. Design Overview

### 2.1 Canary Dimensions

```
┌─────────────────────────────────────────────────────────────┐
│                   Canary Release Dimensions                   │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Dimension 1: By Market                                      │
│    STAGING → SG (low risk) → HK (medium) → UK (high risk)  │
│                                                               │
│  Dimension 2: By Traffic Percentage (within a market)        │
│    5% → 20% → 50% → 100%                                    │
│                                                               │
│  Dimension 3: By Conversation (sticky)                       │
│    Conversation assigned to version at creation time          │
│    Same conversation always uses same version until closed    │
│                                                               │
│  Dimension 4: By Time Window                                  │
│    Business low-peak hours first (e.g., 2am-6am)            │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Canary Pipeline

```
                    ┌──────────────────────┐
                    │  New Config/Version   │
                    │  (merged to main)     │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │  Canary Controller     │
                    │  (manages rollout)     │
                    └──────────┬───────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
┌─────────▼─────────┐ ┌──────▼──────────┐ ┌──────▼──────────┐
│  Stage 1: STAGING  │ │  Stage 2: 5%    │ │  Stage 3: 20%   │
│  (internal test)    │ │  (1 market)      │ │  (2-3 markets)   │
│  100% internal      │ │  1 hour observe  │ │  2 hours observe │
└─────────┬──────────┘ └──────┬──────────┘ └──────┬──────────┘
          │                    │                    │
          └────────────────────┼────────────────────┘
                               │
                    ┌──────────▼───────────┐
                    │  Metrics Evaluator     │
                    │  (error rate, latency, │
                    │   state distribution)   │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │  Decision:             │
                    │  • Pass → next stage   │
                    │  • Fail → auto rollback│
                    └───────────────────────┘
```

---

## 3. Detailed Design

### 3.1 Versioned Config

```java
public record VersionedConfig(
    String versionId,           // e.g., "2026.09.01-hk-3" or Git commit hash
    String configVersion,       // semantic version "2.1.0"
    StateMachineMarketConfig config,
    Instant effectiveFrom,      // when this version becomes active
    String createdBy,           // who created this version
    String changeReason         // why this change was made
) {}
```

### 3.2 Canary Assignment (Sticky by Conversation)

```java
public class CanaryRouter {

    private final CanaryConfig canaryConfig;
    private final Map<String, String> conversationVersionMap = new ConcurrentHashMap<>();

    /**
     * Assigns a config version to a conversation.
     * Once assigned, the conversation always uses the same version (sticky).
     * Assignment is based on:
     * 1. Canary stage (which markets are in canary)
     * 2. Traffic percentage (hash of conversation ID)
     * 3. Explicit override (for testing)
     */
    public String assignVersion(String conversationId, String market) {
        // 1. Check if already assigned (sticky)
        String existing = conversationVersionMap.get(conversationId);
        if (existing != null) return existing;

        // 2. Check if market is in canary
        CanaryStage stage = canaryConfig.getStage(market);
        if (stage == CanaryStage.NONE) {
            return canaryConfig.getStableVersion();
        }

        // 3. Traffic percentage: hash conversation ID to determine canary eligibility
        int hash = Math.abs(conversationId.hashCode());
        boolean inCanary = (hash % 100) < stage.getTrafficPercentage();

        String version = inCanary ? stage.getCanaryVersion() : canaryConfig.getStableVersion();
        conversationVersionMap.put(conversationId, version);
        return version;
    }

    /**
     * Forces a conversation to use a specific version (for testing/debugging).
     */
    public void assignVersionOverride(String conversationId, String version) {
        conversationVersionMap.put(conversationId, version);
    }

    /**
     * Clears assignment for a closed conversation (prevents map growth).
     */
    public void clearAssignment(String conversationId) {
        conversationVersionMap.remove(conversationId);
    }
}
```

### 3.3 Canary Stages

```java
public enum CanaryStage {
    NONE(0, null),           // Not in canary, use stable version
    STAGING(100, "staging"), // Internal testing environment
    CANARY_5(5, "canary"),   // 5% of traffic
    CANARY_20(20, "canary"), // 20% of traffic
    CANARY_50(50, "canary"), // 50% of traffic
    FULL(100, "stable");     // 100% (promoted to stable)

    private final int trafficPercentage;
    private final String versionType;
}

public class CanaryConfig {
    private final String stableVersion;
    private final String canaryVersion;
    private final Map<String, CanaryStage> marketStages = new ConcurrentHashMap<>();

    /**
     * Example configuration:
     *   stableVersion = "2.0.0"
     *   canaryVersion = "2.1.0"
     *   marketStages:
     *     SG → CANARY_5
     *     HK → CANARY_20
     *     UK → NONE (stable only)
     *     US → NONE
     */
}
```

### 3.4 Metrics Evaluator (Auto Rollback)

```java
public class CanaryMetricsEvaluator {

    private final MetricsService metrics;
    private final CanaryThresholds thresholds;

    /**
     * Evaluates canary health by comparing canary vs stable metrics.
     * Returns PASS if canary is healthy, FAIL if rollback is needed.
     */
    public CanaryEvaluation evaluate(String canaryVersion, String stableVersion, String market) {
        CanaryMetrics canaryMetrics = metrics.getVersionMetrics(canaryVersion, market);
        CanaryMetrics stableMetrics = metrics.getVersionMetrics(stableVersion, market);

        List<String> violations = new ArrayList<>();

        // Rule 1: Error rate
        double errorRateRatio = canaryMetrics.errorRate() / stableMetrics.errorRate();
        if (errorRateRatio > thresholds.getMaxErrorRateRatio()) {
            violations.add(String.format("Error rate too high: canary=%.2f%%, stable=%.2f%% (ratio=%.2fx)",
                    canaryMetrics.errorRate() * 100, stableMetrics.errorRate() * 100, errorRateRatio));
        }

        // Rule 2: ERROR state percentage
        if (canaryMetrics.errorStatePercentage() > thresholds.getMaxErrorStatePercentage()) {
            violations.add(String.format("ERROR state percentage too high: %.2f%% (threshold=%.2f%%)",
                    canaryMetrics.errorStatePercentage() * 100,
                    thresholds.getMaxErrorStatePercentage() * 100));
        }

        // Rule 3: Latency P99
        if (canaryMetrics.p99LatencyMs() > stableMetrics.p99LatencyMs() * thresholds.getMaxLatencyMultiplier()) {
            violations.add(String.format("P99 latency too high: canary=%dms, stable=%dms",
                    canaryMetrics.p99LatencyMs(), stableMetrics.p99LatencyMs()));
        }

        // Rule 4: State transition success rate
        if (canaryMetrics.transitionSuccessRate() < thresholds.getMinTransitionSuccessRate()) {
            violations.add(String.format("Transition success rate too low: %.2f%% (threshold=%.2f%%)",
                    canaryMetrics.transitionSuccessRate() * 100,
                    thresholds.getMinTransitionSuccessRate() * 100));
        }

        // Rule 5: Minimum sample size (don't evaluate with too little data)
        if (canaryMetrics.totalEvents() < thresholds.getMinSampleSize()) {
            return CanaryEvaluation.INSUFFICIENT_DATA;
        }

        return violations.isEmpty() ? CanaryEvaluation.PASS : CanaryEvaluation.FAIL(violations);
    }
}

public record CanaryThresholds(
    double maxErrorRateRatio,           // e.g., 2.0 (canary error rate can be 2x stable)
    double maxErrorStatePercentage,     // e.g., 0.05 (5% in ERROR state)
    double maxLatencyMultiplier,        // e.g., 1.5 (P99 can be 1.5x stable)
    double minTransitionSuccessRate,    // e.g., 0.99 (99% success)
    int minSampleSize                    // e.g., 1000 events
) {
    public static CanaryThresholds defaults() {
        return new CanaryThresholds(2.0, 0.05, 1.5, 0.99, 1000);
    }
}
```

### 3.5 Canary Controller (Automated Rollout)

```java
public class CanaryController {

    private final CanaryConfig config;
    private final CanaryMetricsEvaluator evaluator;
    private final CanaryStage[] rolloutStages = {
            CanaryStage.CANARY_5,
            CanaryStage.CANARY_20,
            CanaryStage.CANARY_50,
            CanaryStage.FULL
    };
    private final Duration[] stageObservationPeriods = {
            Duration.ofHours(1),   // 5% → observe 1 hour
            Duration.ofHours(2),   // 20% → observe 2 hours
            Duration.ofHours(4),   // 50% → observe 4 hours
            Duration.ZERO          // FULL → done
    };

    private int currentStageIndex = 0;
    private Instant stageStartTime;

    /**
     * Called periodically (e.g., every 5 minutes) to check canary progress.
     * Automatically advances to next stage or rolls back.
     */
    public void checkAndAdvance() {
        if (currentStageIndex >= rolloutStages.length) return;

        CanaryStage currentStage = rolloutStages[currentStageIndex];
        Duration observationPeriod = stageObservationPeriods[currentStageIndex];

        // Check if observation period has elapsed
        if (Duration.between(stageStartTime, Instant.now()).compareTo(observationPeriod) < 0) {
            return; // Still observing
        }

        // Evaluate metrics for all markets in canary
        boolean allPass = true;
        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, CanaryStage> entry : config.getMarketStages().entrySet()) {
            if (entry.getValue() == currentStage) {
                CanaryEvaluation eval = evaluator.evaluate(
                        config.getCanaryVersion(), config.getStableVersion(), entry.getKey());
                if (eval == CanaryEvaluation.FAIL) {
                    allPass = false;
                    failures.add(entry.getKey() + ": " + eval.getViolations());
                }
            }
        }

        if (allPass) {
            // Advance to next stage
            currentStageIndex++;
            stageStartTime = Instant.now();
            if (currentStageIndex < rolloutStages.length) {
                log.info("Canary advancing to stage {} ({}% traffic)",
                        rolloutStages[currentStageIndex], rolloutStages[currentStageIndex].getTrafficPercentage());
                applyStage(rolloutStages[currentStageIndex]);
            } else {
                log.info("Canary fully promoted to stable");
                promoteToStable();
            }
        } else {
            // Auto rollback
            log.error("Canary failed, auto-rollback. Failures: {}", failures);
            rollback();
        }
    }

    private void rollback() {
        // Set all markets back to stable
        config.getMarketStages().replaceAll((k, v) -> CanaryStage.NONE);
        currentStageIndex = 0;
        // Alert on-call
        alertService.sendAlert("Canary rollback triggered", failures);
    }
}
```

### 3.6 Canary Dashboard

```
=== Canary Release Dashboard ===
Canary version: 2.1.0 (commit abc1234)
Stable version: 2.0.0 (commit def5678)
Current stage: CANARY_20 (20% traffic)
Stage started: 2026-09-01 14:00:00
Next evaluation: 2026-09-01 16:00:00

─── Market Status ───
  Market | Stage    | Traffic | Error Rate | ERROR State | P99 Latency | Status
  -------|----------|---------|------------|-------------|--------------|--------
  SG     | CANARY_5 | 5%      | 0.3%       | 0.1%        | 12ms         | PASS
  HK     | CANARY_20| 20%     | 0.5%       | 0.2%        | 15ms         | PASS
  UK     | NONE     | 0%      | 0.4%       | 0.1%        | 14ms         | stable
  US     | NONE     | 0%      | 0.3%       | 0.1%        | 13ms         | stable

─── Canary vs Stable Comparison ───
  Metric          | Canary | Stable | Ratio | Threshold | Status
  ----------------|--------|--------|-------|-----------|--------
  Error rate      | 0.4%   | 0.35%  | 1.14x | < 2.0x    | PASS
  ERROR state %   | 0.15%  | 0.1%   | 1.5x  | < 5%      | PASS
  P99 latency     | 14ms   | 13ms   | 1.08x | < 1.5x    | PASS
  Transition succ | 99.8%  | 99.9%  | -     | > 99%     | PASS

─── Actions ───
  [Advance to next stage]  [Pause canary]  [Rollback]  [Promote to stable]
```

---

## 4. Implementation Roadmap

### Phase 1: Versioned Config & Sticky Routing (1 day)
- [ ] Implement `VersionedConfig` record
- [ ] Implement `CanaryRouter` with sticky conversation assignment
- [ ] Implement conversation-to-version map with TTL
- [ ] Unit tests for assignment, stickiness, hash distribution

### Phase 2: Canary Stages & Config (0.5 day)
- [ ] Implement `CanaryStage` enum and `CanaryConfig`
- [ ] Implement per-market stage configuration
- [ ] Implement config hot-reload for canary stages
- [ ] Unit tests for stage transitions

### Phase 3: Metrics Evaluator (1 day)
- [ ] Implement `CanaryMetrics` data structure
- [ ] Implement `CanaryMetricsEvaluator` with 5 rules
- [ ] Implement `CanaryThresholds` with defaults
- [ ] Unit tests for pass/fail/insufficient data scenarios

### Phase 4: Canary Controller & Dashboard (1 day)
- [ ] Implement `CanaryController` with auto-advance and auto-rollback
- [ ] Implement canary status API
- [ ] Implement canary dashboard (HTML/JSON)
- [ ] Implement manual actions (advance/pause/rollback/promote)
- [ ] Integration tests for full canary lifecycle

---

## 5. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Canary version has a subtle bug that metrics don't catch | Medium | High | Manual review before canary start; minimum observation period; canary starts in low-traffic market |
| Sticky routing causes map memory growth | Medium | Low | TTL on entries; clear on conversation close; max size with LRU eviction |
| Hash distribution is uneven (some market over/under-represented) | Low | Medium | Use consistent hashing; verify distribution in staging; monitor actual percentage |
| Auto-rollback triggers on transient metric spike | Medium | Medium | Minimum observation period; minimum sample size; require sustained violation (3 consecutive checks) |
| Canary and stable configs share state (cross-contamination) | Low | High | Config is immutable snapshot; state repository is shared but state values are version-agnostic |
| In-flight conversation switches version mid-flow | Low | High | Sticky routing: version assigned at conversation creation, never changes for that conversation |
| Canary dashboard shows stale data | Low | Low | Real-time metrics; last-updated timestamp; alert on stale metrics |

---

## 6. Success Criteria

- [ ] New config can be deployed to 5% of one market with one command
- [ ] Canary auto-advances through stages (5%→20%→50%→100%) if metrics pass
- [ ] Canary auto-rolls back within 5 minutes if error rate exceeds threshold
- [ ] Same conversation always uses the same config version (sticky)
- [ ] Canary status is visible via dashboard (market, stage, metrics, comparison)
- [ ] Canary rollback takes < 2 minutes (config change propagates)
- [ ] Minimum sample size prevents premature evaluation with insufficient data
- [ ] Canary never affects markets not explicitly in canary stage
