# 使用指南

> 版本：2.1 | 最后更新：2026-09-03

## 1. 快速开始

### 1.1 项目结构

这是一个多模块 Maven 项目，包含三个模块：

| 模块 | ArtifactId | 包名 | 职责 |
|------|-----------|------|------|
| **statemachine-core** | `statemachine-core` | `com.selfdevelopment.statemachine` | 通用状态机引擎 + 高级特性 |
| **chat-engine** | `chat-engine` | `com.selfdevelopment.chatengine` | 会话状态机（业务层） |
| **agent-connector** | `agent-connector` | `com.selfdevelopment.agentconnector` | 交互状态机（通道层） |

### 1.2 添加依赖

将相应的模块添加到你的 `pom.xml`：

```xml
<!-- 核心状态机引擎（始终需要） -->
<dependency>
    <groupId>com.selfdevelopment</groupId>
    <artifactId>statemachine-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 会话状态机（chat-engine） -->
<dependency>
    <groupId>com.selfdevelopment</groupId>
    <artifactId>chat-engine</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 交互状态机（agent-connector） -->
<dependency>
    <groupId>com.selfdevelopment</groupId>
    <artifactId>agent-connector</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 1.2 构建并注册状态机

```java
import com.selfdevelopment.chatengine.statemachine.ConversationStateMachineFactory;
import com.selfdevelopment.statemachine.core.StateMachine;

// 构建并注册（在应用启动时调用一次）
StateMachine<ConversationState, ConversationFact, CbolStateContext> machine =
    ConversationStateMachineFactory.build();
```

### 1.3 触发事件

```java
import com.selfdevelopment.chatengine.statemachine.ChatEngineStateMachineService;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.statemachine.core.StateContext;

// 1. 构建上下文
CbolStateContext ctx = CbolStateContext.builder()
    .conversation(ConversationInstance.builder()
        .conversationId("conv-001")
        .state(ConversationState.INITIATED)
        .market("HK")
        .lastActivityTs(System.currentTimeMillis())
        .build())
    .marketConfig(StateMachineMarketConfig.defaultConfig())
    .traceContext(TraceContext.generate())
    .build();

// 2. 触发事件
ChatEngineStateMachineService service = new ChatEngineStateMachineService();
StateContext<ConversationState, ConversationFact, CbolStateContext> result =
    service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);

// 3. 使用结果
System.out.println("New state: " + result.getTargetState());  // IN_PROGRESS
```

## 2. 构建自定义状态机

### 2.1 使用 Builder DSL

```java
StateMachine<OrderState, OrderEvent, OrderContext> machine =
    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>builder("order-machine")
        .initialState(OrderState.CREATED)
        .endStates(OrderState.COMPLETED, OrderState.CANCELLED)

        // 带 entry/exit 动作的状态
        .stateWithEntry(OrderState.PAID, ctx -> sendEmail(ctx))
        .stateWithExit(OrderState.PAID, ctx -> logExit(ctx))

        // 带 guard 和 action 的迁移
        .transition()
            .from(OrderState.CREATED)
            .on(OrderEvent.PAY)
            .to(OrderState.PAID)
            .guard(ctx -> ctx.getBusinessContext().isPaymentValid())
            .perform(ctx -> processPayment(ctx))
        .and()

        // 内部迁移（状态不改变）
        .transition()
            .from(OrderState.PAID)
            .on(OrderEvent.UPDATE_ADDRESS)
            .to(OrderState.PAID)
            .internal()
            .perform(ctx -> updateAddress(ctx))
        .and()

        .build();
```

### 2.2 使用 Configurer 适配器（Spring 风格）

```java
public class OrderStateMachineConfig
        extends StateMachineConfigurerAdapter<OrderState, OrderEvent, OrderContext> {

    @Override
    public void configure(StateConfigurer<OrderState, OrderEvent, OrderContext> states) {
        states.initial(OrderState.CREATED)
              .state(OrderState.PAID)
              .state(OrderState.SHIPPED)
              .end(OrderState.COMPLETED)
              .end(OrderState.CANCELLED);
    }

    @Override
    public void configure(TransitionConfigurer<OrderState, OrderEvent, OrderContext> transitions) {
        transitions.withExternal()
            .source(OrderState.CREATED)
            .event(OrderEvent.PAY)
            .target(OrderState.PAID)
            .guard(ctx -> ctx.getBusinessContext().isPaymentValid())
            .action(ctx -> processPayment(ctx))
        .and().withExternal()
            .source(OrderState.PAID)
            .event(OrderEvent.SHIP)
            .target(OrderState.SHIPPED)
        .and().withExternal()
            .source(OrderState.SHIPPED)
            .event(OrderEvent.DELIVER)
            .target(OrderState.COMPLETED);
    }
}

