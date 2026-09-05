# 状态机核心框架设计

> 版本：4.0 | 最后更新：2026-09-05
> 基于阿里巴巴 COLA StateMachine：https://github.com/alibaba/COLA
> 对齐事件驱动编排设计（v4.0）

## 0. 设计原则

### 0.1 Action-First 转换（核心原则）

状态机遵循 **action-first 转换** 原则：

> **Action 在状态变更之前执行。如果 action 失败，状态不会改变。**

这确保了业务逻辑（action）是状态转换的守门人。从状态 A 到状态 B 的转换只有在关联的 action 成功执行后才完成。

**EXTERNAL 转换的执行顺序（COLA StateMachine）：**
1. 守卫/条件检查（`when()`）— 如果为 false，转换被拒绝，状态保持在源状态
2. **转换 action（`perform()`）— 失败 → 异常传播，状态不会改变**
3. 状态转换完成，返回目标状态

**关键点：**
- 转换 action 是唯一可以阻止状态转换的 action
- 当转换 action 失败时，源状态被保留，异常传播
- COLA StateMachine 在没有转换匹配时**不**抛出 `StateMachineException` — 它返回源状态并调用 `failCallback`

### 0.2 无状态设计

COLA StateMachine 被有意设计为**无状态**：

> 一旦构建完成，状态机实例可以安全地在多个线程间共享。

**含义：**
- 状态机**不**存储当前状态 — 调用者必须将 `sourceState` 传递给 `fireEvent()`
- 事件处理期间没有可变状态 — 转换一旦构建就是不可变的
- 设计上是线程安全的 — 可以在 Spring 容器中用作单例

### 0.3 表驱动转换查找

转换存储在 `Map<S, State<S,E,C>>` 中，每个 `State` 包含一个 `Map<E, List<Transition<S,E,C>>>` 用于 O(1) 事件查找。

### 0.4 泛型类型安全

所有核心接口都使用 Java 泛型：
- `S` — 状态类型（通常是枚举）
- `E` — 事件类型（通常是枚举）
- `C` — 业务上下文类型（携带领域数据）

运行时不使用反射。

---

## 1. 核心抽象（阿里巴巴 COLA StateMachine）

### 1.1 包结构

核心框架基于阿里巴巴 COLA StateMachine：

| 包 | 职责 | 关键类 |
|----|------|--------|
| `com.alibaba.cola.statemachine` | 核心接口 | `StateMachine`、`Action`、`Condition`、`State`、`Transition`、`StateMachineFactory`、`StateContext`、`Visitable`、`Visitor` |
| `com.alibaba.cola.statemachine.builder` | Builder DSL | `StateMachineBuilder`、`StateMachineBuilderFactory`、`ExternalTransitionBuilder`、`ExternalTransitionsBuilder`、`InternalTransitionBuilder`、`ExternalParallelTransitionBuilder`、`From`、`To`、`On`、`When`、`Perform`、`FailCallback` |
| `com.alibaba.cola.statemachine.impl` | 核心实现 | `StateMachineImpl`、`StateImpl`、`TransitionImpl`、`StateHelper`、`EventTransitions`、`TransitionType`、`Debugger`、`SysOutVisitor`、`PlantUMLVisitor`、`StateMachineException` |
| `com.alibaba.cola.statemachine.exception` | 异常 | `TransitionFailException` |

### 1.2 StateMachine 接口

定义状态机契约的中心接口。

```java
public interface StateMachine<S, E, C> extends Visitable {

    /**
     * 验证事件是否可以从源状态触发。
     * @param sourceStateId 源状态
     * @param event 要验证的事件
     * @return 如果 (sourceState, event) 至少存在一个转换则返回 true
     */
    boolean verify(S sourceStateId, E event);

    /**
     * 向状态机发送事件。
     *
     * @param sourceState 源状态
     * @param event 要发送的事件
     * @param ctx 用户定义的业务上下文
     * @return 转换后的目标状态（如果没有转换匹配则返回源状态）
     */
    S fireEvent(S sourceState, E event, C ctx);

    /**
     * 向状态机发送并行事件。
     * 可能匹配多个转换，全部执行。
     *
     * @param sourceState 源状态
     * @param event 要发送的事件
     * @param ctx 用户定义的业务上下文
     * @return 所有并行转换后的目标状态列表
     */
    List<S> fireParallelEvent(S sourceState, E event, C ctx);

    /**
     * MachineId 是状态机的标识符。
     * @return 状态机 ID
     */
    String getMachineId();

    /**
     * 使用访问者模式将状态机结构显示到标准输出。
     */
    void showStateMachine();

    /**
     * 生成 PlantUML 状态图字符串。
     * @return PlantUML 字符串
     */
    String generatePlantUML();
}
```

