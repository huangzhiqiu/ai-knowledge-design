# 03. 市场路由与故障隔离

> 版本：1.0 | 最后更新：2026-09-01
> 优先级：P1 | 预估工作量：3-4 天

## 1. 问题陈述

### 1.1 当前问题

所有市场共享相同的执行资源（线程池、事件队列、处理能力）。如果一个市场经历流量高峰或导致无限循环/死锁的 bug，它可能耗尽共享资源并降低或破坏所有其他市场。

### 1.2 场景

- **流量高峰**：HK 发起营销活动，流量是正常的 10 倍，线程池饱和，UK/SG 请求排队并超时
- **市场特定动作中的 bug**：HK 的 Genesys 连接器有死锁，线程卡住，池耗尽，所有市场受影响
- **配置错误**：HK 配置有无限循环 guard，事件永远循环，CPU 固定在 100%
- **慢速依赖**：HK 的 AIBot 端点慢（5 秒响应），线程阻塞，池耗尽

### 1.3 目标

- 每个请求正确路由到其市场的配置
- 一个市场的故障不会级联到其他市场
- 每个市场有独立的资源限制和监控
- 降级是优雅的（快速失败并带清晰错误，而不是挂起）

---

## 2. 设计概述

### 2.1 架构

```
                    ┌──────────────────────────────────────┐
                    │         事件入口层           │
                    │  (WebSocket / HTTP / 队列消费者)  │
                    └──────────────────┬───────────────────┘
                                       │
                    ┌──────────────────▼───────────────────┐
                    │         市场路由器                  │
                    │  从事件/会话解析市场    │
                    └──────────────────┬───────────────────┘
                                       │
          ┌────────────────────────────┼────────────────────────────┐
          │                            │                            │
┌─────────▼─────────┐      ┌─────────▼─────────┐      ┌─────────▼─────────┐
│  HK 执行器       │      │  UK 执行器       │      │  SG 执行器       │
│  ┌───────────────┐ │      │  ┌───────────────┐ │      │  ┌───────────────┐ │
│  │ 线程池   │ │      │  │ 线程池   │ │      │  │ 线程池   │ │
│  │ (core=4,max=8)│ │      │  │ (core=2,max=4)│ │      │  │ (core=2,max=4)│ │
│  └───────────────┘ │      │  └───────────────┘ │      │  └───────────────┘ │
│  ┌───────────────┐ │      │  ┌───────────────┐ │      │  ┌───────────────┐ │
│  │ 信号量     │ │      │  │ 信号量     │ │      │  │ 信号量     │ │
│  │ (maxConcurrent)│ │      │  │ (maxConcurrent)│ │      │  │ (maxConcurrent)│ │
│  └───────────────┘ │      │  └───────────────┘ │      │  └───────────────┘ │
│  ┌───────────────┐ │      │  ┌───────────────┐ │      │  ┌───────────────┐ │
│  │ 熔断器│ │      │  │ 熔断器│ │      │  │ 熔断器│ │
│  └───────────────┘ │      │  └───────────────┘ │      │  └───────────────┘ │
│  ┌───────────────┐ │      │  ┌───────────────┐ │      │  ┌───────────────┐ │
│  │ 指标       │ │      │  │ 指标       │ │      │  │ 指标       │ │
│  └───────────────┘ │      │  └───────────────┘ │      │  └───────────────┘ │
└────────────────────┘      └────────────────────┘      └────────────────────┘
          │                            │                            │
          └────────────────────────────┼────────────────────────────┘
                                       │
                    ┌──────────────────▼───────────────────┐
                    │    共享状态机引擎        │
                    │  （只读定义，无状态）   │
                    └──────────────────────────────────────┘
```

### 2.2 关键原则

| 原则 | 描述 |
|-----------|-------------|
| **舱壁模式** | 每个市场有自己的线程池和并发限制 |
| **熔断器** | 每个市场有独立的故障检测和快速失败 |
| **共享引擎** | 状态机定义是只读的，可以安全共享 |
| **配置隔离** | 每个市场的配置是不可变快照，无交叉污染 |
| **优雅降级** | 当市场过载时，快速失败返回 503，不要无限排队 |

