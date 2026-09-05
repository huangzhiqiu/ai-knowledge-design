# 05 — 高级功能

> 基于阿里巴巴 COLA StateMachine 的生产级功能：多市场配置、监控器、追踪上下文、幂等性、可观测性、PlantUML 生成和扩展模式。

---

## 1. 多市场配置

### 问题

项目部署到多个市场（HK、SG、UK 等），状态流程相似但略有不同。每个市场可能有不同的超时时间、功能开关和路由策略。

### 解决方案

使用 `StateMachineMarketConfig` record 将市场特定行为捕获为不可变快照。配置在每次状态转换开始时注入到 `CbolStateContext` 中。

```java
@Builder
public record StateMachineMarketConfig(
    int customerIdleSeconds,       // 默认：300
    int transferTimeoutSeconds,    // 默认：120
    int endingGraceSeconds,        // 默认：30
    boolean surveyEnabled,         // 默认：true
    boolean transferEnabled,       // 默认：true
    boolean genesysEnabled,        // 默认：true
    String fallbackRoutingStrategy // 默认："DROP"
) {
    public static StateMachineMarketConfig defaultConfig() { ... }
}
```

### 市场配置提供者

```java
public interface MarketConfigProvider {
    StateMachineMarketConfig getConfig(String market);

    class InMemoryProvider implements MarketConfigProvider {
        private final ConcurrentHashMap<String, StateMachineMarketConfig> cache = new ConcurrentHashMap<>();

        public void put(String market, StateMachineMarketConfig config) {
            cache.put(market, config);
        }

        @Override
        public StateMachineMarketConfig getConfig(String market) {
            return cache.getOrDefault(market, StateMachineMarketConfig.defaultConfig());
        }
    }
}
```

### 在转换中使用

```java
CbolStateContext ctx = CbolStateContext.builder()
        .conversation(conversation)
        .marketConfig(marketConfigProvider.getConfig("HK"))
        .traceContext(TraceContext.generate())
        .build();

ConversationState newState = sm.fireEvent(
        conversation.state(),
        ConversationFact.INTERACTION_BECAME_ACTIVE,
        ctx);
```

### 设计原则

- **配置即快照**：市场配置在转换开始时捕获，而不是在动作执行期间动态读取
- **默认优先**：所有配置字段都有合理的默认值；市场只覆盖不同的部分
- **不可变**：配置是 record，在转换期间不能被修改
- **功能开关**：布尔字段（surveyEnabled、transferEnabled、genesysEnabled）控制哪些转换处于活动状态

---

## 2. 监控器（超时和健康检查）

### 问题

状态机需要检测和处理超时：客户空闲、转接超时、结束宽限期。这些是基于时间的事件，应该触发自动状态转换。

### 解决方案

三个监控器类，检查经过的时间，并在超过阈值时触发系统事件。

### 2.1 客户空闲监控器

```java
public class CustomerIdleMonitor {
    private final ChatEngineStateMachineService service;

    public void check(CbolStateContext ctx, long lastActivityTimestamp) {
        long idleMs = System.currentTimeMillis() - lastActivityTimestamp;
        int threshold = ctx.marketConfig().customerIdleSeconds() * 1000;

        if (idleMs > threshold) {
            service.fire(ctx, ConversationFact.CUSTOMER_IDLE_TIMEOUT);
        }
    }
}
```

### 2.2 转接监控器

```java
public class TransferMonitor {
    private final ChatEngineStateMachineService service;

    public void check(CbolStateContext ctx, long transferStartTimestamp) {
        // 仅在 TRANSFERRED 状态下激活
        if (ctx.conversation().state() != ConversationState.TRANSFERRED) {
            return;
        }

        long elapsedMs = System.currentTimeMillis() - transferStartTimestamp;
        int threshold = ctx.marketConfig().transferTimeoutSeconds() * 1000;

        if (elapsedMs > threshold) {
            service.fire(ctx, ConversationFact.TRANSFER_TIMEOUT);
        }
    }
}
```