**类型参数：**
- `S` — 状态类型（通常是枚举）
- `E` — 事件类型（通常是枚举）
- `C` — 业务上下文类型（携带领域数据）

**关键行为：**
- `fireEvent()` 返回目标状态，而不是包装对象
- 如果没有转换匹配，`fireEvent()` 返回源状态并调用 `failCallback`
- `fireParallelEvent()` 执行所有匹配的转换并返回所有目标状态
- `verify()` 只检查转换是否存在，不检查条件

### 1.3 Action 接口

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
- Actions 在状态转换之前同步执行（action-first 原则）
- 如果 action 抛出异常，状态不会改变，异常传播
- Actions 是无状态的，可以在多个转换间共享
- 可以实现为 lambda 表达式或具体类

**使用示例：**
```java
// Lambda 风格
Action<ConversationState, ConversationFact, CbolStateContext> logAction =
    (from, to, event, ctx) -> log.info("转换：{} -> {} 通过 {}", from, to, event);

// 具体类风格
public class CustomerConnectAction implements Action<ConversationState, ConversationFact, CbolStateContext> {
    @Override
    public void execute(ConversationState from, ConversationState to,
                        ConversationFact event, CbolStateContext ctx) {
        // 业务逻辑
    }
}
```

### 1.4 Condition 接口（守卫）

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

**关键点：**
- 条件在 actions 之前评估
- 如果条件返回 false，转换被跳过（状态保持在源状态）
- 具有相同 (source, event) 的多个转换可以有不同的条件
- 第一个条件满足的转换被选中（对于 `fireEvent`）

**`routeTransition()` 中的条件匹配逻辑：**
```java
for (Transition<S, E, C> transition : transitions) {
    if (transition.getCondition() == null) {
        transit = transition;  // 无条件转换，继续检查
    } else if (transition.getCondition().isSatisfied(ctx)) {
        transit = transition;
        break;  // 条件满足，立即选择此转换
    }
}
```

**重要：** 如果无条件转换（null 条件）出现在条件转换之前，并且条件转换的条件满足，则选择条件转换（因为 `break`）。如果条件转换的条件不满足，则选择无条件转换。

### 1.5 State 接口

表示状态机中的一个状态。

```java
public interface State<S, E, C> extends Visitable {

    /**
     * 获取状态标识符。
     * @return 状态标识符
     */
    S getId();

    /**
     * 向状态添加单个转换。
     * @param event 转换的事件
     * @param target 转换的目标
     * @param transitionType 转换类型（EXTERNAL, INTERNAL, LOCAL）
     * @return 创建的转换
     */
    Transition<S, E, C> addTransition(E event, State<S, E, C> target, TransitionType transitionType);

    /**
     * 添加多个转换（用于并行转换）。
     * @param event 转换的事件
     * @param targets 目标状态列表
     * @param transitionType 转换类型
     * @return 创建的转换列表
     */
    List<Transition<S, E, C>> addTransitions(E event, List<State<S, E, C>> targets, TransitionType transitionType);

    /**
     * 获取特定事件的所有转换。
     * @param event 事件
     * @return 转换列表（如果此事件没有转换可能为 null）
     */
    List<Transition<S, E, C>> getEventTransitions(E event);

    /**
     * 获取此状态的所有转换。
     * @return 所有转换的集合
     */
    Collection<Transition<S, E, C>> getAllTransitions();
}
```

**内部实现（`StateImpl`）：**
- 将转换存储在 `EventTransitions` 中，它包装了 `Map<E, List<Transition<S,E,C>>>`
- `getEventTransitions()` 直接返回列表（O(1) 查找）
- 状态在 `build()` 期间由 builder 创建和管理

