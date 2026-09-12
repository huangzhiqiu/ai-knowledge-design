# 02. 市场差异可视化与兼容性矩阵

> 版本：1.0 | 最后更新：2026-09-01
> 优先级：P0 | 预估工作量：2-3 天

## 1. 问题陈述

### 1.1 当前问题

多个市场各有自己的配置，没有简单的方法回答：
- "HK 和 UK 之间到底有什么不同？"
- "如果我更改这个迁移规则，哪些市场会受到影响？"
- "市场 X 是否拥有市场 Y 的所有迁移？"
- "哪些市场缺少这个新功能？"

差异在生产环境发生 bug 之前是不可见的。

### 1.2 症状

- **手动比较**：开发人员并排读取配置文件，容易出错且缓慢
- **影响未知**：配置变更在不知道哪些市场行为变化的情况下被合并
- **行为不一致**：市场在不知不觉中漂移（例如一个市场缺少 guard 条件）
- **无文档**：没有"每个市场支持什么"的单一真相源

### 1.3 目标

- 自动生成任意两个市场（或版本）之间的结构化差异报告
- 生成显示每个市场中哪些迁移处于活动状态的兼容性矩阵
- 从矩阵自动生成测试用例
- 当基础变更意外影响市场时告警

---

## 2. 设计概述

### 2.1 两个核心工具

```
┌─────────────────────────────────────────────────────────────┐
│  工具 1: 配置差异报告器                                │
│  输入: 两个市场名称（或两个配置版本）           │
│  输出: 结构化差异报告（阈值、开关、       │
│          动作、受影响的迁移）                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  工具 2: 兼容性矩阵生成器                      │
│  输入: 所有市场 + 状态机定义               │
│  输出: 矩阵（状态 × 事件）显示目标状态      │
│          或每个市场的"不可用"                    │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 数据流

```
状态机定义（迁移 + guard）
        +
市场配置（合并后，每市场）
        ↓
迁移解析器（按市场评估 guard）
        ↓
有效迁移映射（market → (state,event) → targetState）
        ↓
┌─────────────┐    ┌──────────────────┐
│ 差异报告 │    │ 兼容性    │
│ (2 市场) │    │ 矩阵 (所有)     │
└─────────────┘    └──────────────────┘
        ↓                   ↓
   文档         测试生成器
   (自动生成)    (自动生成)
```

---

## 3. 详细设计

### 3.1 迁移解析器

给定状态机定义和市场配置，解析哪些迁移处于活动状态：

```java
public class TransitionResolver {

    /**
     * 解析给定市场配置的所有有效迁移。
     * 返回映射: (sourceState, event) → targetState（仅活动迁移）
     */
    public Map<TransitionKey, ConversationState> resolve(
            StateMachine<ConversationState, ConversationFact, CbolStateContext> machine,
            StateMachineMarketConfig config) {

        Map<TransitionKey, ConversationState> effective = new HashMap<>();

        for (Transition<ConversationState, ConversationFact, CbolStateContext> t :
             machine.getAllTransitions()) {

            // 用市场配置构建测试上下文
            CbolStateContext testCtx = CbolStateContext.builder()
                    .conversation(ConversationInstance.builder()
                            .conversationId("resolve-test")
                            .market("test")
                            .state(t.getSourceState())
                            .build())
                    .marketConfig(config)
                    .build();

            // 评估 guard（如果有）
            boolean guardPassed = t.getGuard() == null || t.getGuard().evaluate(testCtx);

            if (guardPassed) {
                effective.put(
                        new TransitionKey(t.getSourceState(), t.getEvent()),
                        t.getTargetState());
            }
        }
        return effective;
    }

    public record TransitionKey(ConversationState state, ConversationFact event) {}
}
```

### 3.2 配置差异报告器

#### 3.2.1 差异报告结构

```java
public class MarketDiffReport {
    private final String marketA;
    private final String marketB;
    private final List<ConfigDifference> differences;
    private final List<TransitionDifference> transitionDifferences;

    public record ConfigDifference(
        String category,        // "timeouts", "features", "businessRules", "connectors"
        String key,             // "customerIdleSeconds"
        Object valueA,
        Object valueB,
        String sourceA,         // "base", "apac", "hk"
        String sourceB,
        String impact           // "no transition affected", "3 transitions affected"
    ) {}

    public record TransitionDifference(
        ConversationState sourceState,
        ConversationFact event,
        ConversationState targetA,    // null = 在 A 中不可用
        ConversationState targetB,    // null = 在 B 中不可用
        String reason                  // "guard: surveyEnabled=false in B"
    ) {}
}
```

#### 3.2.2 示例差异报告（HK vs UK）

```
=== 市场差异报告: HK vs UK ===
生成时间: 2026-09-01

─── 配置差异 (12) ───