// 使用
StateMachine<OrderState, OrderEvent, OrderContext> machine =
    StateMachineBuilder.fromConfigurer("order-machine", new OrderStateMachineConfig());
```

## 3. 使用监听器

### 3.1 审计日志监听器

```java
machine.addListener(new StateMachineListener<OrderState, OrderEvent, OrderContext>() {
    @Override
    public void stateChanged(StateContext<OrderState, OrderEvent, OrderContext> ctx) {
        log.info("Order {}: {} -> {} (event={})",
            ctx.getBusinessContext().getOrderId(),
            ctx.getSourceState(),
            ctx.getTargetState(),
            ctx.getEvent());
    }

    @Override
    public void transitionError(StateContext<OrderState, OrderEvent, OrderContext> ctx) {
        log.error("Transition error: {} -> {} on {}",
            ctx.getSourceState(), ctx.getTargetState(), ctx.getEvent(),
            ctx.getException());
    }
});
```

### 3.2 指标监听器

```java
machine.addListener(new StateMachineListener<>() {
    @Override
    public void transitionEnded(Transition<...> t, StateContext<...> ctx) {
        metrics.increment("statemachine.transition.success",
            Tags.of("machine", ctx.getMachineId()));
    }

    @Override
    public void transitionDenied(StateContext<...> ctx, String reason) {
        metrics.increment("statemachine.transition.denied",
            Tags.of("reason", reason));
    }
});
```

## 4. 使用 ExtendedState

```java
// 为会话创建扩展状态
ExtendedState ext = new ExtendedState();
ext.set("retryCount", 0);
ext.set("lastError", null);

// 触发第一个事件
StateContext<...> result1 = machine.fireEvent(
    OrderState.CREATED, OrderEvent.PAY, context, ext);

// 迁移后读取扩展状态
int retryCount = result1.getExtendedState().get("retryCount", Integer.class);

// 在 guard 中使用
.guard(ctx -> {
    Integer retries = ctx.getExtendedState().get("retryCount", Integer.class);
    return retries != null && retries < 3;
})
```

## 5. 多市场配置

### 5.1 使用 InMemoryProvider

```java
MarketConfigProvider.InMemoryProvider provider = new MarketConfigProvider.InMemoryProvider();

// 配置每市场设置
provider.put("HK", StateMachineMarketConfig.builder()
    .customerIdleSeconds(180)      // HK 市场 3 分钟
    .transferTimeoutSeconds(120)    // 2 分钟
    .endingGraceSeconds(60)          // 1 分钟
    .surveyEnabled(true)
    .build());

provider.put("SG", StateMachineMarketConfig.builder()
    .customerIdleSeconds(300)
    .transferTimeoutSeconds(180)
    .build());

// 获取市场配置（未知市场回退到默认）
StateMachineMarketConfig config = provider.getConfig("HK");
```

### 5.2 构建带市场配置的上下文

```java
CbolStateContext ctx = CbolStateContext.builder()
    .conversation(conversation)
    .marketConfig(marketConfigProvider.getConfig(conversation.market()))
    .traceContext(TraceContext.generate())
    .build();
```

## 6. 使用监控器

### 6.1 设置监控器

```java
ChatEngineStateMachineService service = new ChatEngineStateMachineService();

CustomerIdleMonitor idleMonitor = new CustomerIdleMonitor(service);
TransferMonitor transferMonitor = new TransferMonitor(service);
EndingGraceMonitor endingMonitor = new EndingGraceMonitor(service);
```

### 6.2 定时监控器执行

```java
@Scheduled(fixedDelay = 30000)  // 每 30 秒
public void checkCustomerIdle() {
    List<Conversation> activeConversations = repository.findActiveConversations();
    for (Conversation conv : activeConversations) {
        CbolStateContext ctx = buildContext(conv);
        idleMonitor.check(ctx, conv.getLastActivityTs());
    }
}

@Scheduled(fixedDelay = 15000)  // 每 15 秒
public void checkTransferTimeout() {
    List<Conversation> transferring = repository.findTransferringConversations();
    for (Conversation conv : transferring) {
        CbolStateContext ctx = buildContext(conv);
        transferMonitor.check(ctx, conv.getTransferStartTs());
    }
}
```

## 7. 异步动作执行

### 7.1 使用 ActionWorker

```java
ActionWorker worker = new ActionWorker();  // 默认：core=CPU, max=CPU*2, queue=1000

// 或使用自定义配置
ActionWorker customWorker = new ActionWorker(4, 8, 60, 500);