### 1.6 Transition 接口

表示状态之间的转换。

```java
public interface Transition<S, E, C> {

    State<S, E, C> getSource();
    void setSource(State<S, E, C> state);

    E getEvent();
    void setEvent(E event);

    void setType(TransitionType type);

    State<S, E, C> getTarget();
    void setTarget(State<S, E, C> state);

    Condition<C> getCondition();
    void setCondition(Condition<C> condition);

    Action<S, E, C> getAction();
    void setAction(Action<S, E, C> action);

    /**
     * 执行从源状态到目标状态的转换。
     * @param ctx 业务上下文
     * @param checkCondition 是否检查条件
     * @return 目标状态（如果条件不满足则返回源状态）
     */
    State<S, E, C> transit(C ctx, boolean checkCondition);

    /**
     * 验证转换正确性。
     * 对于 INTERNAL 转换，源和目标必须相同。
     */
    void verify();
}
```

**`transit()` 实现（action-first 原则）：**
```java
@Override
public State<S, E, C> transit(C ctx, boolean checkCondition) {
    this.verify();
    if (!checkCondition || condition == null || condition.isSatisfied(ctx)) {
        if (action != null) {
            action.execute(source.getId(), target.getId(), event, ctx);
        }
        return target;
    }
    // 条件不满足，保持在源状态
    return source;
}
```

**关键点：**
- `verify()` 检查 INTERNAL 转换的 source == target
- `transit()` 在返回目标状态之前执行 action
- 如果条件不满足，返回源状态（不执行 action）
- 转换设计为构建后不可变（线程安全）

### 1.7 TransitionType 枚举

定义转换类型。

```java
public enum TransitionType {
    /**
     * 内部转换：不会导致状态变更。
     * 源和目标必须是相同的状态。
     * Action 被执行，但状态不变。
     */
    INTERNAL,

    /**
     * 本地转换：不会退出复合（源）状态，
     * 但会退出并重新进入复合状态内的任何状态。
     */
    LOCAL,

    /**
     * 外部转换：退出复合（源）状态
     * 并进入目标状态。
     */
    EXTERNAL
}
```

**本项目中的使用：**
- `EXTERNAL` — 标准状态变更（NEW → INITIATED, INITIATED → IN_PROGRESS 等）
- `INTERNAL` — 问卷作为子阶段（SURVEY_START 时 IN_PROGRESS → IN_PROGRESS）
- `LOCAL` — 当前未使用（预留用于未来的复合状态）

### 1.8 StateContext 接口（内部）

转换处理期间使用的内部接口。这**不是**业务上下文 — 业务上下文是传递给 `fireEvent()` 的泛型类型 `C`。

```java
public interface StateContext<S, E, C> {
    /**
     * 获取正在处理的转换。
     */
    Transition<S, E, C> getTransition();

    /**
     * 获取状态机。
     */
    StateMachine<S, E, C> getStateMachine();
}
```

**注意：** 在当前的 COLA 实现中，`StateContext` 已定义但在主 `fireEvent()` 流程中未主动使用。业务上下文 `C` 直接传递给 actions 和 conditions。

### 1.9 FailCallback 接口

当 (sourceState, event) 对没有转换匹配时调用的回调。

```java
@FunctionalInterface
public interface FailCallback<S, E, C> {
    /**
     * 事件触发失败时执行的回调函数。
     * @param sourceState 源状态
     * @param event 失败的事件
     * @param context 业务上下文
     */
    void onFail(S sourceState, E event, C context);
}
```

**内置实现：**
- `NumbFailCallback` — 默认，什么都不做
- `AlertFailCallback` — 记录警告（使用 `Debugger.debug()`）

**使用：**
```java
builder.setFailCallback((source, event, ctx) ->
    log.warn("状态={}, 事件={} 没有转换", source, event));
```

**重要：** 当 `routeTransition()` 返回 null（没有转换匹配或所有条件失败）时，调用 `failCallback.onFail()`。然后 `fireEvent()` 方法返回源状态。

### 1.10 StateMachineFactory

状态机的中央注册表。

