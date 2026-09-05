# 状态机架构设计

> 版本：4.0 | 最后更新：2026-09-05
> 基于阿里巴巴 COLA StateMachine：https://github.com/alibaba/COLA
> 对齐事件驱动编排设计（v4.0）

## 1. 概览

本项目为 CBOL（AI 消息中心）系统实现了一个**轻量级、无状态、表驱动的状态机框架**，由**阿里巴巴 COLA StateMachine** 提供支持。该框架针对简洁性、高性能和类型安全进行了优化。

项目组织为**多模块 Maven 项目**，包含三个模块：

| 模块 | 包名 | 职责 |
|------|------|------|
| **statemachine-core** | `com.alibaba.cola.statemachine` | 阿里巴巴 COLA StateMachine 核心引擎：Action、Condition、State、Transition、Builder DSL、StateMachineFactory、PlantUML 生成 |
| **chat-engine** | `com.selfdevelopment.chatengine` | 会话状态机（业务层）：7 个状态（NEW, INITIATED, ACTIVE, IN_PROGRESS, TRANSFERRED, ENDING, CLOSED）、25+ 个事件、13 个动作、多市场配置、监控器、仓库、Demo |
| **agent-connector** | `com.selfdevelopment.agentconnector` | 交互状态机（通道层）：8 个状态（INITIATED, CONNECTED, IN_PROGRESS, DEGRADED, RECONNECTING, CONSULT_TRANSFER, TRANSFERRED, CLOSED）、20+ 个事件、14 个动作、通道连接器、事件归一化器、Demo |

### 模块依赖

```
chat-engine ──► statemachine-core
agent-connector ──► statemachine-core
```

`chat-engine` 和 `agent-connector` 之间**没有直接依赖**。这种分离确保：
- 通道层关注点（连接、保持、转移）与业务层关注点（会话生命周期）隔离
- 每个模块可以独立开发、测试和部署
- 清晰的系统边界：chat-engine 连接 AIBot 和 ChatHistory；agent-connector 连接 Genesys 和 WebSocket

## 2. 设计原则

### 2.1 无状态引擎（强制）

状态机引擎本身**不存储**当前状态。调用者在每次 `fireEvent` 调用时注入当前状态。这种设计：

- 允许单个机器实例服务数千个并发会话
- 消除状态存储的线程安全问题
- 支持无需会话亲和性的水平扩展
- 简化状态持久化（调用者管理 DB/缓存）

```java
// 调用者管理状态；引擎只存储转换规则
ConversationState newState = stateMachine.fireEvent(
    conversation.getCurrentState(),  // 由调用者注入
    ConversationFact.INTERACTION_BECAME_ACTIVE,
    context
);
conversation.setCurrentState(newState);
repository.save(conversation);
```

### 2.2 表驱动转换（O(1) 查找）

转换存储在 `ConcurrentHashMap` 中，键为 `(sourceState, event)`，实现 O(1) 查找。具有相同键（不同守卫）的多个转换存储为列表并按顺序评估。

### 2.3 Action-First 转换（核心原则）

动作在状态变更**之前**执行。如果动作失败，状态不变。这确保业务逻辑是状态转换的守门人。

**执行顺序：**
1. 守卫/条件检查（`when()`）— 如果为 false，转换被拒绝
2. **转换动作（`perform()`）— 失败 → `StateMachineException`，状态不变**
3. 状态转换完成

### 2.4 COLA Builder DSL

配置 API 使用阿里巴巴 COLA StateMachine 的 Builder DSL：

```java
StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
        StateMachineBuilderFactory.create();

builder.externalTransition()
        .from(ConversationState.NEW)
        .to(ConversationState.INITIATED)
        .on(ConversationFact.CONVERSATION_INITIATED)
        .perform(new ConversationInitAction());

StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        builder.build("conversation");
StateMachineFactory.register(sm);
```

**Builder API 顺序：** `from() → to() → on() → when() → perform()`

### 2.5 泛型类型安全

COLA StateMachine 使用 Java 泛型实现类型安全的状态、事件和上下文。无反射，无运行时类型错误。

### 2.6 独立状态机

会话和交互是**独立的**状态机，具有独立的上下文。它们不共享状态或上下文。

## 3. 包结构

### 3.1 statemachine-core（阿里巴巴 COLA StateMachine）

