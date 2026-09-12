# 05. 金丝雀发布策略

> 版本：1.0 | 最后更新：2026-09-01
> 优先级：P2 | 预估工作量：2-3 天

## 1. 问题陈述

### 1.1 当前问题

当新的状态机版本或市场配置部署时，它立即对 100% 的流量生效。如果有 bug 或性能回归，所有市场中的所有用户同时受到影响。没有安全的方法用真实生产流量测试新行为。

### 1.2 场景

- **新状态机版本**：添加了新状态或修改了迁移规则 — 它会破坏现有会话吗？
- **新市场配置**：SG 现在启用满意度调查 — 满意度调查动作在生产环境中能正常工作吗？
- **性能变更**：优化了迁移查找 — 在真实负载下延迟真的更好吗？
- **连接器变更**：升级了 Genesys 连接器 — 它能与真实 Genesys API 一起工作吗？

### 1.3 目标

- 新配置/版本从一小部分流量开始
- 随着信心增长逐渐增加百分比
- 如果指标降级则自动回滚
- 同一会话始终使用同一版本（不在会话中途切换）
- 金丝雀状态可见且可审计

---

## 2. 设计概述

### 2.1 金丝雀维度

```
┌─────────────────────────────────────────────────────────────┐
│                   金丝雀发布维度                   │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  维度 1: 按市场                                      │
│    STAGING → SG（低风险）→ HK（中）→ UK（高风险）  │
│                                                               │
│  维度 2: 按流量百分比（在市场内）        │
│    5% → 20% → 50% → 100%                                    │
│                                                               │
│  维度 3: 按会话（粘性）                       │
│    会话在创建时分配给版本          │
│    同一会话在关闭前始终使用同一版本    │
│                                                               │
│  维度 4: 按时间窗口                                  │
│    先在业务低峰时段（例如凌晨 2 点-6 点）            │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 金丝雀管道

```
                    ┌──────────────────────┐
                    │  新配置/版本   │
                    │  （合并到 main）     │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │  金丝雀控制器     │
                    │  （管理推出）     │
                    └──────────┬───────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
┌─────────▼─────────┐ ┌──────▼──────────┐ ┌──────▼──────────┐
│  阶段 1: STAGING  │ │  阶段 2: 5%    │ │  阶段 3: 20%   │
│  （内部测试）    │ │  （1 个市场）      │ │  （2-3 个市场）   │
│  100% 内部      │ │  1 小时观察  │ │  2 小时观察 │
└─────────┬──────────┘ └──────┬──────────┘ └──────┬──────────┘
          │                    │                    │
          └────────────────────┼────────────────────┘
                               │
                    ┌──────────▼───────────┐
                    │  指标评估器     │
                    │  （错误率、延迟、 │
                    │   状态分布）   │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │  决策:             │
                    │  • 通过 → 下一阶段   │
                    │  • 失败 → 自动回滚│
                    └───────────────────────┘
```

---

## 3. 详细设计

### 3.1 版本化配置

```java
public record VersionedConfig(
    String versionId,           // 例如 "2026.09.01-hk-3" 或 Git 提交哈希
    String configVersion,       // 语义版本 "2.1.0"
    StateMachineMarketConfig config,
    Instant effectiveFrom,      // 此版本何时生效
    String createdBy,           // 谁创建了此版本
    String changeReason         // 为什么进行此变更
) {}
```

### 3.2 金丝雀分配（按会话粘性）

```java
public class CanaryRouter {

    private final CanaryConfig canaryConfig;
    private final Map<String, String> conversationVersionMap = new ConcurrentHashMap<>();

