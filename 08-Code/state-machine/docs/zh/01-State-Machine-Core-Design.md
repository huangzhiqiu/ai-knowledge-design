# 状态机核心框架设计

> 版本：3.0 | 最后更新：2026-09-04
> 基于阿里巴巴 COLA StateMachine：https://github.com/alibaba/COLA

## 0. 设计原则

### Action-First 转换（核心原则）

状态机遵循 **action-first 转换** 原则：

> **动作在状态变更之前执行。如果动作失败，状态不变。**

这确保业务逻辑（动作）是状态转换的守门人。从状态 A 到状态 B 的转换只有在关联动作成功执行后才完成。

**外部转换的执行顺序（COLA StateMachine）：**
1. 守卫/条件检查（`when()`）— 如果为 false，转换被拒绝
2. **转换动作（`perform()`）— 失败 → `StateMachineException`，状态不变**
3. 状态转换完成

**关键点：**
- 转换动作是唯一可以阻止状态转换的动作
- 当转换动作失败时，源状态被保留，异常传播
- 当没有匹配的转换或动作失败时，COLA StateMachine 抛出 `StateMachineException`

## 1. 核心抽象（阿里巴巴 COLA StateMachine）

### 包结构

核心框架基于阿里巴巴 COLA StateMachine：

| 包 | 职责 |
|----|------|
| `com.alibaba.cola.statemachine` | 核心接口：`StateMachine`、`Action`、`Condition`、`State`、`Transition`、`StateMachineFactory`、`StateContext` |
| `com.alibaba.cola.statemachine.builder` | Builder DSL：`StateMachineBuilder`、`StateMachineBuilderFactory`、`TransitionBuilder`、`From`、`To`、`On`、`When`、`Perform` |
| `com.alibaba.cola.statemachine.impl` | 核心实现：`StateMachineImpl`、`StateImpl`、`TransitionImpl`、`StateContextImpl` |
| `com.alibaba.cola.statemachine.exception` | `StateMachineException` |

### 1.1 StateMachine 接口

定义状态机契约的中心接口。

```java
public interface StateMachine<S, E, C> {
    /**
     * 触发事件并返回目标状态。
     * @param sourceState 源状态
     * @param event 要触发的事件
     * @param ctx 业务上下文
     * @return 转换后的目标状态
     * @throws StateMachineException 如果没有匹配的转换或动作失败
     */
    S fireEvent(S sourceState, E event, C ctx);

    /**
     * 验证事件是否可以从源状态触发。
     */
    boolean verify(S sourceState, E event);

    /**
     * 生成 PlantUML 状态图。
     */
    String generatePlantUML();

    String getMachineId();
}
```

**类型参数：**
- `S` — 状态类型（通常是枚举）
- `E` — 事件类型（通常是枚举）
- `C` — 业务上下文类型（携带领域数据）

### 1.2 Action 接口

转换动作的函数式接口。

```java
@FunctionalInterface
public interface Action<S, E, C> {
    /**
     * 执行动作。
     * @param from 源状态
     * @param to 目标状态
     * @param event 触发转换的事件
     * @param context 业务上下文
     */
    void execute(S from, S to, E event, C context);
}
```

**关键点：**
- 动作在状态转换之前同步执行
- 如果动作抛出异常，状态不变
- 动作是无状态的，可以在多个转换之间共享

### 1.3 Condition 接口（守卫）

转换守卫/条件的函数式接口。

```java
@FunctionalInterface
public interface Condition<C> {
    /**
     * 检查条件是否满足。
     * @param context 业务上下文
     * @return 如果允许转换则返回 true
     */
    boolean isSatisfied(C context);
}
```

## 2. Builder DSL

### 2.1 StateMachineBuilderFactory

创建状态机构建器的入口点。

```java
StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
        StateMachineBuilderFactory.create();
```

### 2.2 外部转换

定义外部状态转换（状态会改变）。

```java
builder.externalTransition()
        .from(ConversationState.NEW)
        .to(ConversationState.INITIATED)
        .on(ConversationFact.CONVERSATION_INITIATED)
        .when(ctx -> ctx.getMarketConfig() != null)  // 可选守卫
        .perform(new ConversationInitAction());
```

**Builder API 顺序：** `from() → to() → on() → when() → perform()`