```
com.alibaba.cola.statemachine/
├── Action.java                    # 转换动作的函数式接口
├── Condition.java                 # 守卫的函数式接口
├── State.java                     # 状态接口
├── StateContext.java              # 上下文接口
├── StateMachine.java              # 核心状态机接口
├── StateMachineFactory.java       # 全局注册表
├── Transition.java                # 转换接口
├── builder/
│   ├── StateMachineBuilder.java   # Builder DSL 入口
│   ├── StateMachineBuilderFactory.java
│   ├── From.java, To.java, On.java, When.java, Perform.java
│   └── TransitionBuilder.java
├── impl/
│   ├── StateMachineImpl.java
│   ├── StateImpl.java
│   ├── TransitionImpl.java
│   └── StateContextImpl.java
└── exception/
    └── StateMachineException.java
```

### 3.2 chat-engine

```
com.selfdevelopment.chatengine/
├── action/
│   ├── ActionWorker.java          # 预留：异步动作执行器
│   └── impl/
│       ├── ConversationInitAction.java
│       ├── CustomerConnectAction.java
│       ├── TransferRequestAction.java
│       ├── TransferFailedAction.java
│       ├── CustomerCloseAction.java
│       ├── SurveyStartAction.java
│       └── SurveyCompleteAction.java
├── config/
│   ├── MarketConfigProvider.java
│   └── StateMachineMarketConfig.java
├── context/
│   ├── CbolStateContext.java
│   ├── TraceContext.java
│   └── TraceMdcHelper.java
├── demo/
│   ├── ChatEngineDemo.java
│   └── DemoLogger.java
├── enums/
│   ├── ConversationFact.java
│   └── ConversationState.java
├── ingress/
│   ├── AibotEvent.java
│   ├── AibotEventNormalizer.java
│   └── ChatEngineEventDispatcher.java
├── model/
│   └── ConversationInstance.java
├── monitor/
│   ├── CustomerIdleMonitor.java
│   ├── TransferMonitor.java
│   └── EndingGraceMonitor.java
├── repository/
│   └── ConversationRepository.java
├── service/
│   └── ChatEngineStateMachineService.java
└── statemachine/
    └── factory/
        └── ConversationStateMachineFactory.java
```

### 3.3 agent-connector

```
com.selfdevelopment.agentconnector/
├── action/
│   └── impl/
│       ├── ConnectionEstablishedAction.java
│       ├── ConnectionFailedAction.java
│       ├── ConnectionDroppedAction.java
│       ├── CloseRequestAction.java
│       ├── ReconnectSuccessAction.java
│       ├── HoldRequestAction.java
│       ├── HoldResumeAction.java
│       ├── TransferStartAction.java
│       └── TransferCompleteAction.java
├── context/
│   └── AgentConnectorStateContext.java
├── demo/
│   ├── AgentConnectorDemo.java
│   └── DemoLogger.java
├── enums/
│   ├── InteractionFact.java
│   └── InteractionState.java
├── ingress/
│   ├── AgentConnectorEventDispatcher.java
│   ├── GenesysEvent.java
│   └── GenesysEventNormalizer.java
├── model/
│   └── InteractionInstance.java
├── service/
│   └── AgentConnectorStateMachineService.java
└── statemachine/
    └── factory/
        └── InteractionStateMachineFactory.java
```

## 4. 关键架构决策

| 决策 | 理由 |
|------|------|
| **使用阿里巴巴 COLA StateMachine** | 经过实战检验、轻量级、类型安全、零外部依赖、活跃社区 |
| **无状态引擎** | 水平扩展、线程安全、简化持久化 |
| **Action-first 转换** | 业务逻辑是状态变更的守门人 |
| **多模块 Maven** | 清晰的关注点分离、独立开发/部署 |
| **独立状态机** | 会话（业务）和交互（通道）具有不同的生命周期 |
| **多市场配置** | 配置驱动的每市场行为（HK、SG、UK 等） |
| **问卷作为子阶段** | SURVEY_START 是内部转换（IN_PROGRESS → IN_PROGRESS），不是独立状态 |
| **NEW 初始状态** | 会话记录已创建但尚未初始化 |
| **工厂缓存模式** | COLA StateMachine 不允许重新构建；缓存以防止重复构建 |

## 5. 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 构建工具 | Maven | 3.x |
| 状态机 | 阿里巴巴 COLA StateMachine | 4.x |
| 日志 | SLF4J + Logback | 1.x |
| 测试 | JUnit 5 | 5.x |
| 代码生成 | Lombok | 1.x |

## 6. 参考资料

- 阿里巴巴 COLA GitHub：https://github.com/alibaba/COLA
- COLA StateMachine 模块：`cola-components/cola-component-statemachine`
- COLA StateMachine 设计理念：轻量级、无状态、表驱动

---

*最后更新：2026-09-04（v3.0 — 迁移到阿里巴巴 COLA StateMachine）*