[超时]
  customerIdleSeconds:    HK=240 (apac)    UK=600 (uk)     [+150%]
  transferTimeoutSeconds: HK=180 (base)    UK=240 (uk)     [+33%]
  endingGraceSeconds:     HK=120 (base)    UK=180 (uk)     [+50%]

[功能]
  genesysEnabled:         HK=true  (hk)     UK=false (uk)    [不同]
  surveyEnabled:          HK=true  (hk)     UK=true  (uk)    [值相同，来源不同]
  regulatoryAuditEnabled: HK=true  (apac)   UK=false (base)  [不同]

[业务规则]
  transferTarget:         HK=GENESYS (hk)   UK=INTERNAL_QUEUE (uk)  [不同]
  surveyType:             HK=CSAT (base)    UK=NPS (uk)     [不同]
  fallbackRoutingStrategy: HK=REQUEUE (apac) UK=FALLBACK_QUEUE (uk) [不同]
  maxTransferRetries:     HK=3 (hk)         UK=2 (uk)       [不同]

─── 迁移差异 (5) ───

  状态              | 事件               | HK 目标      | UK 目标      | 原因
  -------------------|---------------------|----------------|----------------|---------------------------
  IN_PROGRESS             | TRANSFER_REQUEST    | TRANSFERRED    | TRANSFERRED    | (相同)
  IN_PROGRESS             | SURVEY_START        | SURVEY_IN_PROG | SURVEY_IN_PROG | (相同)
  TRANSFERRED        | TRANSFER_CONNECTED  | IN_PROGRESS         | 不可用    | guard: genesysEnabled=false in UK
  TRANSFERRED        | TRANSFER_FAILED     | INITIATED      | INITIATED      | (相同)
  IN_PROGRESS | SURVEY_COMPLETE    | ENDING         | ENDING         | (相同)

─── 摘要 ───
  配置差异: 12（8 个高影响，4 个低影响）
  迁移差异: 1（UK 缺少 TRANSFER_CONNECTED → IN_PROGRESS）
  市场共享: 23 个迁移中的 22 个（95.7% 相似度）
  建议: UK 缺少的 TRANSFER_CONNECTED 是预期的（无 Genesys）。无需操作。
```

### 3.3 兼容性矩阵生成器

#### 3.3.1 矩阵格式

行 = 所有状态，列 = 所有事件，单元格 = 每市场的目标状态。

```
=== 状态机兼容性矩阵 ===
市场: HK, SG, UK, US, JP

─── 状态: INITIATED ───
  事件              | HK         | SG         | UK         | US         | JP
  -------------------|------------|------------|------------|------------|------------
  CUSTOMER_CONNECT   | IN_PROGRESS     | IN_PROGRESS     | IN_PROGRESS     | IN_PROGRESS     | IN_PROGRESS
  SYS_CUSTOMER_IDLE  | ENDING     | ENDING     | ENDING     | ENDING     | ENDING
  SYS_ACTION_FAILED  | ERROR      | ERROR      | ERROR      | ERROR      | ERROR

─── 状态: IN_PROGRESS ───
  事件              | HK         | SG         | UK         | US         | JP
  -------------------|------------|------------|------------|------------|------------
  TRANSFER_REQUEST   | TRANSFERRED| TRANSFERRED| TRANSFERRED| TRANSFERRED| TRANSFERRED
  CUSTOMER_CLOSE     | ENDING     | ENDING     | ENDING     | ENDING     | ENDING
  SURVEY_START       | SURVEY     | —          | SURVEY     | SURVEY     | —
  SYS_CUSTOMER_IDLE  | ENDING     | ENDING     | ENDING     | ENDING     | ENDING
  SYS_ACTION_FAILED  | ERROR      | ERROR      | ERROR      | ERROR      | ERROR

  图例: — = 迁移不可用（guard 返回 false）

─── 状态: TRANSFERRED ───
  事件              | HK         | SG         | UK         | US         | JP
  -------------------|------------|------------|------------|------------|------------
  TRANSFER_CONNECTED | IN_PROGRESS     | —          | —          | —          | —
  TRANSFER_FAILED    | INITIATED  | INITIATED  | INITIATED  | INITIATED  | INITIATED
  TRANSFER_TIMEOUT   | INITIATED  | INITIATED  | INITIATED  | INITIATED  | INITIATED
  SURVEY_START       | SURVEY     | —          | SURVEY     | SURVEY     | —
  SYS_ACTION_FAILED  | ERROR      | ERROR      | ERROR      | ERROR      | ERROR

─── 覆盖率摘要 ───
  市场 | 活动迁移 | 总数 | 覆盖率 | 缺少（预期）
  -------|-------------------|-------|----------|------------------
  HK     | 23                | 23    | 100%     | —
  SG     | 19                | 23    | 82.6%    | SURVEY_START × 2, TRANSFER_CONNECTED × 2
  UK     | 21                | 23    | 91.3%    | TRANSFER_CONNECTED × 2
  US     | 21                | 23    | 91.3%    | TRANSFER_CONNECTED × 2
  JP     | 19                | 23    | 82.6%    | SURVEY_START × 2, TRANSFER_CONNECTED × 2
