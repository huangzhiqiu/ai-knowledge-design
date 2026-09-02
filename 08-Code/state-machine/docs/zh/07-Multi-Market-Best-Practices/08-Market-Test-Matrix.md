# 08. 市场测试矩阵

> 版本：1.0 | 最后更新：2026-09-01
> 优先级：P3 | 预估工作量：2-3 天

## 1. 问题陈述

### 1.1 当前问题

由于多个市场各有不同的配置，测试变成了组合爆炸：
- 市场 × 状态 × 事件 × 配置变体 = 数千个测试用例
- 手动编写测试缓慢、容易出错且不完整
- 不清楚哪些测试用例实际需要，哪些是冗余的
- 市场特定行为是临时测试的，不是系统性的
- 对基础配置的更改需要重新测试所有市场

### 1.2 场景

- "HK 的 TRANSFER_REQUEST → TRANSFERRED 迁移在 Genesys 下工作吗？"
- "SG 的 SURVEY_START 正确拒绝了吗（guard=false）？"
- "UK 的回退路由在转接失败时工作吗？"
- "新的基础配置更改是否破坏了任何市场的预期行为？"
- "是否有任何迁移从未针对特定市场进行过测试？"

### 1.3 目标

- 跨所有市场、状态和事件的系统性测试覆盖
- 从配置 + 状态机定义自动生成测试用例
- 清晰的测试分类（核心 vs 市场特定 vs 边界情况）
- 高效执行（避免冗余测试）
- 每市场的测试覆盖指标

---

## 2. 设计概述

### 2.1 测试矩阵维度

```
┌─────────────────────────────────────────────────────────────────┐
│                      测试矩阵维度                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  维度 1: 市场 (M)                                          │
│    HK, SG, UK, US, JP, ...                                       │
│                                                                   │
│  维度 2: 状态 (S)                                           │
│    INITIATED, ACTIVE, TRANSFERRED, SURVEY_IN_PROGRESS,          │
│    ENDING, ERROR, CLOSED                                          │
│                                                                   │
│  维度 3: 事件 (E)                                           │
│    CUSTOMER_CONNECT, TRANSFER_REQUEST, SURVEY_START, ...         │
│                                                                   │
│  维度 4: 配置变体 (V)                                  │
│    默认、启用满意度调查、启用 Genesys、禁用转接    │
│                                                                   │
│  总可能数: M × S × E × V（数千）                       │
│  但大多数是无效的（无迁移）→ 过滤为有效的     │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 测试分类

```
┌─────────────────────────────────────────────────────────────────┐
│                      测试分类                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  L1: 核心状态机测试（市场无关）                │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • 所有迁移工作（从任何状态，用任何事件）    │  │
│  │ • 无效迁移被拒绝                           │  │
│  │ • Guard 正确评估                                  │  │
│  │ • 动作执行并产生预期结果               │  │
│  │ • 状态机不变量保持                              │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
│  L2: 市场配置测试（每市场）                             │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • 每个市场的配置正确加载                       │  │
│  │ • 配置合并（基础 → 区域 → 市场）正确        │  │
│  │ • 市场特定 guard 按预期评估                │  │
│  │ • 市场特定动作正确执行                   │  │
│  │ • 配置值在有效范围内                      │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
│  L3: 市场行为测试（每市场）                           │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • 此市场的活跃迁移工作                    │  │
│  │ • 非活跃迁移（guard=false）被拒绝            │  │
│  │ • 此市场的端到端会话流程                │  │
│  │ • 市场特定错误处理                              │  │
│  │ • 市场特定连接器集成                       │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
│  L4: 跨市场比较测试                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • 配置相同的市场行为相同                 │  │
│  │ • 配置不同的市场按预期行为            │  │
│  │ • 没有市场有意外的行为差异                │  │
│  │ • 配置更改影响在各市场间一致            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
│  L5: 故障注入与弹性测试                           │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • 动作失败 → 故障转移 → ERROR 状态                     │  │
│  │ • 熔断器打开 → 降级行为                    │  │
│  │ • 配置无效 → 回退到默认                        │  │
│  │ • 并发事件 → 状态一致性                       │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 详细设计

### 3.1 自动生成的测试用例

#### 3.1.1 测试用例生成器