    /**
     * 为会话分配配置版本。
     * 一旦分配，会话始终使用同一版本（粘性）。
     * 分配基于：
     * 1. 金丝雀阶段（哪些市场处于金丝雀中）
     * 2. 流量百分比（会话 ID 的哈希）
     * 3. 显式覆盖（用于测试）
     */
    public String assignVersion(String conversationId, String market) {
        // 1. 检查是否已分配（粘性）
        String existing = conversationVersionMap.get(conversationId);
        if (existing != null) return existing;

        // 2. 检查市场是否处于金丝雀中
        CanaryStage stage = canaryConfig.getStage(market);
        if (stage == CanaryStage.NONE) {
            return canaryConfig.getStableVersion();
        }

        // 3. 流量百分比：哈希会话 ID 以确定金丝雀资格
        int hash = Math.abs(conversationId.hashCode());
        boolean inCanary = (hash % 100) < stage.getTrafficPercentage();

        String version = inCanary ? stage.getCanaryVersion() : canaryConfig.getStableVersion();
        conversationVersionMap.put(conversationId, version);
        return version;
    }

    /**
     * 强制会话使用特定版本（用于测试/调试）。
     */
    public void assignVersionOverride(String conversationId, String version) {
        conversationVersionMap.put(conversationId, version);
    }

    /**
     * 清除已关闭会话的分配（防止 map 增长）。
     */
    public void clearAssignment(String conversationId) {
        conversationVersionMap.remove(conversationId);
    }
}
```

### 3.3 金丝雀阶段

```java
public enum CanaryStage {
    NONE(0, null),           // 不在金丝雀中，使用稳定版本
    STAGING(100, "staging"), // 内部测试环境
    CANARY_5(5, "canary"),   // 5% 流量
    CANARY_20(20, "canary"), // 20% 流量
    CANARY_50(50, "canary"), // 50% 流量
    FULL(100, "stable");     // 100%（提升为稳定）

    private final int trafficPercentage;
    private final String versionType;
}

public class CanaryConfig {
    private final String stableVersion;
    private final String canaryVersion;
    private final Map<String, CanaryStage> marketStages = new ConcurrentHashMap<>();

    /**
     * 示例配置:
     *   stableVersion = "2.0.0"
     *   canaryVersion = "2.1.0"
     *   marketStages:
     *     SG → CANARY_5
     *     HK → CANARY_20
     *     UK → NONE（仅稳定）
     *     US → NONE
     */
}
```

### 3.4 指标评估器（自动回滚）

```java
public class CanaryMetricsEvaluator {

    private final MetricsService metrics;
    private final CanaryThresholds thresholds;

    /**
     * 通过比较金丝雀与稳定指标来评估金丝雀健康状况。
     * 如果金丝雀健康则返回 PASS，如果需要回滚则返回 FAIL。
     */
    public CanaryEvaluation evaluate(String canaryVersion, String stableVersion, String market) {
        CanaryMetrics canaryMetrics = metrics.getVersionMetrics(canaryVersion, market);
        CanaryMetrics stableMetrics = metrics.getVersionMetrics(stableVersion, market);

        List<String> violations = new ArrayList<>();

        // 规则 1: 错误率
        double errorRateRatio = canaryMetrics.errorRate() / stableMetrics.errorRate();
        if (errorRateRatio > thresholds.getMaxErrorRateRatio()) {
            violations.add(String.format("Error rate too high: canary=%.2f%%, stable=%.2f%% (ratio=%.2fx)",
                    canaryMetrics.errorRate() * 100, stableMetrics.errorRate() * 100, errorRateRatio));
        }

        // 规则 2: ERROR 状态百分比
        if (canaryMetrics.errorStatePercentage() > thresholds.getMaxErrorStatePercentage()) {
            violations.add(String.format("ERROR state percentage too high: %.2f%% (threshold=%.2f%%)",
                    canaryMetrics.errorStatePercentage() * 100,
                    thresholds.getMaxErrorStatePercentage() * 100));
        }

        // 规则 3: 延迟 P99
        if (canaryMetrics.p99LatencyMs() > stableMetrics.p99LatencyMs() * thresholds.getMaxLatencyMultiplier()) {
            violations.add(String.format("P99 latency too high: canary=%dms, stable=%dms",
                    canaryMetrics.p99LatencyMs(), stableMetrics.p99LatencyMs()));
        }

        // 规则 4: 状态迁移成功率
        if (canaryMetrics.transitionSuccessRate() < thresholds.getMinTransitionSuccessRate()) {
            violations.add(String.format("Transition success rate too low: %.2f%% (threshold=%.2f%%)",
                    canaryMetrics.transitionSuccessRate() * 100,
                    thresholds.getMinTransitionSuccessRate() * 100));
        }

        // 规则 5: 最小样本量（不要用太少数据评估）
        if (canaryMetrics.totalEvents() < thresholds.getMinSampleSize()) {
            return CanaryEvaluation.INSUFFICIENT_DATA;
        }

        return violations.isEmpty() ? CanaryEvaluation.PASS : CanaryEvaluation.FAIL(violations);
    }
}