```java
public class StateMachineFactory {
    static Map<String, StateMachine> stateMachineMap = new ConcurrentHashMap<>();

    /**
     * 注册状态机。
     * @throws StateMachineException 如果已存在相同 ID 的状态机
     */
    public static <S, E, C> void register(StateMachine<S, E, C> stateMachine) {
        String machineId = stateMachine.getMachineId();
        if (stateMachineMap.get(machineId) != null) {
            throw new StateMachineException(
                "ID 为 [" + machineId + "] 的状态机已经构建，无需再次构建");
        }
        stateMachineMap.put(stateMachine.getMachineId(), stateMachine);
    }

    /**
     * 按 ID 获取已注册的状态机。
     * @throws StateMachineException 如果不存在给定 ID 的状态机
     */
    public static <S, E, C> StateMachine<S, E, C> get(String machineId) {
        StateMachine stateMachine = stateMachineMap.get(machineId);
        if (stateMachine == null) {
            throw new StateMachineException(
                "没有 " + machineId + " 的状态机实例，请先构建");
        }
        return stateMachine;
    }
}
```

**关键行为：**
- 使用 `ConcurrentHashMap` 进行线程安全的注册和查找
- `register()` 如果机器 ID 已存在则抛出（防止重复构建）
- `get()` 如果机器 ID 未找到则抛出
- 状态机存储为原始类型（擦除），但在检索时转换回来

---

## 2. Builder DSL

### 2.1 StateMachineBuilderFactory

创建状态机构建器的入口点。

```java
StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
        StateMachineBuilderFactory.create();
```

### 2.2 StateMachineBuilder 接口

```java
public interface StateMachineBuilder<S, E, C> {

    /**
     * 单个外部转换的构建器。
     */
    ExternalTransitionBuilder<S, E, C> externalTransition();

    /**
     * 多个外部转换的构建器（从多个源状态）。
     */
    ExternalTransitionsBuilder<S, E, C> externalTransitions();

    /**
     * 并行外部转换的构建器（到多个目标状态）。
     */
    ExternalParallelTransitionBuilder<S, E, C> externalParallelTransition();

    /**
     * 内部转换的构建器（状态不变）。
     */
    InternalTransitionBuilder<S, E, C> internalTransition();

    /**
     * 设置失败回调，默认为 NumbFailCallback（什么都不做）。
     */
    void setFailCallback(FailCallback<S, E, C> callback);

    /**
     * 使用给定的机器 ID 构建状态机。
     * @param machineId 此状态机的唯一标识符
     * @return 构建的状态机
     */
    StateMachine<S, E, C> build(String machineId);
}
```

### 2.3 外部转换（单个）

定义外部状态转换（状态改变）。

```java
builder.externalTransition()
        .from(ConversationState.NEW)
        .to(ConversationState.INITIATED)
        .on(ConversationFact.CONVERSATION_INITIATED)
        .when(ctx -> ctx.getMarketConfig() != null)  // 可选守卫
        .perform(new ConversationInitAction());
```

**Builder API 顺序：** `from() → to() → on() → when() → perform()`

**Builder 阶段接口：**
- `From<S,E,C>` — `.from(S state)` 返回 `To`
- `To<S,E,C>` — `.to(S state)` 返回 `On`
- `On<S,E,C>` — `.on(E event)` 返回 `When`
- `When<S,E,C>` — `.when(Condition<C> condition)` 返回 `Perform`
- `Perform<S,E,C>` — `.perform(Action<S,E,C> action)` 完成转换

### 2.4 外部转换（多个源）

定义从多个源状态到相同目标、相同事件的多个外部转换。

```java
// 多个源状态 → 相同目标，相同事件，相同 action
builder.externalTransitions()
        .fromAmong(ConversationState.NEW, ConversationState.INITIATED, ConversationState.IN_PROGRESS)
        .to(ConversationState.ERROR)
        .on(ConversationFact.SYS_ACTION_FAILED)
        .perform(new ErrorAction());
```

这等同于定义三个单独的外部转换。

### 2.5 外部并行转换（多个目标）

定义从一个源状态到多个目标状态的并行转换。

