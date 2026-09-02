# 状态机文档

> CBOL 状态机项目的设计文档和使用指南。

## 文档索引

| # | 文档 | 描述 |
|---|------|------|
| 00 | [架构概览](./00-Architecture-Overview.md) | 高层架构、设计原则、包结构、关键决策 |
| 01 | [状态机核心设计](./01-State-Machine-Core-Design.md) | 核心框架：StateMachine 接口、SimpleStateMachine、Transition、StateDef、Builder DSL、监听器、注册表、性能特性 |
| 02 | [CBOL 业务层设计](./02-CBOL-Business-Layer-Design.md) | CBOL 特定：会话状态/事件、CbolStateContext、多市场配置、监控器、ActionWorker、服务层、错误处理 |
| 03 | [状态迁移图](./03-State-Transition-Diagrams.md) | Mermaid 状态图、迁移表、监控流程图、事件分类、默认配置 |
| 04 | [使用指南](./04-Usage-Guide.md) | 快速开始、Builder DSL、Configurer 适配器、监听器、扩展状态、多市场、监控器、异步动作、错误处理、测试、最佳实践、Spring Boot 集成 |
| 05 | [高级特性](./05-Advanced-Features.md) | 持久化与乐观锁、构建时校验、幂等性、指标、事件溯源、弹性/失败处理、超时事件、图生成、故障转移、装饰器组合 |
| 06 | [多市场设计](./06-Multi-Market-Design.md) | 多市场架构：配置控制 vs 每市场 vs 混合方案、市场感知的 guard/action/扩展点、实施路线图、风险评估 |
| 07 | [多市场最佳实践](./07-Multi-Market-Best-Practices/README.md) | 8 份详细最佳实践指南：三层配置继承、市场差异可视化、路由与隔离、配置即代码 GitOps、灰度发布、熔断与降级、Schema 校验、测试矩阵 |

## 快速参考

### 核心框架
- **无状态引擎** — 每次调用注入当前状态
- **表驱动** — 通过 ConcurrentHashMap 实现 O(1) 迁移查找
- **零依赖** — 仅 JDK（Micrometer 为可选指标依赖）
- **Spring 风格配置** — StateMachineConfigurerAdapter
- **进入/退出动作** — 尽力执行（失败不阻断迁移）
- **迁移动作** — 失败以 StateMachineException 传播
- **生命周期** — start/stop 带监听器通知
- **扩展状态** — 跨迁移共享的键值变量
- **迁移类型** — EXTERNAL（外部）和 INTERNAL（内部）

### 高级特性
- **持久化** — StateRepository 带乐观锁（基于版本）、自动重试
- **校验** — 8 条构建时规则（ERROR/WARNING 级别），构建时校验
- **幂等性** — 事件 ID 去重，缓存结果
- **指标** — Micrometer 集成（Timer/Counter），5 个带标签的指标
- **事件溯源** — 不可变迁移记录、回放、状态重建、时间回溯
- **弹性** — 4 种失败处理器（THROW/RETURN_SOURCE/FALLBACK/带退避的 RETRY）
- **超时** — 进入状态时自动调度、退出时自动取消、单次/重复
- **图生成** — 从配置自动生成 Mermaid、PlantUML、迁移表

### CBOL 业务层
- **7 个状态** — INITIATED, ACTIVE, TRANSFERRED, SURVEY_IN_PROGRESS, ENDING, ERROR, CLOSED
- **18 个事件** — 生命周期、转接、满意度调查、结束、系统、故障转移
- **23 条迁移** — 包括 v6 转接失败重置、满意度调查流程、故障转移流程
- **3 个监控器** — CustomerIdle（客户空闲）、TransferTimeout（转接超时）、EndingGrace（结束宽限）（可被超时特性替代）
- **多市场** — 每市场超时、特性开关、动作映射、扩展点
- **TraceId** — 通过 SLF4J MDC 全链路传播
- **异步动作** — 有界线程池 + MDC 传播
- **故障转移** — 动作错误 → SYS_ACTION_FAILED → ERROR → 重试/中止
- **事件驱动** — StandardEvent、EventNormalizer、EventDispatcher、4 个连接器

### 构建与测试
```bash
cd 08-Code/state-machine
./mvnw.cmd clean test          # 运行所有测试
./mvnw.cmd jacoco:report       # 生成覆盖率报告
```

**当前统计：** 327 个测试用例，83% 行覆盖率 / 71% 分支覆盖率

### 包结构
```
statemachine/
├── core/           # StateMachine, SimpleStateMachine, Transition, StateContext, ExtendedState, StateDef
├── builder/        # StateMachineBuilder DSL
├── config/         # StateMachineConfigurerAdapter
├── listener/       # StateMachineListener (8 个回调)
├── registry/       # StateMachineRegistry
├── exception/      # StateMachineException
├── persistence/    # StateRepository, InMemoryStateRepository, VersionedState, OptimisticLockException
├── validation/     # StateMachineValidator, ValidationError
├── idempotency/    # ProcessedEventStore, IdempotentStateMachineDecorator
├── metrics/        # StateMachineMetrics, MonitoredStateMachine (Micrometer 可选)
├── eventsourcing/  # StateTransitionEvent, StateTransitionStore, EventSourcedStateMachine
├── resilience/     # FailureHandler, Throw/ReturnSource/Fallback/Retry 处理器, ResilientStateMachine, FailoverStateMachine, FailoverContext
├── timeout/        # TimeoutConfig, StateMachineTimeoutScheduler, InMemoryTimeoutScheduler, TimeoutAwareStateMachine
├── event/          # StandardEvent, EventNormalizer, EventDispatcher (事件驱动基础设施)
└── diagram/        # StateMachineDiagramGenerator (Mermaid/PlantUML/表)
```
