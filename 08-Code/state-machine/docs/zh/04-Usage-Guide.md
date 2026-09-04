# 使用指南

> 版本：3.0 | 最后更新：2026-09-05
> 基于阿里巴巴 COLA StateMachine：https://github.com/alibaba/COLA

## 1. 快速开始

### 1.1 项目结构

这是一个包含三个模块的多模块 Maven 项目：

| 模块 | ArtifactId | 包名 | 职责 |
|------|-----------|------|------|
| **statemachine-core** | `statemachine-core` | `com.alibaba.cola.statemachine` | 阿里巴巴 COLA StateMachine 核心引擎 |
| **chat-engine** | `chat-engine` | `com.selfdevelopment.chatengine` | 会话状态机（业务层） |
| **agent-connector** | `agent-connector` | `com.selfdevelopment.agentconnector` | 交互状态机（通道层） |

### 1.2 添加依赖

在 `pom.xml` 中添加相应的模块：

```xml
<!-- 核心状态机引擎（阿里巴巴 COLA StateMachine，必需） -->
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

### 1.3 构建和注册状态机

```java
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import com.alibaba.cola.statemachine.StateMachine;

// 构建和注册（在应用启动时调用一次）
// 工厂使用缓存模式防止重复构建
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        ConversationStateMachineFactory.build();
```

### 1.4 触发事件

```java
// 构建上下文
CbolStateContext ctx = CbolStateContext.builder()
        .conversation(conversation)
        .marketConfig(StateMachineMarketConfig.defaultConfig())
        .traceContext(TraceContext.generate())
        .build();

// 触发事件并获取新状态（COLA API 直接返回目标状态）
ConversationState newState = sm.fireEvent(
        ConversationState.NEW,
        ConversationFact.CONVERSATION_INITIATED,
        ctx);

// 更新会话状态
conversation.setState(newState);
```

## 2. COLA Builder DSL

### 2.1 外部转换

定义外部状态转换（状态会改变）：

```java
StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
        StateMachineBuilderFactory.create();

builder.externalTransition()
        .from(ConversationState.NEW)
        .to(ConversationState.INITIATED)
        .on(ConversationFact.CONVERSATION_INITIATED)
        .when(ctx -> ctx.getMarketConfig() != null)  // 可选守卫
        .perform(new ConversationInitAction());
```

**Builder API 顺序：** `from() → to() → on() → when() → perform()`

### 2.2 内部转换

定义内部转换（状态不变，但动作执行）：

```java
builder.internalTransition()
        .within(ConversationState.IN_PROGRESS)
        .on(ConversationFact.SURVEY_START)
        .perform(new SurveyStartAction());
```

### 2.3 构建和注册

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        builder.build("conversation");
StateMachineFactory.register(sm);
```

### 2.4 获取状态机

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        StateMachineFactory.get("conversation");
```

## 3. Action 接口

### 3.1 实现 Action

```java
import com.alibaba.cola.statemachine.Action;

public class CustomerConnectAction
        implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to,
                        ConversationFact event, CbolStateContext ctx) {
        // 你的业务逻辑
        log.info("客户已连接：conversationId={}",
                ctx.getConversation().getConversationId());
    }
}
```

### 3.2 Action-First 原则

动作在状态变更**之前**执行。如果动作失败，状态不变：

```java
try {
    ConversationState newState = sm.fireEvent(
            ConversationState.INITIATED,
            ConversationFact.CUSTOMER_CONNECT,
            ctx);
    // 状态成功变更
} catch (StateMachineException e) {
    // 动作失败或没有匹配的转换
    // 状态保持 INITIATED
    log.error("转换失败", e);
}
```

## 4. Condition（守卫）接口

### 4.1 实现 Condition

```java
import com.alibaba.cola.statemachine.Condition;

public class SurveyEnabledCondition implements Condition<CbolStateContext> {

    @Override
    public boolean isSatisfied(CbolStateContext ctx) {
        return ctx.getMarketConfig().isSurveyEnabled();
    }
}
```

### 4.2 在转换中使用 Condition

```java
builder.externalTransition()
        .from(ConversationState.IN_PROGRESS)
        .to(ConversationState.ENDING)
        .on(ConversationFact.SURVEY_COMPLETE)
        .when(ctx -> ctx.getMarketConfig().isSurveyEnabled())
        .perform(new SurveyCompleteAction());