---

## 3. 详细设计

### 3.1 市场路由器

```java
public class MarketRouter {

    private final TenantMarketMapping tenantMapping;
    private final Set<String> validMarkets;

    /**
     * 使用优先级顺序解析事件的市场：
     * 1. 事件上的显式市场字段
     * 2. 绑定到会话/会话的市场
     * 3. 租户/组织映射
     * 4. 默认市场（可配置，通常是最常见的一个）
     */
    public String resolve(Event event, Conversation conversation) {
        // 1. 事件上的显式市场
        if (event.getMarket() != null && validMarkets.contains(event.getMarket())) {
            return event.getMarket();
        }

        // 2. 会话绑定的市场
        if (conversation != null && conversation.getMarket() != null) {
            return conversation.getMarket();
        }

        // 3. 租户映射
        if (event.getTenantId() != null) {
            String market = tenantMapping.getMarket(event.getTenantId());
            if (market != null) return market;
        }

        // 4. 默认
        return "DEFAULT";
    }

    /**
     * 校验市场是否已知且已启用。
     * 如果未找到，抛出 UnknownMarketException。
     */
    public void validate(String market) {
        if (!validMarkets.contains(market)) {
            throw new UnknownMarketException("Unknown or disabled market: " + market);
        }
    }
}
```

### 3.2 市场隔离执行器

```java
public class MarketIsolatedExecutor {

    private final Map<String, MarketExecutor> executors = new ConcurrentHashMap<>();
    private final MarketExecutorFactory factory;

    public MarketIsolatedExecutor(MarketExecutorFactory factory, List<String> markets) {
        this.factory = factory;
        for (String market : markets) {
            executors.put(market, factory.create(market));
        }
    }

    /**
     * 提交事件由其市场的执行器处理。
     * 返回一个 CompletableFuture，完成时带结果。
     */
    public <T> CompletableFuture<T> submit(String market, Supplier<T> task) {
        MarketExecutor executor = executors.get(market);
        if (executor == null) {
            return CompletableFuture.failedFuture(new UnknownMarketException(market));
        }
        return executor.submit(task);
    }

    /**
     * 返回所有市场的当前状态（用于监控/健康检查）。
     */
    public Map<String, MarketStatus> getStatus() {
        return executors.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getStatus()));
    }
}
```

### 3.3 每市场执行器（舱壁）

```java
public class MarketExecutor {

    private final String market;
    private final ThreadPoolExecutor threadPool;
    private final Semaphore concurrencyLimiter;
    private final CircuitBreaker circuitBreaker;
    private final MarketMetrics metrics;

    public MarketExecutor(String market, MarketExecutorConfig config) {
        this.market = market;
        this.threadPool = new ThreadPoolExecutor(
                config.corePoolSize(),
                config.maxPoolSize(),
                config.keepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.queueCapacity()),
                new NamedThreadFactory("sm-" + market),
                new ThreadPoolExecutor.AbortPolicy()  // 快速失败，不要挂起
        );
        this.concurrencyLimiter = new Semaphore(config.maxConcurrentEvents());
        this.circuitBreaker = CircuitBreaker.ofDefaults("sm-" + market);
        this.metrics = new MarketMetrics(market);
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        // 1. 熔断器检查
        if (!circuitBreaker.tryAcquirePermission()) {
            metrics.recordCircuitBreakerReject();
            return CompletableFuture.failedFuture(
                    new MarketCircuitOpenException("Circuit breaker is open for market: " + market));
        }

        // 2. 并发限制检查
        if (!concurrencyLimiter.tryAcquire()) {
            metrics.recordConcurrencyReject();
            return CompletableFuture.failedFuture(
                    new MarketOverloadedException("Market " + market + " is overloaded (max concurrent reached)"));
        }

        // 3. 提交到线程池
        long start = System.nanoTime();
        return CompletableFuture.supplyAsync(() -> {
            try {
                T result = task.get();
                circuitBreaker.recordSuccess();
                metrics.recordSuccess(System.nanoTime() - start);
                return result;
            } catch (Exception e) {
                circuitBreaker.recordFailure(e);
                metrics.recordFailure(e);
                throw e;
            } finally {
                concurrencyLimiter.release();
            }
        }, threadPool);
    }

    public MarketStatus getStatus() {
        return new MarketStatus(
                market,
                threadPool.getActiveCount(),
                threadPool.getPoolSize(),
                threadPool.getQueue().size(),
                concurrencyLimiter.availablePermits(),
                circuitBreaker.getState(),
                metrics.getErrorRate()
        );
    }

    public void shutdown() {
        threadPool.shutdown();
    }
}
```