```java
public class MarketTestCaseGenerator {

    private final TransitionResolver transitionResolver;
    private final ConfigProvider configProvider;

    /**
     * 为给定市场生成所有测试用例。
     * 返回测试用例列表: (market, sourceState, event, expectedTarget, isActive)
     */
    public List<MarketTestCase> generateForMarket(String market) {
        StateMachineMarketConfig config = configProvider.getConfig(market);
        StateMachine<...> machine = getStateMachine();
        Map<TransitionKey, ConversationState> effective =
                transitionResolver.resolve(machine, config);

        List<MarketTestCase> cases = new ArrayList<>();

        for (ConversationState state : ConversationState.values()) {
            for (ConversationFact event : ConversationFact.values()) {
                TransitionKey key = new TransitionKey(state, event);
                if (effective.containsKey(key)) {
                    // 活跃迁移: 应该成功
                    cases.add(MarketTestCase.active(market, state, event, effective.get(key)));
                } else {
                    // 非活跃迁移: 应该被拒绝
                    // 仅为非终态生成（终态总是拒绝）
                    if (!isTerminal(state)) {
                        cases.add(MarketTestCase.inactive(market, state, event));
                    }
                }
            }
        }
        return cases;
    }

    /**
     * 为所有市场生成测试用例。
     */
    public List<MarketTestCase> generateAll() {
        List<MarketTestCase> all = new ArrayList<>();
        for (String market : configProvider.listMarkets()) {
            all.addAll(generateForMarket(market));
        }
        return all;
    }
}

public record MarketTestCase(
    String market,
    ConversationState sourceState,
    ConversationFact event,
    ConversationState expectedTarget,  // 如果非活跃则为 null
    boolean isActive,
    String description  // 自动生成的人类可读描述
) {
    public static MarketTestCase active(String market, ConversationState from,
                                          ConversationFact event, ConversationState to) {
        return new MarketTestCase(market, from, event, to, true,
                String.format("[%s] %s --%s--> %s", market, from, event, to));
    }

    public static MarketTestCase inactive(String market, ConversationState from, ConversationFact event) {
        return new MarketTestCase(market, from, event, null, false,
                String.format("[%s] %s --%s--> REJECTED", market, from, event));
    }
}
```

#### 3.1.2 参数化测试执行

```java
class MarketBehaviorTest {

    private static MarketTestCaseGenerator generator;
    private static StateMachineProcessor processor;

    @BeforeAll
    static void setUp() {
        generator = new MarketTestCaseGenerator();
        processor = new StateMachineProcessor();
    }

    @ParameterizedTest
    @MethodSource("allActiveTransitions")
    void shouldTransitionSuccessfully(MarketTestCase testCase) {
        StateMachineMarketConfig config = loadConfig(testCase.market());
        StateContext<...> result = processor.fireEvent(
                testCase.sourceState(), testCase.event(), config);

        assertTrue(result.isTransitionAccepted(),
                "Expected transition accepted: " + testCase.description());
        assertEquals(testCase.expectedTarget(), result.getTargetState(),
                "Unexpected target state: " + testCase.description());
    }

    @ParameterizedTest
    @MethodSource("allInactiveTransitions")
    void shouldRejectInactiveTransition(MarketTestCase testCase) {
        StateMachineMarketConfig config = loadConfig(testCase.market());

        assertThrows(StateMachineException.class, () ->
                        processor.fireEvent(testCase.sourceState(), testCase.event(), config),
                "Expected transition rejected: " + testCase.description());
    }

    static Stream<MarketTestCase> allActiveTransitions() {
        return generator.generateAll().stream().filter(MarketTestCase::isActive);
    }

    static Stream<MarketTestCase> allInactiveTransitions() {
        return generator.generateAll().stream().filter(t -> !t.isActive());
    }
}
```

### 3.2 端到端会话流程测试

