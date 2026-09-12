# 多市场状态机设计

> 版本：4.1 | 最后更新：2026-09-12
> 状态：活跃设计（与当前实现对齐）
> 对齐事件驱动编排设计（v4.0）和 ConditionalAction 模式

## 1. 背景与需求

### 1.1 问题陈述

CBOL 消息中心将部署到**多个市场**（HK、UK、SG 等）。每个市场共享**相似的主流程**（连接 → 活跃 → 转接 → 满意度调查 → 结束 → 关闭），但在以下方面存在**差异**：

- 启用哪些功能（满意度调查、转接、Genesys 集成）
- 超时阈值（空闲、转接、结束宽限、满意度调查）
- 业务规则（路由策略、回退行为）
- 监管要求（数据保留、审计日志）
- 连接器配置（AIBot 端点、Genesys 组织、WebSocket 设置）

### 1.2 关键问题

> 我们应该使用**配置控制**（一个状态机 + 市场级配置）还是**每个市场设计一份**（独立的状态机实例）？

### 1.3 设计原则

1. **DRY（不要重复自己）**：主流程逻辑应定义一次
2. **开闭原则**：新市场应可在不修改核心代码的情况下添加
3. **显式优于隐式**：市场差异应可见且可审计
4. **可测试性**：每个市场的行为应可独立验证
5. **运维简洁性**：配置更改不应需要代码部署

---

## 2. 方案比较

### 2.1 方案 A：配置控制（单状态机 + 市场配置）

**方法**：一个状态机定义，所有差异由 `StateMachineMarketConfig` 控制。

| 优点 | 缺点 |
|------|------|
| 代码复用，主流程单一真相源 | 配置复杂度随市场差异增长 |
| 新市场 = 添加配置，无需改代码 | 极端差异可能需要"配置即代码"（反模式） |
| 跨市场行为一致 | 更难调试（是配置还是代码？） |
| 易于推出全局变更 | 需要灵活的配置模型（guard、动作映射、状态扩展） |
| 维护成本较低 | 市场特定的变通方案可能污染核心代码 |

**最适合**：市场**80%+ 相似**，差异主要在阈值和功能开关。

---

### 2.2 方案 B：每个市场一份（独立状态机实例）

**方法**：每个市场有自己的状态机工厂，带自定义迁移、动作和状态。

| 优点 | 缺点 |
|------|------|
| 最大灵活性，每个市场完全独立 | 大量代码重复 |
| 高度差异化市场实现简单 | 全局变更需要同步 N 份 |
| 易于调试（问题隔离到一个市场） | 容易漂移（一个市场得到修复，其他没有） |
| 无配置复杂度 | 新市场 = 复制粘贴 + 修改，容易出错 |
| 市场特定代码清晰分离 | 维护成本高（与市场数量线性相关） |

**最适合**：市场**<50% 相似**，业务逻辑根本不同。

---

### 2.3 方案 C：混合（推荐）

**方法**：核心状态机定义**主流程**（所有市场共享）。市场差异通过以下方式处理：

1. **市场级配置** — 阈值、功能开关、连接器设置
2. **Guard 条件** — 由市场配置门控的迁移（例如 `surveyEnabled`）
3. **动作映射** — 同一事件按市场触发不同动作（策略模式）
4. **扩展点** — 通过模块化扩展添加市场特定状态/事件
5. **市场配置文件** — 预定义的配置包（例如 `HK-profile`、`UK-profile`）

| 优点 | 缺点 |
|------|------|
| 主流程定义一次，差异显式 | 需要提前设计扩展点 |
| 新市场 = 选择配置文件 + 覆盖配置 | 中等配置复杂度（但可管理） |
| 全局变更自动传播 | 市场特定代码需要清晰分离 |
| 足够灵活以应对差异化市场 | 初始设计工作量略高 |
| 配置更改不需要代码部署 | 需要良好的配置校验工具 |

**最适合**：我们的场景 — 市场共享主流程但有有意义的差异。

---

## 3. 推荐设计：混合方法

### 3.1 架构概述

