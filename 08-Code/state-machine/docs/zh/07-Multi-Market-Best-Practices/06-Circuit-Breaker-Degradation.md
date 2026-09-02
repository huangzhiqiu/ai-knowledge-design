# 06. 熔断器与降级策略

> 版本：1.0 | 最后更新：2026-09-01
> 优先级：P2 | 预估工作量：2-3 天

## 1. 问题陈述

### 1.1 当前问题

当状态机或其依赖项（AIBot、Genesys、WebSocket、数据库）遇到问题时，系统没有结构化的方法来优雅降级。它要么：
- 继续处理并超时（糟糕的用户体验，资源耗尽）
- 完全崩溃（级联故障）
- 返回没有上下文的通用错误

没有"系统降级但仍可运行"或"这个市场宕机但其他市场正常"的概念。

### 1.2 场景

- **AIBot API 慢**：AI 响应需要 30 秒而不是 3 秒，线程阻塞，池耗尽
- **Genesys 中断**：转接请求全部失败，系统继续重试，浪费资源
- **数据库连接池耗尽**：状态持久化失败，会话卡住
- **市场特定问题**：HK 的 Genesys 组织宕机，但 SG/UK 正常工作
- **全局流量高峰**：系统处于 90% 容量，需要优雅地卸载负载

### 1.3 目标

- 及早检测故障并快速失败（不要等待超时）
- 防止级联故障（一个组件的故障不会使整个系统崩溃）
- 提供优雅降级（减少功能而不是崩溃）
- 每市场隔离（一个市场的降级不影响其他市场）
- 自动恢复（当依赖项恢复时，熔断器自动关闭）

---

## 2. 设计概述

### 2.1 降级级别

```
┌─────────────────────────────────────────────────────────────────┐
│                    降级级别 (L0 → L4)                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  L0 正常                                                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 完整功能: 所有事件、所有动作、所有连接器│  │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                    │
│                              ▼ (依赖慢 / 错误率 ↑)  │
│  L1 弱降级                                               │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 跳过非关键动作: 审计日志、通知、  │    │
│  │ 指标收集。核心迁移和连接器工作。  │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                    │
│                              ▼ (错误率 ↑ 更多 / 延迟 ↑)   │
│  L2 中等降级                                             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 仅关键事件: CUSTOMER_CONNECT、CUSTOMER_CLOSE、  │    │
│  │ SURVEY_COMPLETE、SYS_*。非关键事件排队/丢弃。│  │
│  │ 连接器: 使用缓存/回退响应。                  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                    │
│                              ▼ (依赖宕机 / 资源关键)│
│  L3 强降级                                             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 拒绝新会话。现有会话: 仅    │    │
│  │ 允许 CUSTOMER_CLOSE。所有其他事件用 503 拒绝。│  │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                    │
│                              ▼ (状态机本身故障)      │
│  L4 熔断器打开                                                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 所有事件用 503 快速失败。无状态机执行。 │  │
│  │ 返回静态错误响应。                             │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 每依赖熔断器

```
                    ┌──────────────────────────────┐
                    │     事件处理          │
                    └──────────┬───────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
┌─────────▼─────────┐ ┌──────▼──────────┐ ┌──────▼──────────┐
│ AIBot 熔断器      │ │ Genesys 熔断器  │ │ DB 熔断器       │
│ （每市场）       │ │ （每市场）     │ │ （全局）         │
│                    │ │                   │ │                  │
│ 状态:            │ │ 状态:           │ │ 状态:          │
│  • CLOSED（正常） │ │  • CLOSED         │ │  • CLOSED        │
│  • OPEN（快速失败）│ │  • OPEN           │ │  • OPEN          │
│  • HALF_OPEN（测试）│ │  • HALF_OPEN      │ │  • HALF_OPEN     │
└─────────┬──────────┘ └──────┬──────────┘ └──────┬──────────┘
          │                    │                    │
          └────────────────────┼────────────────────┘
                               │
                    ┌──────────▼───────────────────┐
                    │     降级控制器     │
                    │  （组合熔断器状态 →    │
                    │   整体降级级别）    │
                    └────────────────────────────────┘
```

---

## 3. 详细设计

### 3.1 熔断器实现

```java
public class MarketCircuitBreaker {

    private final String market;
    private final String dependency;  // "aibot", "genesys", "db", "websocket"
    private final CircuitBreakerConfig config;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private volatile long openTime;

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public MarketCircuitBreaker(String market, String dependency, CircuitBreakerConfig config) {
        this.market = market;
        this.dependency = dependency;
        this.config = config;
    }

