# 08. Market Test Matrix

> Version: 1.0 | Last Updated: 2026-09-01
> Priority: P3 | Estimated Effort: 2-3 days

## 1. Problem Statement

### 1.1 Current Issue
With multiple markets each having different configs, testing becomes a combinatorial explosion:
- Markets × States × Events × Config variants = thousands of test cases
- Manual test writing is slow, error-prone, and incomplete
- It's unclear which test cases are actually needed vs. redundant
- Market-specific behavior is tested ad-hoc, not systematically
- A change to base config requires re-testing all markets

### 1.2 Scenarios
- "Does HK's TRANSFER_REQUEST → TRANSFERRED transition work with Genesys?"
- "Does SG's SURVEY_START correctly reject (guard=false)?"
- "Does UK's fallback routing work when transfer fails?"
- "Does the new base config change break any market's expected behavior?"
- "Are there any transitions that are never tested for a specific market?"

### 1.3 Goals
- Systematic test coverage across all markets, states, and events
- Auto-generate test cases from config + state machine definition
- Clear test categorization (core vs. market-specific vs. edge cases)
- Efficient execution (avoid redundant tests)
- Test coverage metrics per market

---

## 2. Design Overview

### 2.1 Test Matrix Dimensions

```
┌─────────────────────────────────────────────────────────────────┐
│                      Test Matrix Dimensions                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Dimension 1: Market (M)                                          │
│    HK, SG, UK, US, JP, ...                                       │
│                                                                   │
│  Dimension 2: State (S)                                           │
│    INITIATED, ACTIVE, TRANSFERRED, SURVEY_IN_PROGRESS,          │
│    ENDING, ERROR, CLOSED                                          │
│                                                                   │
│  Dimension 3: Event (E)                                           │
│    CUSTOMER_CONNECT, TRANSFER_REQUEST, SURVEY_START, ...         │
│                                                                   │
│  Dimension 4: Config Variant (V)                                  │
│    Default, survey-enabled, genesys-enabled, transfer-disabled    │
│                                                                   │
│  Total possible: M × S × E × V (thousands)                       │
│  But most are invalid (no transition) → filter to valid ones     │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Test Categories

```
┌─────────────────────────────────────────────────────────────────┐
│                      Test Categories                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  L1: Core State Machine Tests (market-independent)                │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • All transitions work (from any state, with any event)    │  │
│  │ • Invalid transitions are rejected                           │  │
│  │ • Guards evaluate correctly                                  │  │
│  │ • Actions execute and produce expected results               │  │
│  │ • State machine invariants hold                              │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
│  L2: Market Config Tests (per market)                             │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • Each market's config loads correctly                       │  │
│  │ • Config merge (base → regional → market) is correct        │  │
│  │ • Market-specific guards evaluate as expected                │  │
│  │ • Market-specific actions execute correctly                   │  │
│  │ • Config values are within valid ranges                      │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
│  L3: Market Behavior Tests (per market)                           │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • Active transitions for this market work                    │  │
│  │ • Inactive transitions (guard=false) are rejected            │  │
│  │ • End-to-end conversation flow for this market                │  │
│  │ • Market-specific error handling                              │  │
│  │ • Market-specific connector integration                       │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
│  L4: Cross-Market Comparison Tests                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • Markets with same config behave identically                 │  │
│  │ • Markets with different config behave as expected            │  │
│  │ • No market has unexpected behavior differences                │  │
│  │ • Config change impact is consistent across markets            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
│  L5: Fault Injection & Resilience Tests                           │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • Action failure → failover → ERROR state                     │  │
│  │ • Circuit breaker open → degraded behavior                    │  │
│  │ • Config invalid → fallback to default                        │  │
│  │ • Concurrent events → state consistency                       │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Detailed Design

### 3.1 Auto-Generated Test Cases

#### 3.1.1 Test Case Generator