```mermaid
flowchart TB
    subgraph Core["核心状态机（所有市场共享）"]
        SM[ConversationStateMachineFactory]
        STATES[状态: NEW -&gt; INITIATED -&gt; ACTIVE -&gt; IN_PROGRESS -&gt; TRANSFERRED -&gt; ENDING -&gt; CLOSED]
        EVENTS[事件: SESSION_STARTED, INTERACTION_BECAME_ACTIVE, INBOUND_MESSAGE_RECEIVED, SOURCE_INTERACTION_TRANSFERRED, ...]
    end

    subgraph Config["市场配置层"]
        PROVIDER[MarketConfigProvider]
        HK[HK 配置]
        UK[UK 配置]
        SG[SG 配置]
        DEFAULT[默认 / 回退]
    end

    subgraph Guards["Guard 条件（市场感知）"]
        G1[surveyEnabled ?]
        G2[transferEnabled ?]
        G3[genesysEnabled ?]
    end

    subgraph Actions["动作映射（策略模式）"]
        A1[TransferAction: HK→Genesys, UK→Internal, SG→AIBot]
        A2[SurveyAction: HK→CSAT, UK→NPS, SG→Disabled]
    end

    subgraph Extensions["市场扩展（可选）"]
        EXT_HK[HK: RegulatoryAuditExtension]
        EXT_UK[UK: GdprDataRetentionExtension]
    end

    SM --> STATES
    SM --> EVENTS
    PROVIDER --> HK
    PROVIDER --> UK
    PROVIDER --> SG
    PROVIDER --> DEFAULT
    SM --> Guards
    SM --> Actions
    HK --> Extensions
    UK --> Extensions
```

### 3.2 配置模型

#### 3.2.1 StateMachineMarketConfig（当前实现）

```java
@Builder
public record StateMachineMarketConfig(
    // === 超时 ===
    long customerIdleSeconds,
    long transferTimeoutSeconds,
    long endingGraceSeconds,
    long surveyTimeoutSeconds,

    // === 功能开关 ===
    boolean surveyEnabled,
    boolean transferEnabled,
    boolean genesysEnabled,

    // === 业务规则 ===
    String fallbackRoutingStrategy,      // DROP / REQUEUE / FALLBACK_QUEUE

    // === 重试配置 ===
    int maxRetries,
    long retryBaseDelayMs,
    long retryMaxDelayMs
) {
    public static StateMachineMarketConfig defaultConfig() {
        return StateMachineMarketConfig.builder()
                .customerIdleSeconds(300)
                .transferTimeoutSeconds(180)
                .endingGraceSeconds(120)
                .surveyTimeoutSeconds(120)
                .surveyEnabled(false)
                .transferEnabled(true)
                .genesysEnabled(false)
                .fallbackRoutingStrategy("DROP")
                .maxRetries(3)
                .retryBaseDelayMs(1000)
                .retryMaxDelayMs(30000)
                .build();
    }
}
```

#### 3.2.2 市场配置文件（YAML - 未来增强）

> **注意**：YAML 配置加载是计划中的增强功能。当前实现使用
> `StateMachineMarketConfig.defaultConfig()` 并按市场进行程序化覆盖。

```yaml
# config/markets/hk.yaml (计划中)
market: HK
profile: hk-standard
timeouts:
  customerIdleSeconds: 300
  transferTimeoutSeconds: 180
  endingGraceSeconds: 120
  surveyTimeoutSeconds: 300
features:
  surveyEnabled: true
  transferEnabled: true
  genesysEnabled: true
businessRules:
  fallbackRoutingStrategy: REQUEUE
retry:
  maxRetries: 3
  retryBaseDelayMs: 1000
  retryMaxDelayMs: 30000
```

```yaml
# config/markets/uk.yaml (计划中)
market: UK
profile: uk-standard
timeouts:
  customerIdleSeconds: 600      # UK: 更长的空闲超时
  transferTimeoutSeconds: 240
  endingGraceSeconds: 180
  surveyTimeoutSeconds: 600
features:
  surveyEnabled: true
  transferEnabled: true
  genesysEnabled: false          # UK: 无 Genesys
businessRules:
  fallbackRoutingStrategy: FALLBACK_QUEUE
retry:
  maxRetries: 2
  retryBaseDelayMs: 2000
  retryMaxDelayMs: 60000
```