```

### 3.4 自动生成测试用例

从兼容性矩阵自动生成参数化测试：

```java
// 从兼容性矩阵自动生成
class MarketCompatibilityTest {

    @ParameterizedTest
    @MethodSource("allActiveTransitions")
    void shouldTransitionSuccessfully(String market, ConversationState from,
                                        ConversationFact event, ConversationState expectedTo) {
        StateMachineMarketConfig config = configLoader.load(market);
        ConversationState result = fireEvent(config, from, event);
        assertEquals(expectedTo, result);
    }

    @ParameterizedTest
    @MethodSource("allInactiveTransitions")
    void shouldRejectInactiveTransition(String market, ConversationState from, ConversationFact event) {
        StateMachineMarketConfig config = configLoader.load(market);
        assertThrows(StateMachineException.class, () -> fireEvent(config, from, event));
    }

    // 从 TransitionResolver 输出生成的测试数据
    static Stream<Arguments> allActiveTransitions() {
        return matrixGenerator.generate()
                .activeTransitions()
                .stream()
                .map(t -> Arguments.of(t.market(), t.from(), t.event(), t.to()));
    }
}
```

### 3.5 CI 集成

```yaml
# .github/workflows/config-diff.yml
name: 配置差异分析

on:
  pull_request:
    paths:
      - 'config/**/*.yaml'

jobs:
  diff-analysis:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: 生成差异报告
        run: ./mvnw exec:java -Dexec.mainClass="com.example.ConfigDiffTool" -Dexec.args="--pr"
      - name: 生成兼容性矩阵
        run: ./mvnw exec:java -Dexec.mainClass="com.example.MatrixGenerator"
      - name: 将差异作为 PR 评论发布
        uses: actions/github-script@v7
        with:
          script: |
            const diff = require('./diff-report.md');
            const matrix = require('./compatibility-matrix.md');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              body: `## 配置变更影响\n\n${diff}\n\n<details><summary>兼容性矩阵</summary>\n\n${matrix}\n</details>`
            });
```

---

## 4. 实施路线图

### 阶段 1：迁移解析器（0.5 天）
- [ ] 实现带 guard 评估的 `TransitionResolver`
- [ ] 实现 `EffectiveTransitionMap` 数据结构
- [ ] guard 解析的单元测试（true/false/null guard）

### 阶段 2：差异报告器（1 天）
- [ ] 实现 `MarketDiffReport` 数据结构
- [ ] 实现配置级差异（标量、map、列表）
- [ ] 实现迁移级差异（使用 TransitionResolver）
- [ ] 实现 markdown 报告生成器
- [ ] 差异场景的单元测试

### 阶段 3：兼容性矩阵（0.5 天）
- [ ] 实现 `CompatibilityMatrixGenerator`
- [ ] 实现 markdown 矩阵生成器
- [ ] 实现覆盖率摘要（每市场活动/总数）
- [ ] 矩阵生成的单元测试

### 阶段 4：测试生成器与 CI（1 天）
- [ ] 实现自动生成测试用例生成器
- [ ] 将差异报告作为 CI 中的 PR 评论集成
- [ ] 在 CI 中集成矩阵生成
- [ ] 添加"冗余覆盖"检测（市场值 == 基础值）

---

## 5. 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|-----------|--------|------------|
| Guard 评估有副作用（不是纯函数） | 中 | 高 | Guard 必须是纯函数（无 I/O，无状态变更）；通过代码审查和测试强制执行 |
| 差异报告过于冗长，重要变更被埋没 | 中 | 中 | 按影响分类（高/中/低），首先突出迁移差异 |
| 许多市场的矩阵生成缓慢 | 低 | 低 | 缓存有效迁移映射；配置相同的市场共享结果 |
| 自动生成测试不稳定（guard 依赖外部状态） | 中 | 高 | Guard 仅从配置 + 上下文读取；测试上下文完全受控 |
| 冗余覆盖检测导致误报 | 低 | 中 | 标记为警告，不是错误；允许在配置注释中使用 `@IntentionalOverride` 注解 |

---

## 6. 成功标准

- [ ] 运行 `diff HK UK` 在 < 1 秒内生成结构化报告
- [ ] 兼容性矩阵显示每个市场的每个（状态，事件）组合
- [ ] 矩阵中的每个活动迁移都有对应的自动生成测试
- [ ] 配置 PR 自动包含影响分析报告作为评论
- [ ] 冗余覆盖（市场值 == 基础值）被标记
- [ ] 缺少的迁移用 guard 原因解释（例如 "genesysEnabled=false"）