    /**
     * 尝试获取执行权限。
     * 如果允许执行则返回 true，如果熔断器打开则返回 false。
     */
    public boolean tryAcquire() {
        State current = state.get();

        if (current == State.CLOSED) return true;

        if (current == State.OPEN) {
            // 检查冷却期是否已过
            if (System.currentTimeMillis() - openTime >= config.getOpenDurationMs()) {
                // 转换到 HALF_OPEN（允许一个探测请求）
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("Circuit breaker {}/{} transitioning OPEN → HALF_OPEN", market, dependency);
                    return true;  // 允许探测
                }
            }
            return false;  // 仍打开，快速失败
        }

        // HALF_OPEN: 仅允许有限数量的探测请求
        return successCount.get() < config.getHalfOpenMaxProbes();
    }

    /**
     * 记录成功执行。
     */
    public void recordSuccess() {
        State current = state.get();

        if (current == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= config.getHalfOpenSuccessThreshold()) {
                // 关闭熔断器
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    log.info("Circuit breaker {}/{} recovered, HALF_OPEN → CLOSED", market, dependency);
                    resetCounters();
                }
            }
        } else {
            // CLOSED: 成功时重置失败计数（滑动窗口）
            failureCount.set(0);
        }
    }

    /**
     * 记录失败执行。
     */
    public void recordFailure(Exception e) {
        State current = state.get();

        if (current == State.HALF_OPEN) {
            // HALF_OPEN 中的任何失败 → 回到 OPEN
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openTime = System.currentTimeMillis();
                log.warn("Circuit breaker {}/{} probe failed, HALF_OPEN → OPEN: {}",
                        market, dependency, e.getMessage());
            }
            return;
        }

        if (current == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            if (failures >= config.getFailureThreshold()) {
                // 打开熔断器
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    openTime = System.currentTimeMillis();
                    log.warn("Circuit breaker {}/{} opened after {} failures: {}",
                            market, dependency, failures, e.getMessage());
                }
            }
        }
    }

    public State getState() { return state.get(); }

    private void resetCounters() {
        failureCount.set(0);
        successCount.set(0);
    }
}
```

### 3.2 熔断器配置

```java
public record CircuitBreakerConfig(
    int failureThreshold,           // 例如 5（5 次失败 → 打开）
    long openDurationMs,             // 例如 30000（打开 30 秒）
    int halfOpenMaxProbes,           // 例如 3（最多 3 个探测请求）
    int halfOpenSuccessThreshold,    // 例如 2（2 次成功 → 关闭）
    long slidingWindowMs              // 例如 60000（失败计数的 1 分钟窗口）
) {
    public static CircuitBreakerConfig defaults() {
        return new CircuitBreakerConfig(5, 30000, 3, 2, 60000);
    }

    public static CircuitBreakerConfig aggressive() {
        return new CircuitBreakerConfig(3, 60000, 2, 1, 30000);
    }

    public static CircuitBreakerConfig lenient() {
        return new CircuitBreakerConfig(10, 15000, 5, 3, 120000);
    }
}
```

### 3.3 降级控制器

```java
public class DegradationController {

    private final Map<String, Map<String, MarketCircuitBreaker>> circuitBreakers = new ConcurrentHashMap<>();
    private final DegradationConfig config;

    public enum DegradationLevel {
        L0_NORMAL, L1_WEAK, L2_MEDIUM, L3_STRONG, L4_CIRCUIT_OPEN
    }

    /**
     * 通过组合所有依赖熔断器状态来确定市场的整体降级级别。
     */
    public DegradationLevel getLevel(String market) {
        Map<String, MarketCircuitBreaker> breakers = circuitBreakers.get(market);
        if (breakers == null) return DegradationLevel.L0_NORMAL;

        long openCount = breakers.values().stream()
                .filter(b -> b.getState() == MarketCircuitBreaker.State.OPEN)
                .count();
        long halfOpenCount = breakers.values().stream()
                .filter(b -> b.getState() == MarketCircuitBreaker.State.HALF_OPEN)
                .count();
        int totalDeps = breakers.size();

        // L4: 所有关键依赖打开
        if (openCount >= config.getCriticalDependencyCount()) {
            return DegradationLevel.L4_CIRCUIT_OPEN;
        }

        // L3: 大多数依赖打开
        if (openCount > totalDeps / 2) {
            return DegradationLevel.L3_STRONG;
        }

        // L2: 至少一个关键依赖打开
        if (openCount > 0) {
            return DegradationLevel.L2_MEDIUM;
        }

        // L1: 至少一个依赖半开（降级）
        if (halfOpenCount > 0) {
            return DegradationLevel.L1_WEAK;
        }

        return DegradationLevel.L0_NORMAL;
    }