### 2.3 结束宽限监控器

```java
public class EndingGraceMonitor {
    private final ChatEngineStateMachineService service;

    public void check(CbolStateContext ctx, long enterEndingTimestamp) {
        // 仅在 ENDING 状态下激活
        if (ctx.conversation().state() != ConversationState.ENDING) {
            return;
        }

        long elapsedMs = System.currentTimeMillis() - enterEndingTimestamp;
        int threshold = ctx.marketConfig().endingGraceSeconds() * 1000;

        if (elapsedMs > threshold) {
            service.fire(ctx, ConversationFact.ENDING_TIMEOUT);
        }
    }
}
```

### 监控器集成模式

```java
// 定时任务（例如，@Scheduled 每 30 秒）
@Scheduled(fixedDelay = 30000)
public void runMonitors() {
    List<Conversation> activeConversations = repository.findActive();

    for (Conversation conv : activeConversations) {
        CbolStateContext ctx = buildContext(conv);

        customerIdleMonitor.check(ctx, conv.getLastActivityAt());
        transferMonitor.check(ctx, conv.getTransferStartedAt());
        endingGraceMonitor.check(ctx, conv.getEnteredEndingAt());
    }
}
```

---

## 3. 追踪上下文和可观测性

### 问题

在分布式系统中，状态转换需要跨服务可追踪。每次转换都应携带追踪 ID 用于日志记录和调试。

### 解决方案

使用带有基于 UUID 的追踪 ID 的 `TraceContext` record，加上用于 SLF4J MDC 传播的 `TraceMdcHelper`。

### 3.1 追踪上下文

```java
public record TraceContext(
    String traceId,
    long timestamp
) {
    public static TraceContext generate() {
        return new TraceContext(UUID.randomUUID().toString(), System.currentTimeMillis());
    }
}
```

### 3.2 MDC 传播

```java
public class TraceMdcHelper {
    private static final String TRACE_ID_KEY = "traceId";

    public static void set(TraceContext ctx) {
        MDC.put(TRACE_ID_KEY, ctx.traceId());
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
```

### 3.3 在 Service 中使用

```java
public ConversationState fire(CbolStateContext ctx, ConversationFact fact) {
    TraceMdcHelper.set(ctx.traceContext());
    long start = System.currentTimeMillis();
    try {
        ConversationState from = ctx.conversation().state();
        ConversationState to = convSm.fireEvent(from, fact, ctx);

        log.info("状态转换：{} --({})--> {}, conversationId={}, durationMs={}",
                from, fact, to,
                ctx.conversation().conversationId(),
                System.currentTimeMillis() - start);
        return to;
    } finally {
        TraceMdcHelper.clear();
    }
}
```

### 3.4 审计日志模式

每次状态转换都应产生一个审计日志条目，包含：
- 业务 ID（conversationId / interactionId）
- 源状态 / 目标状态
- 事件 / Fact
- 市场
- 追踪 ID
- 持续时间
- 时间戳

---

## 4. 幂等性

### 问题

在分布式系统中，事件可能被多次传递（至少一次传递）。状态机应该优雅地处理重复事件，而不会破坏状态。

### 解决方案

使用 `conversationId + event` 作为幂等键，并在触发前检查。

```java
public class IdempotentStateMachineService {
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();
    private final ChatEngineStateMachineService delegate;

    public ConversationState fire(CbolStateContext ctx, ConversationFact event) {
        String idempotencyKey = ctx.conversation().conversationId() + ":" + event;

        if (processedEvents.contains(idempotencyKey)) {
            log.warn("检测到重复事件：{}", idempotencyKey);
            return ctx.conversation().state(); // 返回当前状态，无操作
        }

        processedEvents.add(idempotencyKey);
        return delegate.fire(ctx, event);
    }
}
```

### COLA 级别的幂等性