### 2.3 内部转换

定义内部转换（状态不变，但动作执行）。

```java
builder.internalTransition()
        .within(ConversationState.IN_PROGRESS)
        .on(ConversationFact.SURVEY_START)
        .perform(new SurveyStartAction());
```

### 2.4 构建和注册

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        builder.build("conversation");
StateMachineFactory.register(sm);
```

### 2.5 获取状态机

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        StateMachineFactory.get("conversation");
```

## 3. StateMachineFactory

状态机的中央注册表。

```java
public class StateMachineFactory {
    /**
     * 注册状态机。
     * @throws StateMachineException 如果已存在相同 id 的状态机
     */
    public static <S, E, C> void register(StateMachine<S, E, C> stateMachine);

    /**
     * 按 id 获取已注册的状态机。
     * @throws StateMachineException 如果不存在给定 id 的状态机
     */
    public static <S, E, C> StateMachine<S, E, C> get(String machineId);
}
```

**重要：** COLA StateMachine 不允许使用相同 id 重新构建状态机。使用工厂缓存模式防止重复构建。

## 4. 事件处理

### 4.1 触发事件

```java
CbolStateContext ctx = buildContext();
ConversationState newState = sm.fireEvent(
        ConversationState.NEW,
        ConversationFact.CONVERSATION_INITIATED,
        ctx);
```

**返回值：** 成功转换后的目标状态。

**异常：**
- `StateMachineException` — 如果给定源状态和事件没有匹配的转换
- `StateMachineException` — 如果转换动作失败（action-first 原则）

### 4.2 验证事件

```java
boolean canFire = sm.verify(ConversationState.NEW, ConversationFact.CONVERSATION_INITIATED);
```

## 5. 性能特性

| 操作 | 复杂度 | 说明 |
|------|--------|------|
| `fireEvent()` | O(1) | 通过事件 HashMap 查找转换 |
| `verify()` | O(1) | HashMap 查找 |
| `build()` | O(n) | n = 转换数量 |
| 动作执行 | 同步 | 阻塞，action-first 原则 |

**关键优化：**
- 表驱动转换查找（ConcurrentHashMap）
- 无状态引擎（当前状态由调用者注入）
- 泛型类型安全（无反射）
- 核心零外部依赖

## 6. PlantUML 图生成

COLA StateMachine 可以自动生成 PlantUML 状态图。

```java
String plantUml = sm.generatePlantUML();
System.out.println(plantUml);
```

输出示例：
```
@startuml
[*] --> NEW
NEW --> INITIATED : CONVERSATION_INITIATED
INITIATED --> IN_PROGRESS : CUSTOMER_CONNECT
IN_PROGRESS --> TRANSFERRED : TRANSFER_REQUEST
IN_PROGRESS --> ENDING : CUSTOMER_CLOSE
@enduml
```

## 7. 工厂缓存模式

由于 COLA StateMachine 不允许重新构建，在工厂类中使用此缓存模式：

```java
public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
    // 先尝试获取已有的状态机
    try {
        StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                StateMachineFactory.get(MACHINE_ID);
        if (existing != null) {
            return existing;
        }
    } catch (Exception ignored) {
        // 状态机尚未构建
    }

    synchronized (ConversationStateMachineFactory.class) {
        // 获取锁后双重检查
        try {
            StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                    StateMachineFactory.get(MACHINE_ID);
            if (existing != null) {
                return existing;
            }
        } catch (Exception ignored) {
            // 状态机尚未构建
        }

        // 构建和注册
        try {
            StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                    StateMachineBuilderFactory.create();
            // ... 定义转换 ...
            StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
                    builder.build(MACHINE_ID);
            StateMachineFactory.register(sm);
            return sm;
        } catch (Exception e) {
            // 状态机已构建，返回已有实例
            return StateMachineFactory.get(MACHINE_ID);
        }
    }
}
```

## 8. 参考资料

- 阿里巴巴 COLA GitHub：https://github.com/alibaba/COLA
- COLA StateMachine 模块：`cola-components/cola-component-statemachine`
- COLA StateMachine 测试：`cola-components/cola-component-statemachine/src/test/java/com/alibaba/cola/test/`

---

*最后更新：2026-09-04（v3.0 — 迁移到阿里巴巴 COLA StateMachine）*