```java
public class MarketEndToEndTest {

    @ParameterizedTest
    @MethodSource("allMarkets")
    void shouldCompleteFullConversationLifecycle(String market) {
        StateMachineMarketConfig config = loadConfig(market);
        String conversationId = "test-" + market + "-" + System.nanoTime();

        // 1. 连接
        assertEquals(ACTIVE, fire(config, INITIATED, CUSTOMER_CONNECT));

        // 2. 转接（如果启用）
        if (config.transferEnabled()) {
            assertEquals(TRANSFERRED, fire(config, ACTIVE, TRANSFER_REQUEST));

            // 3. 转接结果
            // （可能是 CONNECTED、FAILED 或 TIMEOUT — 分别测试）
        }

        // 4. 关闭
        if (config.surveyEnabled()) {
            assertEquals(SURVEY_IN_PROGRESS, fire(config, ACTIVE, SURVEY_START));
            assertEquals(ENDING, fire(config, SURVEY_IN_PROGRESS, SURVEY_COMPLETE));
        } else {
            assertEquals(ENDING, fire(config, ACTIVE, CUSTOMER_CLOSE));
        }

        // 5. 关闭宽限期
        assertEquals(CLOSED, fire(config, ENDING, SYS_ENDING_GRACE_TIMEOUT));
    }

    @ParameterizedTest
    @MethodSource("marketsWithSurvey")
    void shouldHandleSurveyTimeout(String market) {
        StateMachineMarketConfig config = loadConfig(market);

        assertEquals(SURVEY_IN_PROGRESS, fire(config, ACTIVE, SURVEY_START));
        assertEquals(ENDING, fire(config, SURVEY_IN_PROGRESS, SYS_SURVEY_TIMEOUT));
    }

    @ParameterizedTest
    @MethodSource("marketsWithGenesys")
    void shouldHandleGenesysTransferFlow(String market) {
        StateMachineMarketConfig config = loadConfig(market);

        assertEquals(TRANSFERRED, fire(config, ACTIVE, TRANSFER_REQUEST));
        assertEquals(ACTIVE, fire(config, TRANSFERRED, TRANSFER_CONNECTED));
    }

    static Stream<String> allMarkets() {
        return Stream.of("HK", "SG", "UK", "US", "JP");
    }

    static Stream<String> marketsWithSurvey() {
        return allMarkets().filter(m -> loadConfig(m).surveyEnabled());
    }

    static Stream<String> marketsWithGenesys() {
        return allMarkets().filter(m -> loadConfig(m).genesysEnabled());
    }
}
```

### 3.3 跨市场比较测试

```java
public class CrossMarketComparisonTest {

    @Test
    void marketsWithSameConfigShouldBehaveIdentically() {
        // 查找具有相同有效配置的市场
        Map<MergedMarketConfig, List<String>> configGroups = groupByConfig();

        for (Map.Entry<MergedMarketConfig, List<String>> entry : configGroups.entrySet()) {
            List<String> markets = entry.getValue();
            if (markets.size() < 2) continue;

            // 对于每个迁移，验证组中的所有市场行为相同
            for (ConversationState state : ConversationState.values()) {
                for (ConversationFact event : ConversationFact.values()) {
                    Set<ConversationState> targets = new HashSet<>();
                    for (String market : markets) {
                        StateMachineMarketConfig config = loadConfig(market);
                        try {
                            targets.add(fire(config, state, event).getTargetState());
                        } catch (StateMachineException e) {
                            targets.add(null); // null = 拒绝
                        }
                    }
                    assertEquals(1, targets.size(),
                            "Markets " + markets + " behave differently for " + state + " + " + event);
                }
            }
        }
    }

    @Test
    void configChangeShouldOnlyAffectExpectedMarkets() {
        // 模拟基础配置更改并验证影响
        // （使用来自文档 01 的 ConfigImpactAnalyzer）
        ConfigImpactAnalyzer analyzer = new ConfigImpactAnalyzer();
        ImpactReport report = analyzer.analyzeImpact("base", "features.surveyEnabled", true);

        // 显式覆盖 surveyEnabled=false 的市场不应受影响
        for (MarketImpact impact : report.affectedMarkets()) {
            if ("SG".equals(impact.market())) {
                // SG 显式设置 surveyEnabled=false，应保持 false
                assertFalse(impact.after().surveyEnabled());
            }
        }
    }
}
```

### 3.4 测试覆盖指标

```java
public class MarketTestCoverageReporter {

    public CoverageReport generateReport() {
        List<MarketTestCase> allCases = generator.generateAll();
        Set<String> testedCases = loadTestedCasesFromTestResults();

        long total = allCases.size();
        long tested = allCases.stream().filter(testedCases::contains).count();
        long untested = total - tested;

        // 每市场细分
        Map<String, MarketCoverage> perMarket = new HashMap<>();
        for (String market : listMarkets()) {
            long marketTotal = allCases.stream().filter(c -> c.market().equals(market)).count();
            long marketTested = allCases.stream()
                    .filter(c -> c.market().equals(market))
                    .filter(testedCases::contains)
                    .count();
            perMarket.put(market, new MarketCoverage(market, marketTotal, marketTested));
        }

        // 未测试用例（用于优先级排序）
        List<MarketTestCase> untestedCases = allCases.stream()
                .filter(c -> !testedCases.contains(c))
                .toList();

        return new CoverageReport(total, tested, untested, perMarket, untestedCases);
    }
}

public record CoverageReport(
    long totalCases,
    long testedCases,
    long untestedCases,
    Map<String, MarketCoverage> perMarket,
    List<MarketTestCase> untestedCases
) {
    public double coveragePercentage() {
        return totalCases == 0 ? 100.0 : (testedCases * 100.0 / totalCases);
    }
}
```