### 3.4 每市场配置

```java
public record MarketExecutorConfig(
    String market,
    int corePoolSize,           // 例如 HK=4, UK=2, SG=2
    int maxPoolSize,            // 例如 HK=8, UK=4, SG=4
    int queueCapacity,          // 例如 HK=100, UK=50, SG=50
    int maxConcurrentEvents,    // 例如 HK=50, UK=25, SG=25
    long eventTimeoutMs,        // 例如 HK=5000, UK=10000
    double circuitBreakerFailureRateThreshold,  // 例如 0.5 (50%)
    int circuitBreakerWindowSize,               // 例如 100 请求
    long circuitBreakerOpenDurationMs           // 例如 30000 (30秒)
) {
    public static MarketExecutorConfig defaultConfig(String market) {
        return new MarketExecutorConfig(market, 2, 4, 50, 25, 5000, 0.5, 100, 30000);
    }

    public static MarketExecutorConfig highTrafficConfig(String market) {
        return new MarketExecutorConfig(market, 4, 8, 100, 50, 5000, 0.5, 100, 30000);
    }
}
```

### 3.5 事件处理管道

```java
public class MarketAwareEventProcessor {

    private final MarketRouter router;
    private final MarketIsolatedExecutor executor;
    private final StateMachineProcessor processor;
    private final ConfigProvider configProvider;

    public StateContext<...> process(Event event, Conversation conversation) {
        // 1. 解析市场
        String market = router.resolve(event, conversation);
        router.validate(market);

        // 2. 加载市场配置（不可变快照）
        StateMachineMarketConfig config = configProvider.getConfig(market);

        // 3. 提交到市场隔离执行器
        return executor.submit(market, () ->
                processor.process(event, conversation, config)
        ).join();  // 或适当处理异步
    }
}
```

---

## 4. 监控与可观测性

### 4.1 每市场指标（Micrometer）

```java
public class MarketMetrics {
    private final Timer processingTimer;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter circuitBreakerRejectCounter;
    private final Counter concurrencyRejectCounter;
    private final AtomicInteger activeConcurrency;
    private final AtomicInteger queueSize;

    public MarketMetrics(String market) {
        this.processingTimer = Timer.builder("statemachine.processing.duration")
                .tag("market", market)
                .register(registry);
        this.successCounter = Counter.builder("statemachine.events.processed")
                .tag("market", market).tag("result", "success")
                .register(registry);
        // ... 其他指标
    }
}
```

### 4.2 健康检查端点

```json
GET /actuator/health/market-executors

{
  "status": "UP",
  "markets": {
    "HK": {
      "status": "UP",
      "activeThreads": 3,
      "poolSize": 4,
      "queueSize": 12,
      "availablePermits": 47,
      "circuitBreaker": "CLOSED",
      "errorRate": 0.02
    },
    "UK": {
      "status": "DEGRADED",
      "activeThreads": 4,
      "poolSize": 4,
      "queueSize": 48,
      "availablePermits": 5,
      "circuitBreaker": "CLOSED",
      "errorRate": 0.08
    },
    "SG": {
      "status": "CIRCUIT_OPEN",
      "activeThreads": 0,
      "poolSize": 2,
      "queueSize": 0,
      "availablePermits": 25,
      "circuitBreaker": "OPEN",
      "errorRate": 0.75
    }
  }
}
```