public record CanaryThresholds(
    double maxErrorRateRatio,           // 例如 2.0（金丝雀错误率可以是稳定的 2 倍）
    double maxErrorStatePercentage,     // 例如 0.05（5% 处于 ERROR 状态）
    double maxLatencyMultiplier,        // 例如 1.5（P99 可以是稳定的 1.5 倍）
    double minTransitionSuccessRate,    // 例如 0.99（99% 成功）
    int minSampleSize                    // 例如 1000 事件
) {
    public static CanaryThresholds defaults() {
        return new CanaryThresholds(2.0, 0.05, 1.5, 0.99, 1000);
    }
}
```

### 3.5 金丝雀控制器（自动推出）

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
            Duration.ofHours(1),   // 5% → 观察 1 小时
            Duration.ofHours(2),   // 20% → 观察 2 小时
            Duration.ofHours(4),   // 50% → 观察 4 小时
            Duration.ZERO          // FULL → 完成
    };

    private int currentStageIndex = 0;
    private Instant stageStartTime;

    /**
     * 定期调用（例如每 5 分钟）以检查金丝雀进度。
     * 自动推进到下一阶段或回滚。
     */
    public void checkAndAdvance() {
        if (currentStageIndex >= rolloutStages.length) return;

        CanaryStage currentStage = rolloutStages[currentStageIndex];
        Duration observationPeriod = stageObservationPeriods[currentStageIndex];

        // 检查观察期是否已过
        if (Duration.between(stageStartTime, Instant.now()).compareTo(observationPeriod) < 0) {
            return; // 仍在观察
        }

        // 评估金丝雀中所有市场的指标
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
            // 推进到下一阶段
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
            // 自动回滚
            log.error("Canary failed, auto-rollback. Failures: {}", failures);
            rollback();
        }
    }

    private void rollback() {
        // 将所有市场设置回稳定
        config.getMarketStages().replaceAll((k, v) -> CanaryStage.NONE);
        currentStageIndex = 0;
        // 告警值班人员
        alertService.sendAlert("Canary rollback triggered", failures);
    }
}
```

### 3.6 金丝雀仪表板

```
=== 金丝雀发布仪表板 ===
金丝雀版本: 2.1.0 (commit abc1234)
稳定版本: 2.0.0 (commit def5678)
当前阶段: CANARY_20 (20% 流量)
阶段开始: 2026-09-01 14:00:00
下次评估: 2026-09-01 16:00:00

─── 市场状态 ───
  市场 | 阶段    | 流量 | 错误率 | ERROR 状态 | P99 延迟 | 状态
  -------|----------|---------|------------|-------------|--------------|--------
  SG     | CANARY_5 | 5%      | 0.3%       | 0.1%        | 12ms         | PASS
  HK     | CANARY_20| 20%     | 0.5%       | 0.2%        | 15ms         | PASS
  UK     | NONE     | 0%      | 0.4%       | 0.1%        | 14ms         | stable
  US     | NONE     | 0%      | 0.3%       | 0.1%        | 13ms         | stable

─── 金丝雀 vs 稳定比较 ───
  指标          | 金丝雀 | 稳定 | 比率 | 阈值 | 状态
  ----------------|--------|--------|-------|-----------|--------
  错误率      | 0.4%   | 0.35%  | 1.14x | < 2.0x    | PASS
  ERROR 状态 %   | 0.15%  | 0.1%   | 1.5x  | < 5%      | PASS
  P99 延迟     | 14ms   | 13ms   | 1.08x | < 1.5x    | PASS
  迁移成功 | 99.8%  | 99.9%  | -     | > 99%     | PASS

─── 操作 ───
  [推进到下一阶段]  [暂停金丝雀]  [回滚]  [提升为稳定]
```