```

## 5. Chat Engine 使用

### 5.1 会话状态

```java
public enum ConversationState {
    NEW,                // 初始状态，会话记录已创建但未初始化
    INITIATED,          // 会话已初始化，等待客户连接
    IN_PROGRESS,        // 客户已连接，正在处理（包括问卷作为子阶段）
    TRANSFERRED,        // 正在转接人工客服
    ENDING,             // 会话结束，清理宽限期
    ERROR,              // 动作失败，故障转移状态
    CLOSED              // 终止状态
}
```

### 5.2 使用 ChatEngineStateMachineService

```java
// 初始化（在启动时调用一次）
ConversationStateMachineFactory.build();
ChatEngineStateMachineService service = new ChatEngineStateMachineService();

// 构建上下文
CbolStateContext ctx = buildContext();

// 触发事件（直接返回 ConversationState）
ConversationState newState = service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);
```

### 5.3 运行 Chat Engine Demo

```bash
cd 08-Code/state-machine
mvnw.cmd compile -pl chat-engine
java -cp chat-engine/target/classes:statemachine-core/target/classes com.selfdevelopment.chatengine.demo.ChatEngineDemo
```

## 6. Agent Connector 使用

### 6.1 交互状态

```java
public enum InteractionState {
    CONNECTING,     // 正在建立连接
    CONNECTED,      // 活跃连接
    RECONNECTING,   // 正在重连
    HELD,           // 连接保持
    TRANSFERRING,   // 正在通道转接
    DISCONNECTED    // 终止状态，连接已关闭
}
```

### 6.2 使用 AgentConnectorStateMachineService

```java
// 初始化（在启动时调用一次）
InteractionStateMachineFactory.create();
AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

// 构建上下文
AgentConnectorStateContext ctx = buildContext();

// 触发事件（直接返回 InteractionState）
InteractionState newState = service.fire(ctx, InteractionFact.CONNECTION_ESTABLISHED);
```

### 6.3 运行 Agent Connector Demo

```bash
cd 08-Code/state-machine
mvnw.cmd compile -pl agent-connector
java -cp agent-connector/target/classes:statemachine-core/target/classes com.selfdevelopment.agentconnector.demo.AgentConnectorDemo
```

## 7. 多市场配置

### 7.1 默认配置

```java
StateMachineMarketConfig config = StateMachineMarketConfig.defaultConfig();
// customerIdleSeconds=300, transferTimeoutSeconds=120, endingGraceSeconds=30
// surveyEnabled=true, transferEnabled=true, genesysEnabled=true
```

### 7.2 自定义配置

```java
StateMachineMarketConfig config = StateMachineMarketConfig.builder()
        .customerIdleSeconds(600)
        .transferTimeoutSeconds(200)
        .endingGraceSeconds(60)
        .surveyEnabled(true)
        .transferEnabled(true)
        .genesysEnabled(false)
        .fallbackRoutingStrategy("DROP")
        .build();
```

### 7.3 市场配置提供者

```java
MarketConfigProvider.InMemoryProvider provider = new MarketConfigProvider.InMemoryProvider();
provider.put("HK", customConfig);

StateMachineMarketConfig cfg = provider.getConfig("HK");
```

## 8. 监控器

### 8.1 客户空闲监控器

```java
CustomerIdleMonitor monitor = new CustomerIdleMonitor(service);
long lastActivity = System.currentTimeMillis() - 400 * 1000; // 400秒前
monitor.check(ctx, lastActivity);
// 如果空闲时间 > customerIdleSeconds，触发 SYS_CUSTOMER_IDLE
```

### 8.2 转接监控器

```java
TransferMonitor monitor = new TransferMonitor(service);
long transferStart = System.currentTimeMillis() - 200 * 1000; // 200秒前
monitor.check(ctx, transferStart);
// 如果转接时间 > transferTimeoutSeconds，触发 SYS_TRANSFER_TIMEOUT
// 仅在 TRANSFERRED 状态下激活
```

### 8.3 结束宽限监控器

```java
EndingGraceMonitor monitor = new EndingGraceMonitor(service);
long enterEnding = System.currentTimeMillis() - 60 * 1000; // 60秒前
monitor.check(ctx, enterEnding);
// 如果结束时间 > endingGraceSeconds，触发 SYS_ENDING_GRACE_TIMEOUT
// 仅在 ENDING 状态下激活
```

## 9. 追踪上下文

### 9.1 生成追踪上下文

```java
TraceContext traceContext = TraceContext.generate();
// traceId = UUID, timestamp = 当前时间
```

### 9.2 MDC 传播

```java
// 设置 MDC 用于日志
TraceMdcHelper.set(traceContext);
try {
    // 你的代码 - 所有日志将包含 traceId
} finally {
    TraceMdcHelper.clear();
}
```

### 9.3 异步 Action Worker（预留）

```java
// ActionWorker 是预留的工具类，供未来使用
// 当前设计使用同步的 action-first 转换
ActionWorker worker = new ActionWorker();
worker.submit(action, from, to, event, ctx);
```

## 10. PlantUML 图生成

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        ConversationStateMachineFactory.build();

String plantUml = sm.generatePlantUML();
System.out.println(plantUml);
```

