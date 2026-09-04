# 03. Market Routing & Fault Isolation

> Version: 1.0 | Last Updated: 2026-09-01
> Priority: P1 | Estimated Effort: 3-4 days

## 1. Problem Statement

### 1.1 Current Issue
All markets share the same execution resources (thread pools, event queues, processing capacity). If one market experiences a spike in traffic or a bug causing infinite loops / deadlocks, it can exhaust shared resources and degrade or break all other markets.

### 1.2 Scenarios
- **Traffic spike**: HK launches a marketing campaign, traffic 10x normal, thread pool saturated, UK/SG requests queue and timeout
- **Bug in market-specific action**: HK's Genesys connector has a deadlock, threads get stuck, pool depletes, all markets affected
- **Configuration error**: HK config has an infinite loop guard, events cycle forever, CPU pegged at 100%
- **Slow dependency**: HK's AIBot endpoint is slow (5s response), threads block, pool depletes

### 1.3 Goals
- Every request is correctly routed to its market's configuration
- One market's failure does not cascade to other markets
- Each market has independent resource limits and monitoring
- Degradation is graceful (fail fast with clear error, not hang)

---

## 2. Design Overview

### 2.1 Architecture

```
                    ┌──────────────────────────────────────┐
                    │         Event Ingress Layer           │
                    │  (WebSocket / HTTP / Queue Consumer)  │
                    └──────────────────┬───────────────────┘
                                       │
                    ┌──────────────────▼───────────────────┐
                    │         Market Router                  │
                    │  Resolves market from event/session    │
                    └──────────────────┬───────────────────┘
                                       │
          ┌────────────────────────────┼────────────────────────────┐
          │                            │                            │
┌─────────▼─────────┐      ┌─────────▼─────────┐      ┌─────────▼─────────┐
│  HK Executor       │      │  UK Executor       │      │  SG Executor       │
│  ┌───────────────┐ │      │  ┌───────────────┐ │      │  ┌───────────────┐ │
│  │ Thread Pool   │ │      │  │ Thread Pool   │ │      │  │ Thread Pool   │ │
│  │ (core=4,max=8)│ │      │  │ (core=2,max=4)│ │      │  │ (core=2,max=4)│ │
│  └───────────────┘ │      │  └───────────────┘ │      │  └───────────────┘ │
│  ┌───────────────┐ │      │  ┌───────────────┐ │      │  ┌───────────────┐ │
│  │ Semaphore     │ │      │  │ Semaphore     │ │      │  │ Semaphore     │ │
│  │ (maxConcurrent)│ │      │  │ (maxConcurrent)│ │      │  │ (maxConcurrent)│ │
│  └───────────────┘ │      │  └───────────────┘ │      │  └───────────────┘ │
│  ┌───────────────┐ │      │  ┌───────────────┐ │      │  ┌───────────────┐ │
│  │ Circuit Breaker│ │      │  │ Circuit Breaker│ │      │  │ Circuit Breaker│ │
│  └───────────────┘ │      │  └───────────────┘ │      │  └───────────────┘ │
│  ┌───────────────┐ │      │  ┌───────────────┐ │      │  ┌───────────────┐ │
│  │ Metrics       │ │      │  │ Metrics       │ │      │  │ Metrics       │ │
│  └───────────────┘ │      │  └───────────────┘ │      │  └───────────────┘ │
└────────────────────┘      └────────────────────┘      └────────────────────┘
          │                            │                            │
          └────────────────────────────┼────────────────────────────┘
                                       │
                    ┌──────────────────▼───────────────────┐
                    │    Shared State Machine Engine        │
                    │  (read-only definitions, stateless)   │
                    └──────────────────────────────────────┘
```

### 2.2 Key Principles

| Principle | Description |
|-----------|-------------|
| **Bulkhead pattern** | Each market has its own thread pool and concurrency limit |
| **Circuit breaker** | Each market has independent failure detection and fast-fail |
| **Shared engine** | State machine definitions are read-only and safely shared |
| **Config isolation** | Each market's config is an immutable snapshot, no cross-contamination |
| **Graceful degradation** | When a market is overloaded, fail fast with 503, don't queue indefinitely |