// 提交异步动作
CbolAction sendNotification = ctx -> {
    notificationService.send(ctx.conversation().customerId(), "Your conversation is IN_PROGRESS");
};

worker.submit(sendNotification, ctx);

// 应用退出时关闭
worker.shutdown();
```

### 7.2 CbolAction 接口

```java
@FunctionalInterface
public interface CbolAction {
    void execute(CbolStateContext ctx);
}
```

## 8. 错误处理模式

### 8.1 处理 StateMachineException

```java
try {
    StateContext<...> result = service.fire(ctx, ConversationFact.TRANSFER_REQUEST);
    // 更新会话状态
    conversation.setState(result.getTargetState());
    repository.save(conversation);
} catch (StateMachineException e) {
    String message = e.getMessage();
    if (message.contains("No transition found")) {
        // 当前状态的无效事件
        return ResponseEntity.badRequest().body("Invalid action");
    } else if (message.contains("guard condition failed")) {
        // 业务条件不满足
        return ResponseEntity.status(409).body("Transfer not available");
    } else if (message.contains("action failed")) {
        // 动作执行错误
        log.error("Action failed", e);
        return ResponseEntity.status(500).body("Processing error");
    }
    throw e;
}
```

### 8.2 触发前检查

```java
// 检查迁移是否存在（不评估 guard）
if (machine.hasTransition(conversation.getState(), event)) {
    // 继续
}

// 检查事件是否可触发（包括 guard 评估）
if (machine.canFire(conversation.getState(), event, context)) {
    machine.fireEvent(conversation.getState(), event, context);
}
```

## 9. 测试

### 9.1 单元测试示例

```java
@Test
void shouldTransitionFromInitiatedToActiveOnCustomerConnect() {
    // Given
    ConversationStateMachineFactory.build();
    ChatEngineStateMachineService service = new ChatEngineStateMachineService();
    CbolStateContext ctx = buildTestContext(ConversationState.INITIATED);

    // When
    StateContext<ConversationState, ConversationFact, CbolStateContext> result =
        service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);

    // Then
    assertEquals(ConversationState.IN_PROGRESS, result.getTargetState());
    assertTrue(result.isTransitionAccepted());
}

@Test
void shouldThrowWhenNoTransitionExists() {
    ChatEngineStateMachineService service = new ChatEngineStateMachineService();
    CbolStateContext ctx = buildTestContext(ConversationState.CLOSED);

    assertThrows(StateMachineException.class,
        () -> service.fire(ctx, ConversationFact.CUSTOMER_CONNECT));
}
```

### 9.2 测试隔离

```java
@AfterEach
void tearDown() {
    StateMachineRegistry.getInstance().clear();  // 测试间清空注册表
}
```

## 10. 最佳实践

### 10.1 应该做

- **应该**在调用方（仓库/服务层）管理状态持久化
- **应该**在 CbolStateContext 中将市场配置捕获为快照
- **应该**在每个请求的入口点使用 TraceContext.generate()
- **应该**用 try-finally 包裹 TraceMdcHelper.set() 并调用 clear()
- **应该**使用有界线程池（ActionWorker 默认是安全的）
- **应该**在应用启动时注册一次状态机
- **应该**使用监听器处理横切关注点（日志、指标、审计）
- **应该**尽可能保持动作幂等

### 10.2 不应该做

- **不应该**在状态机引擎中存储当前状态（设计上是无状态的）
- **不应该**使用 `Executors.newCachedThreadPool()`（无界，有 OOM 风险）
- **不应该**忘记在 finally 块中调用 `TraceMdcHelper.clear()`
- **不应该**从动作中抛出受检异常（包装为 RuntimeException）
- **不应该**在迁移过程中修改 CbolStateContext（它是不可变 record）
- **不应该**重复注册相同的机器 ID（会抛出 StateMachineException）
- **不应该**依赖 entry/exit 动作失败来阻断迁移（它们是尽力执行的）

## 11. 与 Spring Boot 集成

### 11.1 配置类

```java
@Configuration
public class StateMachineConfig {

    @Bean
    public StateMachine<ConversationState, ConversationFact, CbolStateContext> conversationStateMachine() {
        return ConversationStateMachineFactory.build();
    }

    @Bean
    public ChatEngineStateMachineService ChatEngineStateMachineService() {
        return new ChatEngineStateMachineService();
    }

    @Bean
    public ActionWorker actionWorker() {
        return new ActionWorker();
    }

    @Bean
    public MarketConfigProvider marketConfigProvider() {
        MarketConfigProvider.InMemoryProvider provider =
            new MarketConfigProvider.InMemoryProvider();
        // 从配置中心 / Redis 加载
        return provider;
    }