```java
builder.externalParallelTransition()
        .from(ConversationState.IN_PROGRESS)
        .toAmong(ConversationState.ENDING, ConversationState.SURVEY_COMPLETED)
        .on(ConversationFact.CONVERSATION_COMPLETE)
        .perform(new CompleteAction());
```

**使用 `fireParallelEvent()` 触发时：**
- 所有匹配的转换都被执行
- 所有 actions 都被执行（按顺序）
- 返回所有目标状态的列表

**注意：** 并行转换应谨慎使用 — 如果两个目标状态互斥，这可能导致状态不一致。业务层负责处理返回的状态列表。

### 2.6 内部转换

定义内部转换（状态不变，但 action 执行）。

```java
builder.internalTransition()
        .within(ConversationState.IN_PROGRESS)
        .on(ConversationFact.SURVEY_START)
        .perform(new SurveyStartAction());
```

**关键点：**
- 源和目标必须是相同的状态（由 `Transition.verify()` 验证）
- Action 被执行，但状态不变
- 适用于状态内的子阶段（例如，IN_PROGRESS 内的问卷）

### 2.7 构建和注册

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        builder.build("conversation");
StateMachineFactory.register(sm);
```

**`build()` 过程（在 `StateMachineBuilderImpl` 中）：**
1. 为所有引用的状态创建所有 `State` 对象
2. 将所有转换添加到它们的源状态
3. 使用状态映射创建 `StateMachineImpl`
4. 设置机器 ID 并标记为就绪
5. 返回状态机

### 2.8 获取状态机

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        StateMachineFactory.get("conversation");
```

---

## 3. 事件处理（内部实现）

### 3.1 fireEvent() 流程

```java
@Override
public S fireEvent(S sourceStateId, E event, C ctx) {
    isReady();  // 如果状态机未构建则抛出
    Transition<S, E, C> transition = routeTransition(sourceStateId, event, ctx);

    if (transition == null) {
        Debugger.debug("没有 " + event + " 的转换");
        failCallback.onFail(sourceStateId, event, ctx);
        return sourceStateId;  // 返回源状态，无变化
    }

    return transition.transit(ctx, false).getId();  // 执行转换
}
```

**逐步说明：**
1. **isReady()** — 检查状态机是否已构建，如果没有则抛出 `StateMachineException`
2. **routeTransition()** — 查找 (sourceState, event) 的匹配转换
3. **如果没有转换** — 调用 `failCallback.onFail()`，返回源状态
4. **如果找到转换** — 调用 `transition.transit(ctx, false)`，它：
   - 验证转换正确性
   - 检查条件（如果 `checkCondition=true`）
   - 执行 action（action-first！）
   - 返回目标状态
5. **返回目标状态 ID**

### 3.2 routeTransition() 逻辑

```java
private Transition<S, E, C> routeTransition(S sourceStateId, E event, C ctx) {
    State sourceState = getState(sourceStateId);
    List<Transition<S, E, C>> transitions = sourceState.getEventTransitions(event);

    if (transitions == null || transitions.size() == 0) {
        return null;
    }

    Transition<S, E, C> transit = null;
    for (Transition<S, E, C> transition : transitions) {
        if (transition.getCondition() == null) {
            transit = transition;  // 无条件，作为后备
        } else if (transition.getCondition().isSatisfied(ctx)) {
            transit = transition;
            break;  // 条件满足，立即选择
        }
    }

    return transit;
}
```

**条件匹配算法：**
1. 遍历 (source, event) 对的所有转换
2. 如果转换没有条件（null），将其标记为候选（后备）
3. 如果转换有条件且条件满足，立即选择它（break）
4. 如果没有条件转换满足，返回无条件的那个（如果存在）
5. 如果根本没有转换匹配，返回 null

**重要含义：** 转换定义的**顺序**很重要。条件转换应在无条件后备之前定义，以确保它们首先被检查。

### 3.3 transition.transit() 实现