```yaml
# config/markets/sg.yaml (计划中)
market: SG
profile: sg-lite
timeouts:
  customerIdleSeconds: 300
  transferTimeoutSeconds: 180
  endingGraceSeconds: 120
features:
  surveyEnabled: false           # SG: 无满意度调查
  transferEnabled: true
  genesysEnabled: false
businessRules:
  fallbackRoutingStrategy: DROP
retry:
  maxRetries: 1
  retryBaseDelayMs: 500
  retryMaxDelayMs: 10000
```

### 3.3 市场感知状态机构建

#### 3.3.1 Guard 条件（通过 ConditionalAction）

迁移通过 `ConditionalAction` 模式由市场配置门控。
每个 Action 实现 `ConditionalAction` 并提供 `getCondition()` 方法。
工厂自动提取条件并传递给 COLA 的 `.when()`：

```java
// 示例：满意度调查相关迁移仅在 surveyEnabled 时允许
// 在 SurveySubmittedAction 中通过 ConditionalAction 实现
@Override
public Condition<CbolStateContext> getCondition() {
    return ctx -> ctx != null
        && ctx.conversation() != null
        && ctx.marketConfig() != null
        && ctx.marketConfig().surveyEnabled();
}

// 在 ConversationStateMachineFactory 中，条件自动提取：
Function<ConversationFact, Condition<CbolStateContext>> conditionProvider = fact -> {
    Action<CbolStateContext> action = actionProvider.apply(fact);
    return ((ConditionalAction<CbolStateContext>) action).getCondition();
};

builder.externalTransition()
    .from(ConversationState.ENDING)
    .to(ConversationState.ENDING)
    .on(ConversationFact.SURVEY_SUBMITTED)
    .when(conditionProvider.apply(ConversationFact.SURVEY_SUBMITTED))
    .perform(actionProvider.apply(ConversationFact.SURVEY_SUBMITTED));
```

> **参考**：详见 `04-Usage-Guide.md` 第 4 节 "Condition with ConditionalAction"
> 和 `05-Advanced-Features.md` 第 8.4 节 "ConditionalAction Pattern"。

#### 3.3.2 动作映射（策略模式 - 未来增强）

> **当前实现**：Action 通过 `@HandlesFact` 注解 + `ConversationActionRegistry`
> 自动发现进行管理。每个 Action 都是绑定到特定 `ConversationFact` 的 Spring `@Component`。
> 市场特定的 Action 变体可以通过为同一 fact 添加多个 Action bean，并通过
> `ConditionalAction` 实现市场感知条件来实现。

```java
// 转接动作策略（市场特定转接逻辑的未来增强）
public interface TransferStrategy {
    void execute(CbolStateContext ctx);
}

public class GenesysTransferStrategy implements TransferStrategy { ... }
public class InternalQueueTransferStrategy implements TransferStrategy { ... }
public class AibotTransferStrategy implements TransferStrategy { ... }

// 在 Action 实现中，按市场配置解析策略
@Component
@HandlesFact(ConversationFact.SOURCE_INTERACTION_TRANSFERRED)
public class SourceInteractionTransferredAction implements ConditionalAction<CbolStateContext> {
    private final TransferStrategyRegistry strategyRegistry;

    @Override
    public void execute(CbolStateContext ctx) {
        TransferStrategy strategy = strategyRegistry.resolve(ctx.marketConfig());
        strategy.execute(ctx);
    }

    @Override
    public Condition<CbolStateContext> getCondition() {
        return ctx -> ctx != null
            && ctx.marketConfig() != null
            && ctx.marketConfig().transferEnabled();
    }
}
```

#### 3.3.3 市场扩展（可选 - 未来增强）

对于不适合核心模型的市场特定状态/事件。
当前实现没有正式的扩展机制；
市场差异通过配置 + ConditionalAction 处理。