    @PreDestroy
    public void shutdown() {
        actionWorker().shutdown();
    }
}
```

## 12. 高级特性

### 12.1 构建时校验

在构建时校验状态机配置以尽早发现错误：

```java
// 构建期间校验（ERROR 级别问题时抛出）
StateMachine<OrderState, OrderEvent, OrderContext> machine =
    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>builder("order")
        .initialState(OrderState.CREATED)
        .transition()
            .from(OrderState.CREATED).on(OrderEvent.PAY).to(OrderState.PAID)
        .and()
        .build(true);  // validate=true

// 或单独校验以获取所有错误
List<ValidationError> errors = StateMachineValidator.validate(machine);
errors.forEach(e -> System.out.println(e.level() + ": " + e.message()));
```

校验规则：`NO_TRANSITIONS`、`INITIAL_STATE_DEFINED`、`INITIAL_STATE_REACHABLE`、`END_STATE_NO_OUTGOING`、`UNREACHABLE_STATE`、`DEAD_END_STATE`、`INTERNAL_TRANSITION_MATCH`、`DUPLICATE_TRANSITION_NO_GUARD`。

### 12.2 乐观锁持久化

使用内置的仓库模式和基于版本的乐观锁：

```java
StateRepository<ConversationState> repository = new InMemoryStateRepository<>();

// 保存初始状态
repository.save("conv-123", ConversationState.INITIATED, 0);

// 加载并使用乐观锁迁移
ChatEngineStateMachineService service = new ChatEngineStateMachineService(machine, repository);
StateContext<...> result = service.fireWithLock("conv-123", ConversationFact.USER_MESSAGE, ctx);
// 版本冲突时自动重试最多 3 次
```

生产环境中，使用 JDBC/MongoDB 实现 `StateRepository`：

```java
public class JdbcStateRepository<S> implements StateRepository<S> {
    // SELECT state, version FROM conversations WHERE id = ?
    // UPDATE conversations SET state = ?, version = version + 1 WHERE id = ? AND version = ?
}
```

### 12.3 幂等事件处理

通过事件 ID 去重防止重复事件处理：

```java
ProcessedEventStore store = new InMemoryProcessedEventStore();
IdempotentStateMachineDecorator<OrderState, OrderEvent, OrderContext> idempotent =
    new IdempotentStateMachineDecorator<>(machine, store);

// 第一次调用：处理事件
StateContext<...> result1 = idempotent.fireEventWithId(
    "evt-001", OrderState.CREATED, OrderEvent.PAY, ctx);

// 相同 ID 的第二次调用：返回缓存结果，不重新处理
StateContext<...> result2 = idempotent.fireEventWithId(
    "evt-001", OrderState.CREATED, OrderEvent.PAY, ctx);
```

### 12.4 Micrometer 指标

使用 Micrometer 指标自动检测状态机：

```java
MeterRegistry registry = new SimpleMeterRegistry();  // 或 Spring 自动配置的 registry
StateMachine<OrderState, OrderEvent, OrderContext> monitored =
    new MonitoredStateMachine<>(machine, registry);

// 所有 fireEvent 调用自动被检测
monitored.fireEvent(OrderState.CREATED, OrderEvent.PAY, ctx);

// 可用指标：
// - statemachine.transition.duration (Timer)
// - statemachine.transition.success (Counter)
// - statemachine.transition.error (Counter)
// - statemachine.transition.denied (Counter)
// - statemachine.event.received (Counter)
```

### 12.5 事件溯源 / 审计追踪

自动记录所有迁移以用于审计和重放：

```java
StateTransitionStore<ConversationState, ConversationFact> store =
    new InMemoryStateTransitionStore<>();

StateMachine<ConversationState, ConversationFact, CbolStateContext> eventSourced =
    new EventSourcedStateMachine<>(machine, store, "conv-123");

// 所有迁移自动被记录
eventSourced.fireEvent(ConversationState.INITIATED, ConversationFact.USER_MESSAGE, ctx);

// 重放完整历史
List<StateTransitionEvent<...>> history = store.replay("conv-123");

// 重建当前状态
Optional<ConversationState> current = store.reconstructState("conv-123");

// 时间旅行查询
List<...> stateAtTime = store.replayUpTo("conv-123", Instant.parse("2026-01-01T10:00:00Z"));
```

### 12.6 弹性 / 失败处理

根据用例选择失败处理策略：

```java
// 1. 失败时抛出（默认）
StateMachine<...> resilient = new ResilientStateMachine<>(machine, new ThrowFailureHandler<>());