```java
@Override
public State<S, E, C> transit(C ctx, boolean checkCondition) {
    Debugger.debug("执行转换：" + this);
    this.verify();  // 验证 INTERNAL 转换的 source==target

    if (!checkCondition || condition == null || condition.isSatisfied(ctx)) {
        if (action != null) {
            action.execute(source.getId(), target.getId(), event, ctx);  // ACTION FIRST!
        }
        return target;  // 状态改变
    }

    Debugger.debug("条件不满足，保持在 " + source + " 状态");
    return source;  // 状态不变
}
```

**代码中的 action-first 原则：**
- Action 在返回目标状态之前执行
- 如果 action 抛出异常，`return target` 永远不会被执行
- 异常传播到 `fireEvent()` 调用者
- 状态机状态不变（无状态设计，调用者管理状态）

### 3.4 fireParallelEvent() 流程

```java
@Override
public List<S> fireParallelEvent(S sourceState, E event, C context) {
    isReady();
    List<Transition<S, E, C>> transitions = routeTransitions(sourceState, event, context);
    List<S> result = new ArrayList<>();

    if (transitions == null || transitions.isEmpty()) {
        Debugger.debug("没有 " + event + " 的转换");
        failCallback.onFail(sourceState, event, context);
        result.add(sourceState);
        return result;
    }

    for (Transition<S, E, C> transition : transitions) {
        S id = transition.transit(context, false).getId();
        result.add(id);
    }
    return result;
}
```

**与 `fireEvent()` 的区别：**
- `routeTransitions()` 返回所有匹配的转换（不只是第一个）
- 所有转换按顺序执行
- 返回所有目标状态的列表

### 3.5 verify() 方法

```java
@Override
public boolean verify(S sourceStateId, E event) {
    isReady();
    State sourceState = getState(sourceStateId);
    List<Transition<S, E, C>> transitions = sourceState.getEventTransitions(event);
    return transitions != null && transitions.size() != 0;
}
```

**注意：** `verify()` 只检查 (source, event) 是否存在转换。它**不**检查条件是否满足。使用 `fireEvent()` 并检查返回状态是否等于源状态来确定转换是否实际发生。

---

## 4. 访问者模式（PlantUML 和调试）

COLA StateMachine 使用访问者模式来显示状态机结构。

### 4.1 Visitable 接口

```java
public interface Visitable {
    String accept(Visitor visitor);
}
```

`StateMachine` 和 `State` 都继承 `Visitable`。

### 4.2 Visitor 接口

```java
public interface Visitor {
    String visitOnEntry(StateMachine<?, ?, ?> stateMachine);
    String visitOnExit(StateMachine<?, ?, ?> stateMachine);
    String visitOnEntry(State<?, ?, ?> state);
    String visitOnExit(State<?, ?, ?> state);
}
```

### 4.3 PlantUMLVisitor

生成 PlantUML 状态图字符串。

```java
@Override
public String generatePlantUML() {
    PlantUMLVisitor plantUMLVisitor = new PlantUMLVisitor();
    return accept(plantUMLVisitor);
}
```

**输出示例：**
```
@startuml
[*] --> NEW
NEW --> INITIATED : CONVERSATION_INITIATED
INITIATED --> IN_PROGRESS : CUSTOMER_CONNECT
IN_PROGRESS --> TRANSFERRED : TRANSFER_REQUEST
IN_PROGRESS --> ENDING : CUSTOMER_CLOSE
@enduml
```

### 4.4 SysOutVisitor

将状态机结构打印到标准输出。

```java
@Override
public void showStateMachine() {
    SysOutVisitor sysOutVisitor = new SysOutVisitor();
    accept(sysOutVisitor);
}
```

用于在运行时调试状态机结构。

---

## 5. 性能特性

### 5.1 复杂度分析

| 操作 | 复杂度 | 说明 |
|------|--------|------|
| `fireEvent()` | 平均 O(1) | HashMap 查找状态，然后 HashMap 查找事件，然后遍历转换（通常 1-2 个） |
| `fireParallelEvent()` | O(n) | n = 匹配转换的数量 |
| `verify()` | O(1) | 仅 HashMap 查找 |
| `build()` | O(n) | n = 转换数量，创建所有 State 对象 |
| Action 执行 | 同步 | 阻塞，action-first 原则 |
| `generatePlantUML()` | O(n) | n = 状态 + 转换数量 |

### 5.2 关键优化