---

## 4. 实施路线图

### 阶段 1：版本化配置与粘性路由（1 天）
- [ ] 实现 `VersionedConfig` record
- [ ] 实现带粘性会话分配的 `CanaryRouter`
- [ ] 实现带 TTL 的会话到版本映射
- [ ] 分配、粘性、哈希分布的单元测试

### 阶段 2：金丝雀阶段与配置（0.5 天）
- [ ] 实现 `CanaryStage` 枚举和 `CanaryConfig`
- [ ] 实现每市场阶段配置
- [ ] 实现金丝雀阶段的配置热重载
- [ ] 阶段转换的单元测试

### 阶段 3：指标评估器（1 天）
- [ ] 实现 `CanaryMetrics` 数据结构
- [ ] 实现带 5 条规则的 `CanaryMetricsEvaluator`
- [ ] 实现带默认值的 `CanaryThresholds`
- [ ] 通过/失败/数据不足场景的单元测试

### 阶段 4：金丝雀控制器与仪表板（1 天）
- [ ] 实现带自动推进和自动回滚的 `CanaryController`
- [ ] 实现金丝雀状态 API
- [ ] 实现金丝雀仪表板（HTML/JSON）
- [ ] 实现手动操作（推进/暂停/回滚/提升）
- [ ] 完整金丝雀生命周期的集成测试

---

## 5. 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|-----------|--------|------------|
| 金丝雀版本有指标未捕获的微妙 bug | 中 | 高 | 金丝雀开始前人工评审；最小观察期；金丝雀从低流量市场开始 |
| 粘性路由导致 map 内存增长 | 中 | 低 | 条目上的 TTL；会话关闭时清除；带 LRU 淘汰的最大大小 |
| 哈希分布不均匀（某些市场过度/不足代表） | 低 | 中 | 使用一致性哈希；在 staging 中验证分布；监控实际百分比 |
| 自动回滚在瞬时指标飙升上触发 | 中 | 中 | 最小观察期；最小样本量；要求持续违规（连续 3 次检查） |
| 金丝雀和稳定配置共享状态（交叉污染） | 低 | 高 | 配置是不可变快照；状态仓库共享但状态值与版本无关 |
| 进行中的会话在流程中途切换版本 | 低 | 高 | 粘性路由：版本在会话创建时分配，该会话永不变更 |
| 金丝雀仪表板显示过时数据 | 低 | 低 | 实时指标；最后更新时间戳；过时指标告警 |

---

## 6. 成功标准

- [ ] 新配置可以用一个命令部署到一个市场的 5%
- [ ] 如果指标通过，金丝雀自动推进阶段（5%→20%→50%→100%）
- [ ] 如果错误率超过阈值，金丝雀在 5 分钟内自动回滚
- [ ] 同一会话始终使用同一配置版本（粘性）
- [ ] 金丝雀状态通过仪表板可见（市场、阶段、指标、比较）
- [ ] 金丝雀回滚需要 < 2 分钟（配置变更传播）
- [ ] 最小样本量防止用不足数据过早评估
- [ ] 金丝雀永远不会影响未明确处于金丝雀阶段的市场