COLA StateMachine 本身在以下意义上是幂等的：
- 如果没有转换匹配 `(sourceState, event)`，它会抛出 `StateMachineException`
- 异常时状态不变（action-first 原则）
- 从同一状态重复触发同一事件将要么重复成功（如果动作是幂等的），要么一致地失败

**最佳实践**：使你的 Action 实现幂等。使用数据库唯一约束或乐观锁来防止重复副作用。

---

## 5. PlantUML 图生成

### 问题

状态机可能因许多状态和转换而变得复杂。可视化文档有助于开发人员理解流程。

### 解决方案

COLA StateMachine 有一个内置的 `generatePlantUML()` 方法，可以生成 PlantUML 状态图。

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        ConversationStateMachineFactory.create();

String plantUml = sm.generatePlantUML();
System.out.println(plantUml);
```

### 输出示例

```
@startuml
[*] --> NEW
NEW --> INITIATED : CONVERSATION_INITIATED
INITIATED --> IN_PROGRESS : CUSTOMER_CONNECT
IN_PROGRESS --> TRANSFERRED : TRANSFER_REQUEST
IN_PROGRESS --> ENDING : CUSTOMER_CLOSE
TRANSFERRED --> IN_PROGRESS : TRANSFER_FAILED
TRANSFERRED --> ENDING : TRANSFER_COMPLETE
ENDING --> CLOSED : SYS_ENDING_GRACE_TIMEOUT
@enduml
```

### 渲染

使用任何 PlantUML 渲染器：
- 在线：https://www.plantuml.com/plantuml/
- VS Code：PlantUML 扩展
- IntelliJ：PlantUML 集成插件

### CI/CD 集成

```bash
# 在 CI 中生成 PlantUML 并渲染为 PNG
java -jar plantuml.jar -tpng state-machine.puml
```

---

## 6. 工厂缓存模式

### 问题

COLA StateMachine 不允许使用相同 ID 重新构建状态机。尝试构建两次会抛出：
```
The state machine with id [conversation] is already built, no need to build again
```

### 解决方案

使用带有双重检查锁定的工厂缓存模式。

```java
public class ConversationStateMachineFactory {
    public static final String MACHINE_ID = "conversation";
    private static volatile StateMachine<ConversationState, ConversationFact, CbolStateContext> instance;

    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
        // 快速路径：检查是否已构建
        try {
            StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                    StateMachineFactory.get(MACHINE_ID);
            if (existing != null) {
                return existing;
            }
        } catch (Exception ignored) {
            // 尚未构建
        }

        // 慢速路径：同步构建
        synchronized (ConversationStateMachineFactory.class) {
            // 双重检查
            try {
                StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                        StateMachineFactory.get(MACHINE_ID);
                if (existing != null) {
                    return existing;
                }
            } catch (Exception ignored) {
                // 尚未构建
            }

            // 构建
            StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                    StateMachineBuilderFactory.create();

            // ... 定义转换 ...

            StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
                    builder.build(MACHINE_ID);
            StateMachineFactory.register(sm);
            return sm;
        }
    }
}
```

### 测试隔离

对于需要全新状态机的测试，每个测试类使用唯一的机器 ID：

```java
class MyTest {
    private static final String TEST_MACHINE_ID = "conversation-test-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // 使用唯一 ID 构建
    }
}
```

---

## 7. Action-First 原则和错误处理

### 问题

当 Action 在状态转换期间抛出异常时会发生什么？

### 解决方案

COLA StateMachine 遵循 **action-first 原则**：
1. Action 在状态变更**之前**执行
2. 如果动作成功 → 状态变更为目标状态
3. 如果动作失败 → 抛出 `StateMachineException`，状态保持不变

```java
try {
    ConversationState newState = sm.fireEvent(
            ConversationState.IN_PROGRESS,
            ConversationFact.CUSTOMER_CLOSE,
            ctx);
    // 状态成功变更
} catch (StateMachineException e) {
    // 动作失败或没有匹配的转换
    // 状态保持 IN_PROGRESS
    log.error("转换失败", e);

    // 业务层可以决定：重试、故障转移或告警
    handleFailure(ctx, e);
}
```

### 故障转移模式（业务层）

虽然 COLA 没有内置的故障转移状态机，但业务层可以实现一个：

```java
public ConversationState fireWithFailover(CbolStateContext ctx, ConversationFact event) {
    try {
        return sm.fireEvent(ctx.conversation().state(), event, ctx);
    } catch (StateMachineException e) {
        log.warn("主转换失败，尝试故障转移：{}", e.getMessage());

        // 尝试故障转移事件（例如，SYSTEM_ERROR）
        try {
            return sm.fireEvent(ctx.conversation().state(), ConversationFact.SYSTEM_ERROR, ctx);
        } catch (StateMachineException e2) {
            log.error("故障转移也失败了", e2);
            throw e2;
        }
    }
}
```

---

## 8. 扩展模式

### 8.1 自定义 Action 组合

将多个动作组合成一个：

```java
public class CompositeAction<S, E, C> implements Action<S, E, C> {
    private final List<Action<S, E, C>> actions;