1. **表驱动转换查找** — `Map<S, State>` + `Map<E, List<Transition>>` 实现 O(1) 查找
2. **无状态引擎** — 事件处理期间没有可变状态，线程安全
3. **不可变转换** — 一旦构建，转换不能被修改（线程安全）
4. **泛型类型安全** — 运行时无反射
5. **核心零外部依赖** — 仅 Java 标准库
6. **StateMachineFactory 中的 ConcurrentHashMap** — 线程安全的注册和查找

### 5.3 线程安全

**StateMachineImpl 是线程安全的，因为：**
- `stateMap` 在 `build()` 后不可变
- `ready` 标志在 `build()` 期间设置一次
- `machineId` 在 `build()` 期间设置一次
- `failCallback` 在 `build()` 期间设置一次
- `fireEvent()` 处理期间没有可变状态

**可以安全地在 Spring 中用作单例：**
```java
@Bean
public StateMachine<ConversationState, ConversationFact, CbolStateContext> conversationStateMachine() {
    return ConversationStateMachineFactory.build();  // 缓存，返回相同实例
}
```

---

## 6. 错误处理

### 6.1 StateMachineException

状态机抛出的运行时异常。

```java
public class StateMachineException extends RuntimeException {
    public StateMachineException(String message) {
        super(message);
    }
}
```

**抛出时机：**
- 状态机尚未构建（`isReady()` 检查）
- 源状态在状态映射中未找到
- 在 `StateMachineFactory` 中重复注册机器 ID
- 在 `StateMachineFactory.get()` 中未找到机器 ID
- INTERNAL 转换的 source != target（`verify()` 检查）

**不抛出时机：**
- (source, event) 没有转换匹配 — 返回源状态，调用 failCallback
- 条件不满足 — 返回源状态
- Action 抛出异常 — 传播原始异常（不包装在 StateMachineException 中）

### 6.2 TransitionFailException

```java
public class TransitionFailException extends RuntimeException {
    // 在特定转换失败场景中抛出
}
```

### 6.3 Action 异常传播

当 action 抛出异常时：
1. `transition.transit()` 不捕获它 — 直接传播
2. `fireEvent()` 不捕获它 — 直接传播给调用者
3. 状态不变（action-first 原则）
4. 调用者负责捕获和处理

```java
try {
    ConversationState newState = sm.fireEvent(source, event, ctx);
} catch (RuntimeException e) {
    // Action 失败，状态保持在源状态
    log.error("转换失败", e);
    // 业务层可以：重试、触发故障转移事件或升级
}
```

---

## 7. 工厂缓存模式

由于 COLA StateMachine 不允许使用相同 ID 重新构建状态机，请在工厂类中使用此缓存模式：

```java
public class ConversationStateMachineFactory {
    public static final String MACHINE_ID = "conversation";

    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
        // 快速路径：首先尝试获取现有状态机
        try {
            StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                    StateMachineFactory.get(MACHINE_ID);
            if (existing != null) {
                return existing;
            }
        } catch (StateMachineException ignored) {
            // 状态机尚未构建
        }

        // 慢速路径：使用双重检查锁定构建
        synchronized (ConversationStateMachineFactory.class) {
            // 获取锁后双重检查
            try {
                StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                        StateMachineFactory.get(MACHINE_ID);
                if (existing != null) {
                    return existing;
                }
            } catch (StateMachineException ignored) {
                // 状态机尚未构建
            }

            // 构建和注册
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

**为什么使用此模式：**
- `StateMachineFactory.register()` 如果机器 ID 已存在则抛出
- `StateMachineFactory.get()` 如果机器 ID 未找到则抛出
- 双重检查锁定确保只有一个线程构建状态机
- 快速路径避免后续调用的同步开销

---

## 8. 最佳实践

### 8.1 状态管理

**应该：**
- 在业务层管理当前状态（数据库、实体等）
- 显式将源状态传递给 `fireEvent()`
- 成功 `fireEvent()` 后更新实体状态

```java
ConversationState currentState = conversation.getState();
ConversationState newState = sm.fireEvent(currentState, event, ctx);
conversation.setState(newState);
```

**不应该：**
- 期望状态机存储当前状态（它是无状态的！）
- 不知道当前源状态就调用 `fireEvent()`
- 忽略 `fireEvent()` 的返回值

### 8.2 Action 设计

**应该：**
- 使 actions 无状态（没有可变的实例字段）
- 使 actions 幂等（可以安全重试）
- 保持 actions 专注于单一职责
- 使用构造函数注入依赖

```java
public class CustomerConnectAction implements Action<ConversationState, ConversationFact, CbolStateContext> {
    private final NotificationService notificationService;