```java
public class MarketTestCaseGenerator {

    private final TransitionResolver transitionResolver;
    private final ConfigProvider configProvider;

    /**
     * Generates all test cases for a given market.
     * Returns a list of test cases: (market, sourceState, event, expectedTarget, isActive)
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
                    // Active transition: should succeed
                    cases.add(MarketTestCase.active(market, state, event, effective.get(key)));
                } else {
                    // Inactive transition: should be rejected
                    // Only generate for non-terminal states (terminal states always reject)
                    if (!isTerminal(state)) {
                        cases.add(MarketTestCase.inactive(market, state, event));
                    }
                }
            }
        }
        return cases;
    }

    /**
     * Generates test cases for all markets.
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
    ConversationState expectedTarget,  // null if inactive
    boolean isActive,
    String description  // auto-generated human-readable description
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

#### 3.1.2 Parameterized Test Execution

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

### 3.2 End-to-End Conversation Flow Tests

```java
public class MarketEndToEndTest {

    @ParameterizedTest
    @MethodSource("allMarkets")
    void shouldCompleteFullConversationLifecycle(String market) {
        StateMachineMarketConfig config = loadConfig(market);
        String conversationId = "test-" + market + "-" + System.nanoTime();

        // 1. Connect
        assertEquals(ACTIVE, fire(config, INITIATED, CUSTOMER_CONNECT));

        // 2. Transfer (if enabled)
        if (config.transferEnabled()) {
            assertEquals(TRANSFERRED, fire(config, ACTIVE, TRANSFER_REQUEST));

            // 3. Transfer outcome
            // (could be CONNECTED, FAILED, or TIMEOUT — test each separately)
        }

        // 4. Close
        if (config.surveyEnabled()) {
            assertEquals(SURVEY_IN_PROGRESS, fire(config, ACTIVE, SURVEY_START));
            assertEquals(ENDING, fire(config, SURVEY_IN_PROGRESS, SURVEY_COMPLETE));
        } else {
            assertEquals(ENDING, fire(config, ACTIVE, CUSTOMER_CLOSE));
        }

        // 5. Close grace
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

### 3.3 Cross-Market Comparison Tests

```java
public class CrossMarketComparisonTest {

    @Test
    void marketsWithSameConfigShouldBehaveIdentically() {
        // Find markets with identical effective configs
        Map<MergedMarketConfig, List<String>> configGroups = groupByConfig();

        for (Map.Entry<MergedMarketConfig, List<String>> entry : configGroups.entrySet()) {
            List<String> markets = entry.getValue();
            if (markets.size() < 2) continue;

            // For each transition, verify all markets in group behave the same
            for (ConversationState state : ConversationState.values()) {
                for (ConversationFact event : ConversationFact.values()) {
                    Set<ConversationState> targets = new HashSet<>();
                    for (String market : markets) {
                        StateMachineMarketConfig config = loadConfig(market);
                        try {
                            targets.add(fire(config, state, event).getTargetState());
                        } catch (StateMachineException e) {
                            targets.add(null); // null = rejected
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
        // Simulate a base config change and verify impact
        // (uses ConfigImpactAnalyzer from doc 01)
        ConfigImpactAnalyzer analyzer = new ConfigImpactAnalyzer();
        ImpactReport report = analyzer.analyzeImpact("base", "features.surveyEnabled", true);

        // Markets that explicitly override surveyEnabled=false should NOT be affected
        for (MarketImpact impact : report.affectedMarkets()) {
            if ("SG".equals(impact.market())) {
                // SG explicitly sets surveyEnabled=false, should remain false
                assertFalse(impact.after().surveyEnabled());
            }
        }
    }
}
```

### 3.4 Test Coverage Metrics

```java
public class MarketTestCoverageReporter {

    public CoverageReport generateReport() {
        List<MarketTestCase> allCases = generator.generateAll();
        Set<String> testedCases = loadTestedCasesFromTestResults();

        long total = allCases.size();
        long tested = allCases.stream().filter(testedCases::contains).count();
        long untested = total - tested;

        // Per-market breakdown
        Map<String, MarketCoverage> perMarket = new HashMap<>();
        for (String market : listMarkets()) {
            long marketTotal = allCases.stream().filter(c -> c.market().equals(market)).count();
            long marketTested = allCases.stream()
                    .filter(c -> c.market().equals(market))
                    .filter(testedCases::contains)
                    .count();
            perMarket.put(market, new MarketCoverage(market, marketTotal, marketTested));
        }

        // Untested cases (for prioritization)
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

### 3.5 Example Coverage Report

```
=== Market Test Coverage Report ===
Generated: 2026-09-01

Overall: 312 / 350 cases tested (89.1%)

─── Per-Market Breakdown ───
  Market | Total | Tested | Coverage | Untested
  -------|-------|--------|----------|----------
  HK     | 70    | 70     | 100%     | 0
  SG     | 56    | 50     | 89.3%    | 6
  UK     | 63    | 55     | 87.3%    | 8
  US     | 63    | 60     | 95.2%    | 3
  JP     | 56    | 47     | 83.9%    | 9
  DEFAULT| 42    | 30     | 71.4%    | 12

─── Untested Cases (top priority) ───
  1. [JP] TRANSFERRED --TRANSFER_CONNECTED--> REJECTED (genesysEnabled=false)
  2. [UK] SURVEY_IN_PROGRESS --SURVEY_COMPLETE--> ENDING
  3. [SG] ACTIVE --SURVEY_START--> REJECTED (surveyEnabled=false)
  ...

─── Recommendations ───
  • Add tests for DEFAULT market (lowest coverage: 71.4%)
  • Add inactive transition tests for JP (genesys disabled)
  • Add survey flow tests for UK
  • Consider auto-generating inactive transition tests (currently manual)
```

---

## 4. Implementation Roadmap

### Phase 1: Test Case Generator (1 day)
- [ ] Implement `MarketTestCase` record
- [ ] Implement `MarketTestCaseGenerator` (active/inactive transitions)
- [ ] Integrate with `TransitionResolver` (from doc 02)
- [ ] Unit tests for generator correctness

### Phase 2: Parameterized Tests (1 day)
- [ ] Implement `MarketBehaviorTest` (active/inactive transitions)
- [ ] Implement `MarketEndToEndTest` (full lifecycle per market)
- [ ] Implement market-specific test methods (survey, genesys, transfer)
- [ ] Verify all generated tests pass

### Phase 3: Cross-Market & Coverage (0.5 day)
- [ ] Implement `CrossMarketComparisonTest`
- [ ] Implement `MarketTestCoverageReporter`
- [ ] Generate coverage report in CI
- [ ] Add coverage threshold check (e.g., > 85%)

### Phase 4: Fault Injection & Advanced (0.5 day)
- [ ] Implement failover tests per market
- [ ] Implement circuit breaker tests per market
- [ ] Implement config invalid → fallback tests
- [ ] Implement concurrent event tests

---

## 5. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Too many test cases (execution time too long) | Medium | Medium | Filter to meaningful cases (skip terminal states, skip duplicate configs); parallel test execution; categorize by priority |
| Auto-generated tests are flaky | Medium | High | Deterministic test data; no external dependencies in generated tests; fixed seed for random data |
| Test generator itself has bugs (generates wrong cases) | Medium | High | Test the generator with known configs; manual review of generated cases; compare generated cases against compatibility matrix |
| Coverage metric is misleading (tests exist but don't assert correctly) | Low | Medium | Mutation testing; test review; assert on both acceptance and target state |
| Market config changes break tests (test maintenance burden) | Medium | Medium | Tests read config dynamically (not hardcoded); tests assert behavior relative to config, not absolute values |
| Cross-market comparison tests are slow | Low | Low | Run in nightly pipeline, not on every PR; sample markets for PR checks |

---

## 6. Success Criteria

- [ ] Test case generator produces cases for all markets × states × events
- [ ] Active transition tests assert both acceptance and target state
- [ ] Inactive transition tests assert rejection
- [ ] End-to-end lifecycle test exists for every market
- [ ] Cross-market comparison test verifies consistent behavior for identical configs
- [ ] Coverage report generated in CI with per-market breakdown
- [ ] Overall test coverage > 85%
- [ ] No market has < 70% coverage
- [ ] Test suite executes in < 2 minutes (parallel)
- [ ] New market automatically gets test cases (no manual test writing needed)
