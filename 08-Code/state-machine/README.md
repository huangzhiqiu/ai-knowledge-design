# state-machine — 自研轻量级状态机引擎

> 基于 [01-CBOL-Domain-Knowledge/state-machine/](../../01-CBOL-Domain-Knowledge/state-machine/) 设计文档的参考实现。

## 设计目标

| 原则 | 说明 |
|------|------|
| **无状态引擎** | 仅存储迁移规则（图），当前状态由业务层注入 |
| **表驱动** | Map 查找 O(1)，无反射，无 Spring 容器 |
| **单一职责** | 仅处理：校验条件 → 迁移状态 → 执行 Action |
| **零外部依赖** | 核心引擎仅依赖 JDK 标准库 |
| **类型安全** | 泛型 Java 类型，编译期检查 State/Event/Context |
| **可测试** | DSL 风格配置即活文档，天然可单元测试 |

## 技术栈

- Java 17+
- Maven 3.8+
- JUnit 5（测试）
- 零运行时依赖

## 项目结构

```
state-machine/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/selfdevelopment/ai/messaging/statemachine/
    │   ├── core/                    # 核心引擎
    │   │   ├── StateMachine.java        # 状态机接口
    │   │   ├── SimpleStateMachine.java  # 默认实现
    │   │   ├── Transition.java          # 迁移规则
    │   │   ├── Condition.java           # 守卫条件（函数式接口）
    │   │   └── Action.java              # 迁移动作（函数式接口）
    │   ├── builder/                 # DSL 构建器
    │   │   └── StateMachineBuilder.java
    │   ├── registry/                # 注册表（可选）
    │   │   └── StateMachineRegistry.java
    │   └── exception/               # 异常
    │       └── StateMachineException.java
    └── test/java/com/selfdevelopment/ai/messaging/statemachine/
        ├── core/
        └── builder/
```

## 快速开始

### 1. 定义状态、事件、上下文

```java
public enum OrderState { NEW, PAID, SHIPPED, COMPLETED, CANCELLED }
public enum OrderEvent { PAY, SHIP, COMPLETE, CANCEL }
public class OrderContext { /* business data */ }
```

### 2. 构建状态机

```java
StateMachine<OrderState, OrderEvent, OrderContext> machine =
    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>builder()
        .transition()
            .from(OrderState.NEW)
            .on(OrderEvent.PAY)
            .to(OrderState.PAID)
            .when(ctx -> ctx.isPaymentValid())
            .perform(ctx -> System.out.println("Payment processed"))
        .and()
        .build();
```

### 3. 触发事件

```java
OrderContext ctx = new OrderContext();
OrderState newState = machine.fireEvent(OrderState.NEW, OrderEvent.PAY, ctx);
```

## 性能特性

| 指标 | 特性 |
|------|------|
| 时间复杂度 | O(1) 迁移查找（Map-based） |
| 空间复杂度 | O(n)，n = 迁移规则数（通常 < 50） |
| 线程安全 | 初始化后无锁读取（ConcurrentHashMap） |
| 预期吞吐量 | 单线程 10M+ 次迁移/秒 |
| 内存占用 | 典型对话状态机 < 100KB |

## 安全与合规

- **零依赖**：100% 自研，无第三方状态机库
- **网络隔离**：引擎无外部网络调用
- **无反射**：消除动态类加载风险
- **代码审查友好**：核心代码 < 200 行，高可读性
- **扫描合规**：完全兼容 Snyk、Black Duck、SonarQube（零 CVE 风险）

## 参考文档

- [状态机引擎设计（README）](../../01-CBOL-Domain-Knowledge/state-machine/README.md)
- [事件驱动编排详细设计（version4 最终稿）](../../01-CBOL-Domain-Knowledge/state-machine/event-driven-orchestration-design.md)
- [历史版本](../../01-CBOL-Domain-Knowledge/state-machine/history/)

## 构建与测试

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 打包
mvn package

# 生成覆盖率报告
mvn test jacoco:report
```

---

*state-machine — Self-Development AI Messaging Hub — 2026-08-31*
