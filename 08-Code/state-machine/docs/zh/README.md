# 状态机文档

> 多模块状态机项目的设计文档和使用指南。

## 文档索引

| # | 文档 | 描述 |
|---|------|------|
| 00 | [架构概览](./00-Architecture-Overview.md) | 高层架构、多模块结构、设计原则、包结构、关键决策 |
| 01 | [状态机核心设计](./01-State-Machine-Core-Design.md) | 核心框架：StateMachine 接口、SimpleStateMachine、Transition、StateDef、Builder DSL、监听器、注册表、性能特性 |
| 02 | [业务层设计](./02-CBOL-Business-Layer-Design.md) | chat-engine（会话状态机）+ agent-connector（交互状态机）：状态/事件、上下文、多市场配置、监控器、服务、连接器 |
| 03 | [状态迁移图](./03-State-Transition-Diagrams.md) | Mermaid 状态图、迁移表、监控流程图、事件分类、默认配置 |
| 04 | [使用指南](./04-Usage-Guide.md) | 快速开始、Builder DSL、Configurer 适配器、监听器、扩展状态、多市场、监控器、异步动作、错误处理、测试、最佳实践、Spring Boot 集成、Demo 使用 |
| 05 | [高级特性](./05-Advanced-Features.md) | 持久化与乐观锁、构建时校验、幂等性、指标、事件溯源、弹性/失败处理、超时事件、图生成、故障转移、装饰器组合 |
| 06 | [多市场设计](./06-Multi-Market-Design.md) | 多市场架构：配置控制 vs 每市场 vs 混合方案、市场感知的 guard/action/扩展点、实施路线图、风险评估 |
| 07 | [多市场最佳实践](./07-Multi-Market-Best-Practices/README.md) | 8 份详细最佳实践指南：三层配置继承、市场差异可视化、路由与隔离、配置即代码 GitOps、灰度发布、熔断与降级、Schema 校验、测试矩阵 |

## 更新日志

### v2.4 (2026-09-03)
- **Registry 重构（方案 A）**：
  - 在 statemachine-core 的 `StateMachineRegistry` 中添加了 `getInstance()` 静态方法，用于全局单例访问
  - 删除了重复的业务层 Registry 类：
    * 删除了 `CbolStateMachineRegistry`（chat-engine）
    * 删除了 `AgentConnectorStateMachineRegistry`（agent-connector）
  - 更新所有业务代码，直接使用 `StateMachineRegistry.getInstance()`
  - 在 `AgentConnectorStateMachineService` 中通过 `getOrCreateStateMachine()` 方法保留了自动初始化行为
  - 更新了 5 个测试文件，使用 `StateMachineRegistry.getInstance().clear()`
  - 消除了约 100 行重复代码
  - 两个业务模块现在共享同一个全局 Registry（machineId 确保不会冲突）

### v2.3 (2026-09-03)
- **代码质量修复 (P0)**：
  - 修复了 3 个 Monitor 类的参数命名：`ChatEngineStateMachineService` → `chatEngineStateMachineService`
  - 修复了 CustomerIdleMonitor 中过时的 Javadoc：`ACTIVE` → `IN_PROGRESS`
  - 修复了 ChatEngineStateMachineService 中的全限定类名使用（添加了 StateMachineException 的 import）
- **ActionWorker 改进 (P1)**：
  - 新增 `submitWithResult()` 方法，返回 `CompletableFuture<Void>` 用于结果跟踪
  - 新增 `submitWithCallback()` 方法，支持成功/失败回调
  - 新增 `getExecutor()` 方法，用于高级配置和监控
  - 重构原有的 `submit()` 方法，内部使用 `submitWithResult()`（向后兼容）
  - 新增 15 个 ActionWorker 测试用例
- **代码重复消除**：
  - 将 `AbstractEventDispatcher<C>` 提取到 statemachine-core
  - 重构 ChatEngineEventDispatcher 和 AgentConnectorEventDispatcher 继承 AbstractEventDispatcher
  - 消除了事件分发器中约 30% 的重复代码