---

## 3. Detailed Design

### 3.1 Market Router

```java
public class MarketRouter {

    private final TenantMarketMapping tenantMapping;
    private final Set<String> validMarkets;

    /**
     * Resolves the market for an event using priority order:
     * 1. Explicit market field on the event
     * 2. Market bound to the conversation/session
     * 3. Tenant/org mapping
     * 4. Default market (configurable, usually the most common one)
     */
    public String resolve(Event event, Conversation conversation) {
        // 1. Explicit market on event
        if (event.getMarket() != null && validMarkets.contains(event.getMarket())) {
            return event.getMarket();
        }

        // 2. Conversation-bound market
        if (conversation != null && conversation.getMarket() != null) {
            return conversation.getMarket();
        }

        // 3. Tenant mapping
        if (event.getTenantId() != null) {
            String market = tenantMapping.getMarket(event.getTenantId());
            if (market != null) return market;
        }

        // 4. Default
        return "DEFAULT";
    }

    /**
     * Validates that a market is known and enabled.
     * Throws UnknownMarketException if not found.
     */
    public void validate(String market) {
        if (!validMarkets.contains(market)) {
            throw new UnknownMarketException("Unknown or disabled market: " + market);
        }
    }
}
```

### 3.2 Market-Isolated Executor

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
     * Submits an event for processing by its market's executor.
     * Returns a CompletableFuture that completes with the result.
     */
    public <T> CompletableFuture<T> submit(String market, Supplier<T> task) {
        MarketExecutor executor = executors.get(market);
        if (executor == null) {
            return CompletableFuture.failedFuture(new UnknownMarketException(market));
        }
        return executor.submit(task);
    }

    /**
     * Returns the current status of all markets (for monitoring/health checks).
     */
    public Map<String, MarketStatus> getStatus() {
        return executors.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getStatus()));
    }
}
```

### 3.3 Per-Market Executor (Bulkhead)

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
                new ThreadPoolExecutor.AbortPolicy()  // fail fast, don't hang
        );
        this.concurrencyLimiter = new Semaphore(config.maxConcurrentEvents());
        this.circuitBreaker = CircuitBreaker.ofDefaults("sm-" + market);
        this.metrics = new MarketMetrics(market);
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        // 1. Circuit breaker check
        if (!circuitBreaker.tryAcquirePermission()) {
            metrics.recordCircuitBreakerReject();
            return CompletableFuture.failedFuture(
                    new MarketCircuitOpenException("Circuit breaker is open for market: " + market));
        }

        // 2. Concurrency limit check
        if (!concurrencyLimiter.tryAcquire()) {
            metrics.recordConcurrencyReject();
            return CompletableFuture.failedFuture(
                    new MarketOverloadedException("Market " + market + " is overloaded (max concurrent reached)"));
        }

        // 3. Submit to thread pool
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

### 3.4 Per-Market Configuration

```java
public record MarketExecutorConfig(
    String market,
    int corePoolSize,           // e.g., HK=4, UK=2, SG=2
    int maxPoolSize,            // e.g., HK=8, UK=4, SG=4
    int queueCapacity,          // e.g., HK=100, UK=50, SG=50
    int maxConcurrentEvents,    // e.g., HK=50, UK=25, SG=25
    long eventTimeoutMs,        // e.g., HK=5000, UK=10000
    double circuitBreakerFailureRateThreshold,  // e.g., 0.5 (50%)
    int circuitBreakerWindowSize,               // e.g., 100 requests
    long circuitBreakerOpenDurationMs           // e.g., 30000 (30s)
) {
    public static MarketExecutorConfig defaultConfig(String market) {
        return new MarketExecutorConfig(market, 2, 4, 50, 25, 5000, 0.5, 100, 30000);
    }

    public static MarketExecutorConfig highTrafficConfig(String market) {
        return new MarketExecutorConfig(market, 4, 8, 100, 50, 5000, 0.5, 100, 30000);
    }
}
```

### 3.5 Event Processing Pipeline

```java
public class MarketAwareEventProcessor {

    private final MarketRouter router;
    private final MarketIsolatedExecutor executor;
    private final StateMachineProcessor processor;
    private final ConfigProvider configProvider;