    /**
     * 检查在当前降级级别是否应处理事件。
     */
    public boolean isEventAllowed(String market, ConversationFact event) {
        DegradationLevel level = getLevel(market);

        return switch (level) {
            case L0_NORMAL -> true;  // 所有事件允许
            case L1_WEAK -> !isNonCriticalAction(event);
            case L2_MEDIUM -> isCriticalEvent(event);
            case L3_STRONG -> event == ConversationFact.CUSTOMER_CLOSE;
            case L4_CIRCUIT_OPEN -> false;  // 无事件允许
        };
    }

    private boolean isCriticalEvent(ConversationFact event) {
        return switch (event) {
            case CUSTOMER_CONNECT, CUSTOMER_CLOSE, SURVEY_COMPLETE,
                 SYS_ACTION_FAILED, SYS_RETRY, SYS_ABORT -> true;
            default -> false;
        };
    }

    private boolean isNonCriticalAction(ConversationFact event) {
        return switch (event) {
            // 触发非关键动作（审计、通知）的事件
            case TRANSFER_CONNECTED, SURVEY_START -> true;
            default -> false;
        };
    }
}
```

### 3.4 带熔断器的连接器

```java
public class CircuitBreakerAwareConnector {

    private final AibotConnector delegate;
    private final MarketCircuitBreaker circuitBreaker;

    public Optional<Connector.ConnectorResponse> sendMessage(String market,
                                                                String conversationId,
                                                                String message,
                                                                String userId) {
        // 1. 检查熔断器
        if (!circuitBreaker.tryAcquire()) {
            log.warn("AIBot circuit breaker open for market {}, returning cached response", market);
            return Optional.of(cachedResponse(conversationId));
        }

        // 2. 带超时执行
        try {
            Optional<Connector.ConnectorResponse> response =
                    delegate.sendMessage(conversationId, message, userId);
            circuitBreaker.recordSuccess();
            return response;
        } catch (Exception e) {
            circuitBreaker.recordFailure(e);
            throw e;
        }
    }

    private Connector.ConnectorResponse cachedResponse(String conversationId) {
        return Connector.ConnectorResponse.success(Map.of(
                "conversationId", conversationId,
                "cached", true,
                "message", "Service temporarily degraded, using cached response"
        ));
    }
}
```

### 3.5 带降级的事件处理

```java
public class DegradationAwareEventProcessor {

    private final DegradationController degradationController;
    private final StateMachineProcessor processor;

    public StateContext<...> process(String market, Event event, Conversation conversation) {
        // 1. 检查降级级别
        DegradationController.DegradationLevel level = degradationController.getLevel(market);

        // 2. 检查事件是否允许
        ConversationFact fact = mapToFact(event);
        if (!degradationController.isEventAllowed(market, fact)) {
            return rejectedResponse(level, fact, market);
        }

        // 3. 应用降级特定行为
        return switch (level) {
            case L0_NORMAL -> processor.process(event, conversation);
            case L1_WEAK -> processWithSkippedActions(event, conversation);
            case L2_MEDIUM -> processCriticalOnly(event, conversation);
            case L3_STRONG -> processCloseOnly(event, conversation);
            case L4_CIRCUIT_OPEN -> rejectedResponse(level, fact, market);
        };
    }

    private StateContext<...> processWithSkippedActions(Event event, Conversation conversation) {
        // 跳过非关键动作（审计、通知）但仍执行状态迁移
        return processor.process(event, conversation, ProcessingOptions.skipNonCriticalActions());
    }
}
```

### 3.6 降级感知状态机

对于 L1（弱降级），状态机可以跳过非关键动作：

```java
public class DegradationAwareStateMachine implements StateMachine<...> {

    private final StateMachine<...> delegate;
    private final DegradationController degradationController;
    private final String market;

    @Override
    public StateContext<...> fireEvent(ConversationState source, ConversationFact event, CbolStateContext ctx) {
        DegradationController.DegradationLevel level = degradationController.getLevel(market);

        if (level == DegradationController.DegradationLevel.L1_WEAK) {
            // 跳过标记为非关键的 entry/exit 动作和迁移动作
            return delegate.fireEvent(source, event, ctx,
                    ExtendedState.with("skipNonCriticalActions", true));
        }

        return delegate.fireEvent(source, event, ctx);
    }
}
```

---

## 4. 监控与告警

### 4.1 熔断器指标

```
# HELP circuit_breaker_state Current state of circuit breaker (0=CLOSED, 1=HALF_OPEN, 2=OPEN)
# TYPE circuit_breaker_state gauge
circuit_breaker_state{market="HK",dependency="aibot"} 0
circuit_breaker_state{market="HK",dependency="genesys"} 2
circuit_breaker_state{market="SG",dependency="aibot"} 0