输出：
```
@startuml
[*] --> NEW
NEW --> INITIATED : CONVERSATION_INITIATED
INITIATED --> IN_PROGRESS : CUSTOMER_CONNECT
IN_PROGRESS --> TRANSFERRED : TRANSFER_REQUEST
IN_PROGRESS --> ENDING : CUSTOMER_CLOSE
@enduml
```

## 11. 测试

### 11.1 运行所有测试

```bash
cd 08-Code/state-machine
mvnw.cmd clean test
```

### 11.2 运行模块测试

```bash
# Chat engine 测试
mvnw.cmd test -pl chat-engine

# Agent connector 测试
mvnw.cmd test -pl agent-connector

# 核心测试（COLA）
mvnw.cmd test -pl statemachine-core
```

### 11.3 测试覆盖率

- statemachine-core：219 个 COLA 测试
- chat-engine：36 个测试
- agent-connector：（待添加测试）

## 12. 最佳实践

### 12.1 状态机初始化

```java
// 在应用启动时调用一次
@PostConstruct
public void init() {
    ConversationStateMachineFactory.build();
    InteractionStateMachineFactory.create();
}
```

### 12.2 工厂缓存模式

COLA StateMachine 不允许重新构建。使用缓存模式：

```java
public static StateMachine<...> build() {
    try {
        StateMachine<...> existing = StateMachineFactory.get(MACHINE_ID);
        if (existing != null) return existing;
    } catch (Exception ignored) {
        // 尚未构建
    }
    synchronized (Factory.class) {
        // 双重检查 + 构建 + 注册
    }
}
```

### 12.3 错误处理

```java
try {
    ConversationState newState = sm.fireEvent(source, event, ctx);
} catch (StateMachineException e) {
    // 没有匹配的转换或动作失败
    // 状态保持不变
    log.error("状态转换失败：source={}, event={}", source, event, e);
    throw new BusinessException("转换失败", e);
}
```

### 12.4 幂等性

```java
// 使用 conversationId + event 作为幂等键
String idempotencyKey = ctx.getConversation().getConversationId() + ":" + event;
if (processedEvents.contains(idempotencyKey)) {
    return currentState; // 已处理
}
processedEvents.add(idempotencyKey);
```

## 13. Spring Boot 集成

### 13.1 配置类

```java
@Configuration
public class StateMachineConfig {

    @Bean
    public StateMachine<ConversationState, ConversationFact, CbolStateContext> conversationStateMachine() {
        return ConversationStateMachineFactory.build();
    }

    @Bean
    public ChatEngineStateMachineService chatEngineStateMachineService() {
        return new ChatEngineStateMachineService();
    }
}
```

### 13.2 Service 使用

```java
@Service
public class ConversationService {

    @Autowired
    private ChatEngineStateMachineService stateMachineService;

    public Conversation handleEvent(Conversation conversation, ConversationFact event) {
        CbolStateContext ctx = buildContext(conversation);
        ConversationState newState = stateMachineService.fire(ctx, event);
        conversation.setState(newState);
        return conversation;
    }
}
```

## 14. 参考资料

- 阿里巴巴 COLA GitHub：https://github.com/alibaba/COLA
- COLA StateMachine 模块：`cola-components/cola-component-statemachine`
- COLA StateMachine 测试：`cola-components/cola-component-statemachine/src/test/java/com/alibaba/cola/test/`

---

*最后更新：2026-09-05（v3.0 — 迁移到阿里巴巴 COLA StateMachine）*