```java
// 未来增强：MarketExtension 接口
public interface MarketExtension {
    String getName();
    void registerTransitions(StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder);
}

// HK: 每次状态变更的监管审计（未来）
public class HKRegulatoryAuditExtension implements MarketExtension {
    public String getName() { return "HKRegulatoryAudit"; }

    public void registerTransitions(StateMachineBuilder<...> builder) {
        // 如有需要添加 HK 特定迁移
    }
}

// UK: 会话关闭时的 GDPR 数据保留（未来）
public class UKGdprRetentionExtension implements MarketExtension { ... }
```

### 3.4 市场感知服务层

```java
@Service
public class ChatEngineStateMachineService {
    private final ConversationStateMachineFactory stateMachineFactory;
    private final MarketConfigProvider configProvider;

    /**
     * 触发事件。每次 fire 创建新的状态机实例，
     * 以避免 COLA "already built" 异常并确保线程安全。
     */
    public ConversationState fire(String conversationId, String market, ConversationFact fact) {
        StateMachineMarketConfig config = configProvider.getConfig(market);
        CbolStateContext ctx = buildContext(conversationId, config);

        // 每次 fire 创建新的状态机实例（带唯一 machineId）
        StateMachine<ConversationState, ConversationFact, CbolStateContext> machine =
            stateMachineFactory.createStateMachine();

        return machine.fireEvent(ctx.conversation().state(), fact, ctx);
    }

    /**
     * 关闭会话：仅在 surveyEnabled 时走满意度调查路径。
     * 满意度调查是 ENDING 状态中的字段（surveyStatus），不是独立状态。
     */
    public ConversationState closeConversation(String conversationId, String market) {
        StateMachineMarketConfig config = configProvider.getConfig(market);
        // 进入 ENDING，调查将在 ENDING 内处理
        return fire(conversationId, market, ConversationFact.ENDING_STARTED);
    }
}
```

---

## 4. 实施路线图

### 阶段 1：仅配置差异（✅ 已完成）

- [x] 实现包含所有阈值/开关字段的 `StateMachineMarketConfig`
- [x] 通过 `ConditionalAction` 模式为 `surveyEnabled`、`transferEnabled`、`genesysEnabled` 添加 guard 条件
- [x] 实现带缓存的 `MarketConfigProvider`
- [x] 编写市场配置测试

**状态**：核心配置模型已实现。YAML 配置加载计划在未来实现。

### 阶段 2：动作映射（🔄 进行中）

- [x] 定义用于 Action-Fact 绑定的 `@HandlesFact` 注解
- [x] 实现用于自动发现的 `ConversationActionRegistry`
- [x] 实现用于动作管理的 `ConversationActionService`
- [ ] 实现市场特定动作策略变体
- [ ] 为每个动作变体编写测试

**状态**：动作管理框架已实现。市场特定策略变体计划中。

### 阶段 3：市场扩展（📋 计划中）

- [ ] 定义 `MarketExtension` 接口
- [ ] 实现 `ExtensionRegistry`
- [ ] 实现 HK 监管审计扩展
- [ ] 实现 UK GDPR 保留扩展
- [ ] 将扩展接入状态机构建
- [ ] 为扩展加载和执行编写测试

**预估工作量**：3-5 天

### 阶段 4：工具与运维（📋 计划中）

- [ ] 配置校验工具（启动时校验所有市场配置）
- [ ] 配置差异工具（比较两个市场配置）
- [ ] 每市场状态机图表生成器
- [ ] 配置热重载支持（无需重启刷新配置）
- [ ] 监控仪表板（每市场状态分布、错误率）
- [ ] 带三层继承的 YAML 配置加载器

**预估工作量**：2-3 周

---

## 5. 风险评估与缓解

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|-----------|--------|------------|
| 配置变得过于复杂（意大利面配置） | 中 | 高 | 从仅配置开始，仅在需要时添加动作映射/扩展。为每个市场设置"复杂度预算"。 |
| 市场特定代码泄漏到核心 | 中 | 中 | 严格的包分离：`cbol.extension.hk.*`、`cbol.extension.uk.*`。核心代码不得引用市场名称。 |
| 市场间配置漂移（行为不一致） | 中 | 中 | 配置校验 + 差异工具。定期配置审计。默认配置文件作为基线。 |
| 难以调试市场特定问题 | 中 | 中 | 所有状态迁移记录市场 + 配置快照。每市场追踪 ID。审计日志中的配置版本。 |
| 扩展排序冲突 | 低 | 中 | 扩展声明依赖项。注册表在启动时校验 DAG。 |
| 配置查找的性能开销 | 低 | 低 | 配置缓存在内存中。配置对象是不可变的（record）。每次迁移无 DB 调用。 |
| 新市场需要代码部署（扩展） | 低 | 中 | 扩展是可选的。大多数市场应仅用配置即可工作。扩展作为独立 jar 部署。 |