// 2. 返回源状态（无异常，检查返回值）
StateMachine<...> resilient = new ResilientStateMachine<>(machine, new ReturnSourceFailureHandler<>());
StateContext<...> result = resilient.fireEvent(state, event, ctx);
if (!result.isTransitionAccepted()) {
    // 处理拒绝
}

// 3. 回退到 ERROR 状态
StateMachine<...> resilient = new ResilientStateMachine<>(machine,
    new FallbackStateFailureHandler<>(OrderState.ERROR));

// 4. 指数退避重试，然后回退
FailureHandler<...> fallback = new FallbackStateFailureHandler<>(OrderState.ERROR);
RetryFailureHandler<...> retry = RetryFailureHandler.exponentialBackoff(
    3, fallback, 100, 5000);
StateMachine<...> resilient = new ResilientStateMachine<>(machine, retry);
```

### 12.7 超时事件 / 定时迁移

当实体在某个状态停留过久时自动触发事件：

```java
// 配置超时
Map<ConversationState, TimeoutConfig<ConversationState, ConversationFact>> timeouts = Map.of(
    ConversationState.IN_PROGRESS, TimeoutConfig.<ConversationState, ConversationFact>builder()
        .state(ConversationState.IN_PROGRESS)
        .timeoutEvent(ConversationFact.IDLE_TIMEOUT)
        .duration(30).timeUnit(TimeUnit.SECONDS).build(),
    ConversationState.TRANSFERRING, TimeoutConfig.<ConversationState, ConversationFact>builder()
        .state(ConversationState.TRANSFERRING)
        .timeoutEvent(ConversationFact.TRANSFER_TIMEOUT)
        .duration(60).timeUnit(TimeUnit.SECONDS).build()
);

// 创建调度器
StateMachineTimeoutScheduler<ConversationState, ConversationFact> scheduler =
    new InMemoryTimeoutScheduler<>("conversation-timeout", 4);

// 包装状态机
StateMachine<ConversationState, ConversationFact, CbolStateContext> timeoutAware =
    new TimeoutAwareStateMachine<>(machine, scheduler, timeouts, "conv-123");

// 进入 IN_PROGRESS 自动启动 30 秒计时器
timeoutAware.fireEvent(ConversationState.INITIATED, ConversationFact.USER_MESSAGE, ctx);

// 离开 IN_PROGRESS 自动取消计时器
timeoutAware.fireEvent(ConversationState.IN_PROGRESS, ConversationFact.AGENT_JOIN, ctx);

// 查询超时状态
boolean IN_PROGRESS = timeoutAware.isTimeoutActive();
long remainingMs = timeoutAware.getRemainingTimeoutMs();
timeoutAware.cancelTimeout();  // 手动取消
```

这取代了对外部 Monitor 类（CustomerIdleMonitor、TransferMonitor、EndingGraceMonitor）的需求。

### 12.8 图表生成

直接从状态机配置生成文档图表：

```java
// Mermaid（用于 GitHub / Markdown）
String mermaid = StateMachineDiagramGenerator.toMermaid(machine);

// PlantUML（用于 Confluence / 企业文档）
String plantUml = StateMachineDiagramGenerator.toPlantUML(machine);

// 迁移表（Markdown）
String table = StateMachineDiagramGenerator.toTransitionTable(machine);

// 写入文件
Files.writeString(Path.of("state-diagram.mmd"), mermaid);
Files.writeString(Path.of("state-diagram.puml"), plantUml);
Files.writeString(Path.of("transitions.md"), table);
```

### 12.9 装饰器组合

组合多个装饰器以构建全功能管道：

```java
StateMachine<OrderState, OrderEvent, OrderContext> pipeline =
    new TimeoutAwareStateMachine<>(          // 1. 最外层：超时管理
        new ResilientStateMachine<>(         // 2. 失败处理
            new EventSourcedStateMachine<>(  // 3. 审计追踪
                new MonitoredStateMachine<>( // 4. 指标
                    new IdempotentStateMachineDecorator<>( // 5. 最内层：去重
                        machine,
                        eventStore
                    ),
                    meterRegistry
                ),
                transitionStore,
                "order-123"
            ),
            new ThrowFailureHandler<>()
        ),
        timeoutScheduler,
        timeoutConfigs,
        "order-123"
    );
```

**推荐顺序（从最外层到最内层）：** TimeoutAware → Resilient → EventSourced → Monitored → Idempotent → SimpleStateMachine

---

*有关每个高级特性的详细设计，请参见 [05-Advanced-Features.md](./05-Advanced-Features.md)。*