    public CustomerConnectAction(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void execute(ConversationState from, ConversationState to,
                        ConversationFact event, CbolStateContext ctx) {
        notificationService.sendWelcome(ctx.conversation().getCustomerId());
    }
}
```

**不应该：**
- 在 actions 中放置有状态字段（它们在转换间共享）
- 从 actions 抛出受检异常（如果需要，包装在 RuntimeException 中）
- 在 actions 中执行重量级 IO 而不考虑超时

### 8.3 Condition 设计

**应该：**
- 保持条件简单快速（无 IO）
- 使用条件进行业务规则检查
- 考虑使用默认的无条件转换作为后备

**不应该：**
- 在条件中放置副作用（它们可能被多次评估）
- 使用条件执行 actions（改用 `perform()`）

### 8.4 转换顺序

**应该：**
- 在无条件后备之前定义条件转换
- 使用描述发生了什么的有意义的事件名称

**不应该：**
- 为相同的 (source, event) 定义多个无条件转换 — 只有最后一个会被用作后备

### 8.5 测试

**应该：**
- 独立测试每个转换
- 测试条件 true/false 分支
- 测试 action 失败场景
- 测试无效的 (source, event) 对
- 在测试中使用唯一的机器 ID 以避免冲突

```java
class ConversationStateMachineTest {
    private static final String TEST_MACHINE_ID = "conversation-test-" + UUID.randomUUID();

    @Test
    void shouldTransitionFromNewToInitiated() {
        StateMachine<ConversationState, ConversationFact, CbolStateContext> sm = buildTestMachine(TEST_MACHINE_ID);
        CbolStateContext ctx = buildTestContext();

        ConversationState result = sm.fireEvent(ConversationState.NEW, ConversationFact.CONVERSATION_INITIATED, ctx);

        assertEquals(ConversationState.INITIATED, result);
    }
}
```

---

## 9. 与其他框架的比较

| 特性 | COLA StateMachine | Spring StateMachine | Sqllin StateMachine |
|------|-------------------|---------------------|---------------------|
| 无状态 | 是 | 否（有扩展状态） | 是 |
| Action-first | 是 | 否（转换后 action） | 是 |
| Builder DSL | 流式 | ConfigurerAdapter | 流式 |
| PlantUML 生成 | 内置 | 通过支持 | 否 |
| 并行转换 | 是 | 是（regions） | 否 |
| 外部依赖 | 无 | Spring Context | 无 |
| 学习曲线 | 低 | 中 | 低 |
| 性能 | 高（O(1) 查找） | 中 | 高 |

**为什么本项目选择 COLA StateMachine：**
1. **无状态** — 适合我们的架构，状态在业务层管理
2. **Action-first** — 确保业务逻辑是状态变更的守门人
3. **零依赖** — 轻量级，可以在任何 Java 项目中使用
4. **简单 API** — 易于学习和使用
5. **PlantUML 生成** — 自动文档化
6. **阿里巴巴验证** — 在阿里巴巴生产环境中使用

---

## 10. 参考资料

- 阿里巴巴 COLA GitHub：https://github.com/alibaba/COLA
- COLA StateMachine 模块：`cola-components/cola-component-statemachine`
- COLA StateMachine 源码：`cola-components/cola-component-statemachine/src/main/java/com/alibaba/cola/statemachine/`
- COLA StateMachine 测试：`cola-components/cola-component-statemachine/src/test/java/com/alibaba/cola/test/`
- COLA StateMachine 作者：Frank Zhang (https://github.com/FrankZhang007)

---

*最后更新：2026-09-05（v3.1 — 基于实际 COLA 源码的详细核心框架文档）*