---

## 6. 评估与建议

### 6.1 为什么不选方案 A（纯配置）？

纯配置控制在差异是**简单开关和阈值**时有效。但我们的市场在以下方面存在差异：
- **转接机制**（Genesys vs 内部队列 vs AIBot）— 需要不同代码路径
- **满意度调查类型**（CSAT vs NPS）— 需要不同的调查 UI 和数据模型
- **监管要求**（HK 审计、UK GDPR）— 需要不同的事件监听器和数据处理

这些差异无法在不创建"配置编程语言"（反模式）的情况下干净地表达为配置。

### 6.2 为什么不选方案 B（每市场一份）？

我们的市场共享**80%+ 的主流程**：
- 状态定义相同
- 大多数迁移相同
- 错误处理（故障转移、重试）相同
- 监控和审计基础设施相同

创建独立状态机会重复这些共享逻辑，导致：
- 维护负担（在 3+ 个地方修复 bug）
- 行为不一致（一个市场得到功能，其他没有）
- 上手成本（新市场 = 复制粘贴 + 修改）

### 6.3 建议：方案 C（混合）

**采用分阶段推出的混合方法：**

1. **从阶段 1（仅配置）开始** — 覆盖约 70% 的差异
2. **在转接/满意度调查差异需要不同代码时添加阶段 2（动作映射）**
3. **仅为有真正独特需求的市场（HK 监管、UK GDPR）添加阶段 3（扩展）**
4. **大多数市场应永远不需要扩展** — 配置 + 动作映射应足够

### 6.4 成功标准

- [ ] 新市场可在 < 1 天内仅用配置上线（无需改代码）
- [ ] 全局 bug 修复自动应用于所有市场
- [ ] 每个市场的活动迁移可可视化（配置感知图表）
- [ ] 配置更改可在部署前校验
- [ ] 市场特定行为可独立测试
- [ ] 核心状态机代码中不出现市场名称

---

## 7. 附录

### 7.1 市场比较矩阵（示例 - 计划中）

| 功能 | HK | UK | SG |
|---------|----|----|----|
| 满意度调查 | 启用 | 启用 | 禁用 |
| 转接 | 启用（Genesys） | 启用（内部） | 启用（AIBot） |
| Genesys | 启用 | 禁用 | 禁用 |
| 空闲超时 | 300秒 | 600秒 | 300秒 |
| 转接超时 | 180秒 | 240秒 | 180秒 |
| 结束宽限 | 120秒 | 180秒 | 120秒 |
| 回退策略 | REQUEUE | FALLBACK_QUEUE | DROP |
| 最大重试 | 3 | 2 | 1 |

> **注意**：满意度调查类型（CSAT/NPS）、监管审计、数据保留和连接器端点
> 当前不在 `StateMachineMarketConfig` 中。这些可以根据需要添加或通过扩展机制处理。

### 7.2 参考

- `StateMachineMarketConfig` — 当前配置模型（11 个字段：超时、功能开关、回退策略、重试配置）
- `MarketConfigProvider` — 带内存缓存的配置提供者接口
- `ConditionalAction` — 动作绑定条件模式（参见 `04-Usage-Guide.md` 第 4 节）
- `ConversationActionRegistry` — 通过 `@HandlesFact` 注解自动发现 Action
- `05-Advanced-Features.md` — 异常处理机制、ConditionalAction 模式
- `02-CBOL-Business-Layer-Design.md` — 当前 Chat Engine 业务层设计
- `07-Multi-Market-Best-Practices/` — 8 个多市场最佳实践的详细设计文档