    @Override
    public void execute(S from, S to, E event, C context) {
        for (Action<S, E, C> action : actions) {
            action.execute(from, to, event, context);
        }
    }
}
```

### 8.2 带重试的 Action

用重试逻辑包装一个动作：

```java
public class RetryAction<S, E, C> implements Action<S, E, C> {
    private final Action<S, E, C> delegate;
    private final int maxRetries;
    private final long backoffMs;

    @Override
    public void execute(S from, S to, E event, C context) {
        int attempts = 0;
        while (true) {
            try {
                delegate.execute(from, to, event, context);
                return;
            } catch (Exception e) {
                if (++attempts >= maxRetries) {
                    throw e;
                }
                try {
                    Thread.sleep(backoffMs * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
```

### 8.3 异步 Action Worker（预留）

`ActionWorker` 是预留的工具类，供未来异步动作执行使用。当前设计使用同步的 action-first 转换。

```java
// 预留：供未来异步动作执行使用
public class ActionWorker {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public CompletableFuture<Void> submit(
            Action<ConversationState, ConversationFact, CbolStateContext> action,
            ConversationState from, ConversationState to,
            ConversationFact event, CbolStateContext ctx) {
        return CompletableFuture.runAsync(() -> {
            TraceMdcHelper.set(ctx.traceContext());
            try {
                action.execute(from, to, event, ctx);
            } finally {
                TraceMdcHelper.clear();
            }
        }, executor);
    }
}
```

---

## 9. 总结表

| 功能 | 实现 | 位置 |
|------|------|------|
| 多市场配置 | `StateMachineMarketConfig` record | chat-engine/config |
| 客户空闲监控器 | `CustomerIdleMonitor` | chat-engine/monitor |
| 转接监控器 | `TransferMonitor` | chat-engine/monitor |
| 结束宽限监控器 | `EndingGraceMonitor` | chat-engine/monitor |
| 追踪上下文 | `TraceContext` + `TraceMdcHelper` | chat-engine/context |
| 幂等性 | 业务层模式 | 应用代码 |
| PlantUML 生成 | COLA 内置 `generatePlantUML()` | statemachine-core |
| 工厂缓存 | 双重检查锁定模式 | chat-engine/statemachine/factory |
| Action-first 错误处理 | COLA 内置 | statemachine-core |
| 故障转移 | 业务层模式 | 应用代码 |
| 异步 Action Worker | 预留工具类 | chat-engine/action |

---

## 10. 参考资料

- 阿里巴巴 COLA GitHub：https://github.com/alibaba/COLA
- COLA StateMachine 模块：`cola-components/cola-component-statemachine`
- COLA StateMachine 测试：`cola-components/cola-component-statemachine/src/test/java/com/alibaba/cola/test/`

---

*最后更新：2026-09-05（v3.0 — 为阿里巴巴 COLA StateMachine 重写）*