### 3.5 示例覆盖报告

```
=== 市场测试覆盖报告 ===
生成时间: 2026-09-01

总体: 312 / 350 个用例已测试 (89.1%)

─── 每市场细分 ───
  市场 | 总数 | 已测试 | 覆盖率 | 未测试
  -------|-------|--------|----------|----------
  HK     | 70    | 70     | 100%     | 0
  SG     | 56    | 50     | 89.3%    | 6
  UK     | 63    | 55     | 87.3%    | 8
  US     | 63    | 60     | 95.2%    | 3
  JP     | 56    | 47     | 83.9%    | 9
  DEFAULT| 42    | 30     | 71.4%    | 12

─── 未测试用例（最高优先级） ───
  1. [JP] TRANSFERRED --TRANSFER_CONNECTED--> REJECTED (genesysEnabled=false)
  2. [UK] SURVEY_IN_PROGRESS --SURVEY_COMPLETE--> ENDING
  3. [SG] ACTIVE --SURVEY_START--> REJECTED (surveyEnabled=false)
  ...

─── 建议 ───
  • 为 DEFAULT 市场添加测试（覆盖率最低: 71.4%）
  • 为 JP 添加非活跃迁移测试（Genesys 已禁用）
  • 为 UK 添加满意度调查流程测试
  • 考虑自动生成非活跃迁移测试（目前是手动的）
```

---

## 4. 实施路线图

### 阶段 1：测试用例生成器（1 天）
- [ ] 实现 `MarketTestCase` record
- [ ] 实现 `MarketTestCaseGenerator`（活跃/非活跃迁移）
- [ ] 与 `TransitionResolver` 集成（来自文档 02）
- [ ] 生成器正确性的单元测试

### 阶段 2：参数化测试（1 天）
- [ ] 实现 `MarketBehaviorTest`（活跃/非活跃迁移）
- [ ] 实现 `MarketEndToEndTest`（每市场完整生命周期）
- [ ] 实现市场特定测试方法（满意度调查、Genesys、转接）
- [ ] 验证所有生成的测试通过

### 阶段 3：跨市场与覆盖（0.5 天）
- [ ] 实现 `CrossMarketComparisonTest`
- [ ] 实现 `MarketTestCoverageReporter`
- [ ] 在 CI 中生成覆盖报告
- [ ] 添加覆盖阈值检查（例如 > 85%）

### 阶段 4：故障注入与高级（0.5 天）
- [ ] 实现每市场的故障转移测试
- [ ] 实现每市场的熔断器测试
- [ ] 实现配置无效 → 回退测试
- [ ] 实现并发事件测试

---

## 5. 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|-----------|--------|------------|
| 测试用例太多（执行时间太长） | 中 | 中 | 过滤为有意义的用例（跳过终态，跳过重复配置）；并行测试执行；按优先级分类 |
| 自动生成的测试不稳定 | 中 | 高 | 确定性测试数据；生成的测试中无外部依赖；随机数据的固定种子 |
| 测试生成器本身有 bug（生成错误用例） | 中 | 高 | 用已知配置测试生成器；手动审查生成的用例；将生成的用例与兼容性矩阵进行比较 |
| 覆盖指标具有误导性（测试存在但断言不正确） | 低 | 中 | 变异测试；测试审查；同时断言接受和目标状态 |
| 市场配置更改破坏测试（测试维护负担） | 中 | 中 | 测试动态读取配置（不硬编码）；测试断言相对于配置的行为，而不是绝对值 |
| 跨市场比较测试缓慢 | 低 | 低 | 在夜间管道中运行，而不是每个 PR；为 PR 检查抽样市场 |

---

## 6. 成功标准

- [ ] 测试用例生成器为所有市场 × 状态 × 事件生成用例
- [ ] 活跃迁移测试同时断言接受和目标状态
- [ ] 非活跃迁移测试断言拒绝
- [ ] 每个市场都存在端到端生命周期测试
- [ ] 跨市场比较测试验证相同配置的一致行为
- [ ] 在 CI 中生成带每市场细分的覆盖报告
- [ ] 总体测试覆盖 > 85%
- [ ] 没有市场的覆盖率 < 70%
- [ ] 测试套件在 < 2 分钟内执行（并行）
- [ ] 新市场自动获得测试用例（无需手动编写测试）