# HELP circuit_breaker_failures_total Total failures recorded
# TYPE circuit_breaker_failures_total counter
circuit_breaker_failures_total{market="HK",dependency="genesys"} 42

# HELP degradation_level Current degradation level (0-4)
# TYPE degradation_level gauge
degradation_level{market="HK"} 2
degradation_level{market="SG"} 0
degradation_level{market="UK"} 1
```

### 4.2 告警规则

| 告警 | 条件 | 严重程度 |
|-------|-----------|----------|
| 熔断器打开 | circuit_breaker_state == 2 超过 1 分钟 | 警告 |
| 市场降级 | degradation_level >= 2 超过 5 分钟 | 严重 |
| 多个熔断器打开 | 同一市场中 > 2 个依赖打开 | 严重 |
| 全局降级 | > 50% 市场处于 L2+ | 严重 |
| 熔断器抖动 | 10 分钟内状态变更 > 5 次 | 警告 |
| 恢复 | 熔断器转换 OPEN → CLOSED | 信息 |

---

## 5. 实施路线图

### 阶段 1：熔断器核心（1 天）
- [ ] 实现带 CLOSED/OPEN/HALF_OPEN 状态的 `MarketCircuitBreaker`
- [ ] 实现带 defaults/aggressive/lenient 预设的 `CircuitBreakerConfig`
- [ ] 实现每市场每依赖熔断器注册表
- [ ] 状态转换、阈值、恢复的单元测试

### 阶段 2：连接器集成（1 天）
- [ ] 用熔断器包装 AIBot 连接器
- [ ] 用熔断器包装 Genesys 连接器
- [ ] 为打开的熔断器实现缓存/回退响应
- [ ] 实现超时强制执行
- [ ] 熔断器行为的集成测试

### 阶段 3：降级控制器（1 天）
- [ ] 实现带 5 个级别的 `DegradationController`
- [ ] 实现每级别的事件允许/拒绝
- [ ] 实现降级感知事件处理器
- [ ] 实现 L1 动作跳过
- [ ] 每个降级级别的单元测试

### 阶段 4：监控与运维（0.5 天）
- [ ] 实现熔断器指标（Micrometer）
- [ ] 实现降级级别指标
- [ ] 实现带熔断器状态的健康端点
- [ ] 实现告警规则
- [ ] 实现手动熔断器控制（管理员 API：强制打开/关闭/重置）

---

## 6. 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|-----------|--------|------------|
| 熔断器过于激进（在瞬时错误上打开） | 中 | 中 | 可配置阈值；滑动窗口；最小失败计数；非关键依赖的宽松默认值 |
| 熔断器过于宽松（应打开时不打开） | 中 | 中 | 监控打开时间指标；高错误率但熔断器未打开时告警；在 staging 中调整阈值 |
| 缓存响应变陈旧 | 中 | 低 | 缓存响应上的短 TTL；清楚标记缓存响应；仅缓存幂等/只读操作 |
| 降级级别抖动（在级别之间振荡） | 低 | 中 | 滞后：在更改级别前要求持续状态变更（例如连续 3 次检查） |
| L1 动作跳过导致数据丢失（审计日志缺失） | 低 | 中 | 审计日志是尽力执行的；关键审计（财务）永不跳过；L1 仅跳过非关键动作 |
| 重启时熔断器状态丢失 | 低 | 低 | 熔断器在内存中；重启时重置为 CLOSED（安全默认值）；持久状态可选 |
| 手动熔断器控制被滥用（有人强制打开并忘记） | 低 | 中 | 手动覆盖有 TTL（1 小时后自动过期）；所有手动操作的审计日志；强制打开熔断器时告警 |

---

## 7. 成功标准

- [ ] 熔断器在依赖失败阈值违规后 30 秒内打开
- [ ] 熔断器在依赖恢复后 60 秒内关闭
- [ ] 打开的熔断器在 < 100ms 内返回响应（快速失败，不等待超时）
- [ ] 一个市场的打开熔断器不影响其他市场
- [ ] 降级级别实时可见（健康端点 + 指标）
- [ ] L1 降级跳过非关键动作但完成所有状态迁移
- [ ] L4 降级用清晰的 503 响应拒绝所有事件
- [ ] 手动熔断器控制（打开/关闭/重置）可通过管理员 API 获得
- [ ] 熔断器状态变更被记录和告警
- [ ] 从降级恢复是自动的（无需人工干预）