### v2.2 (2026-09-03)
- **状态重命名**：`ACTIVE` → `IN_PROGRESS`
- **移除状态**：`SURVEY_IN_PROGRESS` — 调查现在是 `IN_PROGRESS` 内的内部子阶段
- **新增状态**：`NEW` — 初始状态，会话记录已创建但尚未初始化
- **新增迁移**：通过 `CONVERSATION_INITIATED` 事件实现 `NEW → INITIATED`
- **新增动作**：`ConversationInitAction` — 验证配置、分配资源、设置路由
- **调查重新设计**：`SURVEY_START` 现在是内部迁移（`IN_PROGRESS → IN_PROGRESS`）；`SURVEY_COMPLETE` 直接迁移到 `ENDING`
- **架构边界**：从 chat-engine 移除 `InteractionInstance` — Conversation 和 Interaction 是独立的状态机

### v2.1 (2026-09-03)
- 添加 Action-First 状态迁移设计原则
- 添加 6 个具体的 action 实现
- 更新所有文档到版本 2.1

## 多模块结构

```
state-machine/
├── pom.xml                          # 父 POM (packaging=pom)
├── statemachine-core/               # 共享核心状态机引擎
│   └── com.selfdevelopment.statemachine
├── chat-engine/                     # 会话状态机（业务层）
│   └── com.selfdevelopment.chatengine
├── agent-connector/                 # 交互状态机（通道层）
│   └── com.selfdevelopment.agentconnector
└── docs/                            # 本文档
```

### 模块职责

| 模块 | 包名 | 职责 |
|------|------|------|
| **statemachine-core** | `com.selfdevelopment.statemachine` | 通用状态机引擎、Builder、ConfigurerAdapter、持久化、事件溯源、幂等性、超时、弹性、指标、校验、图生成、Connector 通用接口 |
| **chat-engine** | `com.selfdevelopment.chatengine` | 会话状态机（7 个状态）、Aibot 连接器、ChatHistory ODS 连接器、监控器、市场配置、Trace 上下文、异步动作执行器 |
| **agent-connector** | `com.selfdevelopment.agentconnector` | 交互状态机（6 个状态）、Genesys 连接器、WebSocket 连接器、事件归一化器 |

### 模块依赖

```
chat-engine ──► statemachine-core
agent-connector ──► statemachine-core
```

`chat-engine` 和 `agent-connector` 之间**没有直接依赖**。这种分离确保：
- 通道层关注点（连接、保持、转接）与业务层关注点（会话生命周期）隔离
- 每个模块可以独立开发、测试和部署
- 清晰的系统边界：chat-engine 连接 AIBot 和 ChatHistory；agent-connector 连接 Genesys 和 WebSocket

## 快速参考

### 核心框架 (statemachine-core)
- **无状态引擎** — 每次调用注入当前状态
- **表驱动** — 通过 ConcurrentHashMap 实现 O(1) 迁移查找
- **零依赖** — 仅 JDK（Micrometer 为可选指标依赖）
- **Spring 风格配置** — StateMachineConfigurerAdapter
- **进入/退出动作** — 尽力执行（失败不阻断迁移）
- **迁移动作** — 失败以 StateMachineException 传播
- **生命周期** — start/stop 带监听器通知
- **扩展状态** — 跨迁移共享的键值变量
- **迁移类型** — EXTERNAL（外部）和 INTERNAL（内部）

### 高级特性 (statemachine-core)
- **持久化** — StateRepository 带乐观锁（基于版本）、自动重试
- **校验** — 8 条构建时规则（ERROR/WARNING 级别），构建时校验
- **幂等性** — 事件 ID 去重，缓存结果
- **指标** — Micrometer 集成（Timer/Counter），5 个带标签的指标
- **事件溯源** — 不可变迁移记录、回放、状态重建、时间回溯
- **弹性** — 4 种失败处理器（THROW/RETURN_SOURCE/FALLBACK/带退避的 RETRY）
- **超时** — 进入状态时自动调度、退出时自动取消、单次/重复
- **图生成** — 从配置自动生成 Mermaid、PlantUML、迁移表