### 4.3 告警规则

| 告警 | 条件 | 严重程度 |
|-------|-----------|----------|
| 市场熔断器打开 | circuitBreaker == OPEN 超过 1 分钟 | 严重 |
| 市场队列接近容量 | queueSize > 容量的 80% | 警告 |
| 市场错误率飙升 | errorRate > 10% 持续 5 分钟 | 警告 |
| 市场线程池饱和 | activeThreads == maxPoolSize 持续 5 分钟 | 警告 |
| 市场处理延迟高 | P99 延迟 > 基线的 2 倍 | 警告 |
| 全局：所有市场降级 | > 50% 市场处于 DEGRADED/CIRCUIT_OPEN | 严重 |

---

## 5. 实施路线图

### 阶段 1：市场路由器（0.5 天）
- [ ] 实现带基于优先级解析的 `MarketRouter`
- [ ] 实现 `TenantMarketMapping`
- [ ] 实现 `UnknownMarketException`
- [ ] 解析优先级和校验的单元测试

### 阶段 2：市场隔离执行器（1.5 天）
- [ ] 实现带线程池 + 信号量的 `MarketExecutor`
- [ ] 实现 `MarketIsolatedExecutor`（每市场执行器的 map）
- [ ] 实现带每市场大小调整的 `MarketExecutorConfig`
- [ ] 实现优雅关闭
- [ ] 并发限制、队列拒绝、隔离的单元测试

### 阶段 3：熔断器集成（1 天）
- [ ] 每市场集成 Resilience4j CircuitBreaker
- [ ] 实现 `MarketCircuitOpenException`
- [ ] 实现熔断器指标
- [ ] OPEN/HALF_OPEN/CLOSED 转换的单元测试

### 阶段 4：监控与健康（1 天）
- [ ] 实现 `MarketMetrics`（Micrometer）
- [ ] 实现健康检查端点
- [ ] 实现告警规则（Prometheus/Grafana）
- [ ] 完整管道的集成测试

---

## 6. 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|-----------|--------|------------|
| 每市场线程池增加内存使用 | 中 | 低 | 核心池大小小（2-4），空闲线程回收；总数受市场数量限制 |
| 市场路由不正确（事件到错误市场） | 低 | 高 | 带显式优先级的多级解析；市场针对已知列表校验；市场在 MDC 中用于日志 |
| 熔断器过于激进（在瞬时错误上打开） | 中 | 中 | 可配置阈值（100 请求上 50% 失败率）；带探测请求的 HALF_OPEN |
| 信号量 + 线程池双重限制导致混淆 | 低 | 中 | 清晰文档：信号量 = 并发进行中，线程池 = 执行能力；监控两者 |
| 共享状态机引擎有可变状态 | 低 | 高 | 引擎必须是无状态的（当前状态每次调用注入）；所有可变状态在上下文/仓库中 |
| 快速失败拒绝丢失事件 | 中 | 中 | 调用方必须处理拒绝（退避重试 / 死信队列）；事件是幂等的 |
| 新市场未配置 → 无执行器 | 低 | 中 | 执行器在第一个事件上用默认配置惰性创建；未知市场告警 |

---

## 7. 成功标准

- [ ] 10 倍流量的市场不会使其他市场的延迟增加 > 10%
- [ ] 有死锁动作的市场不影响其他市场
- [ ] 熔断器在阈值违规后 30 秒内为失败市场打开
- [ ] 过载市场在 100ms 内返回 503（快速失败），而不是挂起
- [ ] 每个事件日志包含市场标签用于追踪
- [ ] 健康端点实时显示每市场状态
- [ ] 总线程数有界：所有市场的 maxPoolSize 总和 < 2 倍 CPU 核心数
- [ ] 新市场在第一个事件上自动创建带默认配置的执行器