    public StateContext<...> process(Event event, Conversation conversation) {
        // 1. Resolve market
        String market = router.resolve(event, conversation);
        router.validate(market);

        // 2. Load market config (immutable snapshot)
        StateMachineMarketConfig config = configProvider.getConfig(market);

        // 3. Submit to market-isolated executor
        return executor.submit(market, () ->
                processor.process(event, conversation, config)
        ).join();  // or handle async appropriately
    }
}
```

---

## 4. Monitoring & Observability

### 4.1 Per-Market Metrics (Micrometer)

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
        // ... other metrics
    }
}
```

### 4.2 Health Check Endpoint

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

### 4.3 Alert Rules

| Alert | Condition | Severity |
|-------|-----------|----------|
| Market circuit breaker open | circuitBreaker == OPEN for > 1 min | Critical |
| Market queue near capacity | queueSize > 80% of capacity | Warning |
| Market error rate spike | errorRate > 10% for 5 min | Warning |
| Market thread pool saturated | activeThreads == maxPoolSize for 5 min | Warning |
| Market processing latency high | P99 latency > 2x baseline | Warning |
| Global: all markets degraded | > 50% markets in DEGRADED/CIRCUIT_OPEN | Critical |

---

## 5. Implementation Roadmap

### Phase 1: Market Router (0.5 day)
- [ ] Implement `MarketRouter` with priority-based resolution
- [ ] Implement `TenantMarketMapping`
- [ ] Implement `UnknownMarketException`
- [ ] Unit tests for resolution priority and validation

### Phase 2: Market-Isolated Executor (1.5 days)
- [ ] Implement `MarketExecutor` with thread pool + semaphore
- [ ] Implement `MarketIsolatedExecutor` (map of per-market executors)
- [ ] Implement `MarketExecutorConfig` with per-market sizing
- [ ] Implement graceful shutdown
- [ ] Unit tests for concurrency limits, queue rejection, isolation

### Phase 3: Circuit Breaker Integration (1 day)
- [ ] Integrate Resilience4j CircuitBreaker per market
- [ ] Implement `MarketCircuitOpenException`
- [ ] Implement circuit breaker metrics
- [ ] Unit tests for OPEN/HALF_OPEN/CLOSED transitions

### Phase 4: Monitoring & Health (1 day)
- [ ] Implement `MarketMetrics` (Micrometer)
- [ ] Implement health check endpoint
- [ ] Implement alert rules (Prometheus/Grafana)
- [ ] Integration tests for full pipeline

---

## 6. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Thread pool per market increases memory usage | Medium | Low | Core pool size small (2-4), idle threads reclaimed; total bounded by market count |
| Market routing incorrect (event goes to wrong market) | Low | High | Multi-level resolution with explicit priority; market validated against known list; market in MDC for logging |
| Circuit breaker too aggressive (opens on transient errors) | Medium | Medium | Configurable threshold (50% failure rate over 100 requests); HALF_OPEN with probe requests |
| Semaphore + thread pool double-limiting causes confusion | Low | Medium | Clear documentation: semaphore = concurrent in-flight, thread pool = execution capacity; monitor both |
| Shared state machine engine has mutable state | Low | High | Engine must be stateless (current state injected per call); all mutable state in context/repository |
| Fail-fast rejection loses events | Medium | Medium | Caller must handle rejection (retry with backoff / dead-letter queue); events are idempotent |
| New market not configured → no executor | Low | Medium | Executor lazily created on first event with default config; alert on unknown market |

---

## 7. Success Criteria

- [ ] A market with 10x traffic does not increase latency of other markets by > 10%
- [ ] A market with a deadlocking action does not affect other markets
- [ ] Circuit breaker opens for a failing market within 30 seconds of threshold breach
- [ ] Overloaded market returns 503 (fail fast) within 100ms, not hang
- [ ] Every event log includes market tag for tracing
- [ ] Health endpoint shows per-market status in real-time
- [ ] Total thread count bounded: sum of maxPoolSize across all markets < 2x CPU cores
- [ ] New market auto-creates executor with default config on first event