### Chat Engine (chat-engine)
- **7 个会话状态** — INITIATED, IN_PROGRESS, TRANSFERRED, IN_PROGRESS, ENDING, ERROR, CLOSED
- **18 个事件** — 生命周期、转接、满意度调查、结束、系统、故障转移
- **23 条迁移** — 包括 v6 转接失败重置、满意度调查流程、故障转移流程
- **3 个监控器** — CustomerIdle（客户空闲）、TransferTimeout（转接超时）、EndingGrace（结束宽限）
- **多市场** — 每市场超时、特性开关、动作映射、扩展点
- **TraceId** — 通过 SLF4J MDC 全链路传播
- **异步动作** — 有界线程池 + MDC 传播
- **故障转移** — 动作错误 → SYS_ACTION_FAILED → ERROR → 重试/中止
- **连接器** — AibotConnector、ChatHistoryOdsConnector

### Agent Connector (agent-connector)
- **6 个交互状态** — CONNECTING, CONNECTED, RECONNECTING, HELD, TRANSFERRING, DISCONNECTED
- **14 个事件** — 连接生命周期、保持、转接
- **连接器** — GenesysConnector、CbolWebsocketConnector
- **事件归一化器** — GenesysEventNormalizer

### Demo 代码
- **ChatEngineDemo** — 4 个演示：基础流程、满意度调查流程、多市场配置、转接失败
- **AgentConnectorDemo** — 6 个演示：连接、保持、重连、重连耗尽、转接、连接失败

## 构建与测试

```bash
cd 08-Code/state-machine

# 构建所有模块
./mvnw.cmd clean install

# 编译所有模块
./mvnw.cmd clean compile

# 运行所有测试
./mvnw.cmd clean test

# 运行指定模块的测试
./mvnw.cmd clean test -pl statemachine-core
./mvnw.cmd clean test -pl chat-engine
./mvnw.cmd clean test -pl agent-connector

# 生成覆盖率报告
./mvnw.cmd test jacoco:report

# 运行 Demo
./mvnw.cmd exec:java -pl chat-engine -Dexec.mainClass="com.selfdevelopment.chatengine.demo.ChatEngineDemo"
./mvnw.cmd exec:java -pl agent-connector -Dexec.mainClass="com.selfdevelopment.agentconnector.demo.AgentConnectorDemo"
```

**当前统计：** 270+ 个测试用例，全部模块 BUILD SUCCESS

## 包结构 (statemachine-core)

```
com.selfdevelopment.statemachine/
├── api/              # 核心接口 (StateMachine, Action, Guard, Listener, Registry)
├── core/             # 核心实现 (SimpleStateMachine, Transition, StateDef, StateContext, ExtendedState)
├── builder/          # StateMachineBuilder DSL
├── config/           # StateMachineConfigurerAdapter (Spring 风格)
├── connector/        # 通用 Connector 接口
├── event/            # StandardEvent, EventNormalizer, EventDispatcher
├── persistence/      # StateRepository, InMemoryStateRepository, VersionedState, OptimisticLockException
├── validation/       # StateMachineValidator, ValidationError
├── idempotency/      # ProcessedEventStore, IdempotentStateMachineDecorator
├── metrics/          # StateMachineMetrics, MonitoredStateMachine (Micrometer 可选)
├── eventsourcing/    # StateTransitionEvent, StateTransitionStore, EventSourcedStateMachine
├── resilience/       # FailureHandler, Throw/ReturnSource/Fallback/Retry 处理器, ResilientStateMachine, FailoverStateMachine
├── timeout/          # TimeoutConfig, StateMachineTimeoutScheduler, TimeoutAwareStateMachine
├── diagram/          # StateMachineDiagramGenerator (Mermaid/PlantUML/表)
└── exception/        # StateMachineException
```

---

*最后更新：2026-09-02*
