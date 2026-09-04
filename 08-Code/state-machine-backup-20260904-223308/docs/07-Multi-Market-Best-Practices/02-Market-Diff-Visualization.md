# 02. Market Diff Visualization & Compatibility Matrix

> Version: 1.0 | Last Updated: 2026-09-01
> Priority: P0 | Estimated Effort: 2-3 days

## 1. Problem Statement

### 1.1 Current Issue
With multiple markets each having their own config, there is no easy way to answer:
- "What exactly is different between HK and UK?"
- "If I change this migration rule, which markets are affected?"
- "Does market X have all the transitions that market Y has?"
- "Which markets are missing this new feature?"

Differences are invisible until a bug occurs in production.

### 1.2 Symptoms
- **Manual comparison**: Developers read config files side by side, error-prone and slow
- **Unknown impact**: Config changes merged without knowing which markets' behavior changes
- **Inconsistent behavior**: Markets drift apart unnoticed (e.g., one market missing a guard condition)
- **No documentation**: No single source of truth for "what each market supports"

### 1.3 Goals
- Automatically generate structured diff reports between any two markets (or versions)
- Generate a compatibility matrix showing which transitions are IN_PROGRESS in each market
- Auto-generate test cases from the matrix
- Alert when a base change affects markets unexpectedly

---

## 2. Design Overview

### 2.1 Two Core Tools

```
┌─────────────────────────────────────────────────────────────┐
│  Tool 1: Config Diff Reporter                                │
│  Input: two market names (or two config versions)           │
│  Output: structured diff report (thresholds, toggles,       │
│          actions, transitions affected)                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Tool 2: Compatibility Matrix Generator                      │
│  Input: all markets + state machine definition               │
│  Output: matrix (states × events) showing target state      │
│          or "unavailable" for each market                    │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Data Flow

```
State Machine Definition (transitions + guards)
        +
Market Configs (merged, per market)
        ↓
Transition Resolver (evaluates guards per market)
        ↓
Effective Transition Map (market → (state,event) → targetState)
        ↓
┌─────────────┐    ┌──────────────────┐
│ Diff Report │    │ Compatibility    │
│ (2 markets) │    │ Matrix (all)     │
└─────────────┘    └──────────────────┘
        ↓                   ↓
   Documentation         Test Generator
   (auto-generated)    (auto-generated)
```

---

## 3. Detailed Design

### 3.1 Transition Resolver

Given a state machine definition and a market config, resolve which transitions are IN_PROGRESS:

```java
public class TransitionResolver {

    /**
     * Resolves all effective transitions for a given market config.
     * Returns a map: (sourceState, event) → targetState (only for IN_PROGRESS transitions)
     */
    public Map<TransitionKey, ConversationState> resolve(
            StateMachine<ConversationState, ConversationFact, CbolStateContext> machine,
            StateMachineMarketConfig config) {

        Map<TransitionKey, ConversationState> effective = new HashMap<>();

        for (Transition<ConversationState, ConversationFact, CbolStateContext> t :
             machine.getAllTransitions()) {

            // Build a test context with the market config
            CbolStateContext testCtx = CbolStateContext.builder()
                    .conversation(ConversationInstance.builder()
                            .conversationId("resolve-test")
                            .market("test")
                            .state(t.getSourceState())
                            .build())
                    .marketConfig(config)
                    .build();

            // Evaluate guard (if any)
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

### 3.2 Config Diff Reporter

#### 3.2.1 Diff Report Structure

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
        ConversationState targetA,    // null = unavailable in A
        ConversationState targetB,    // null = unavailable in B
        String reason                  // "guard: surveyEnabled=false in B"
    ) {}
}
```

#### 3.2.2 Example Diff Report (HK vs UK)

```
=== Market Diff Report: HK vs UK ===
Generated: 2026-09-01

─── Config Differences (12) ───

[Timeouts]
  customerIdleSeconds:    HK=240 (apac)    UK=600 (uk)     [+150%]
  transferTimeoutSeconds: HK=180 (base)    UK=240 (uk)     [+33%]
  endingGraceSeconds:     HK=120 (base)    UK=180 (uk)     [+50%]

[Features]
  genesysEnabled:         HK=true  (hk)     UK=false (uk)    [DIFFERENT]
  surveyEnabled:          HK=true  (hk)     UK=true  (uk)    [same value, different source]
  regulatoryAuditEnabled: HK=true  (apac)   UK=false (base)  [DIFFERENT]

[Business Rules]
  transferTarget:         HK=GENESYS (hk)   UK=INTERNAL_QUEUE (uk)  [DIFFERENT]
  surveyType:             HK=CSAT (base)    UK=NPS (uk)     [DIFFERENT]
  fallbackRoutingStrategy: HK=REQUEUE (apac) UK=FALLBACK_QUEUE (uk) [DIFFERENT]
  maxTransferRetries:     HK=3 (hk)         UK=2 (uk)       [DIFFERENT]

─── Transition Differences (5) ───

  State              | Event               | HK target      | UK target      | Reason
  -------------------|---------------------|----------------|----------------|---------------------------
  IN_PROGRESS             | TRANSFER_REQUEST    | TRANSFERRED    | TRANSFERRED    | (same)
  IN_PROGRESS             | SURVEY_START        | SURVEY_IN_PROG | SURVEY_IN_PROG | (same)
  TRANSFERRED        | TRANSFER_CONNECTED  | IN_PROGRESS         | UNAVAILABLE    | guard: genesysEnabled=false in UK
  TRANSFERRED        | TRANSFER_FAILED     | INITIATED      | INITIATED      | (same)
  IN_PROGRESS | SURVEY_COMPLETE    | ENDING         | ENDING         | (same)

─── Summary ───
  Config differences: 12 (8 high-impact, 4 low-impact)
  Transition differences: 1 (UK missing TRANSFER_CONNECTED → IN_PROGRESS)
  Markets share: 22 of 23 transitions (95.7% similarity)
  Recommendation: UK's missing TRANSFER_CONNECTED is expected (no Genesys). No action needed.
```

### 3.3 Compatibility Matrix Generator

#### 3.3.1 Matrix Format

Rows = all states, Columns = all events, Cells = target state per market.

```
=== State Machine Compatibility Matrix ===
Markets: HK, SG, UK, US, JP

─── State: INITIATED ───
  Event              | HK         | SG         | UK         | US         | JP
  -------------------|------------|------------|------------|------------|------------
  CUSTOMER_CONNECT   | IN_PROGRESS     | IN_PROGRESS     | IN_PROGRESS     | IN_PROGRESS     | IN_PROGRESS
  SYS_CUSTOMER_IDLE  | ENDING     | ENDING     | ENDING     | ENDING     | ENDING
  SYS_ACTION_FAILED  | ERROR      | ERROR      | ERROR      | ERROR      | ERROR

─── State: IN_PROGRESS ───
  Event              | HK         | SG         | UK         | US         | JP
  -------------------|------------|------------|------------|------------|------------
  TRANSFER_REQUEST   | TRANSFERRED| TRANSFERRED| TRANSFERRED| TRANSFERRED| TRANSFERRED
  CUSTOMER_CLOSE     | ENDING     | ENDING     | ENDING     | ENDING     | ENDING
  SURVEY_START       | SURVEY     | —          | SURVEY     | SURVEY     | —
  SYS_CUSTOMER_IDLE  | ENDING     | ENDING     | ENDING     | ENDING     | ENDING
  SYS_ACTION_FAILED  | ERROR      | ERROR      | ERROR      | ERROR      | ERROR

  Legend: — = transition unavailable (guard returned false)

─── State: TRANSFERRED ───
  Event              | HK         | SG         | UK         | US         | JP
  -------------------|------------|------------|------------|------------|------------
  TRANSFER_CONNECTED | IN_PROGRESS     | —          | —          | —          | —
  TRANSFER_FAILED    | INITIATED  | INITIATED  | INITIATED  | INITIATED  | INITIATED
  TRANSFER_TIMEOUT   | INITIATED  | INITIATED  | INITIATED  | INITIATED  | INITIATED
  SURVEY_START       | SURVEY     | —          | SURVEY     | SURVEY     | —
  SYS_ACTION_FAILED  | ERROR      | ERROR      | ERROR      | ERROR      | ERROR

─── Coverage Summary ───
  Market | IN_PROGRESS Transitions | Total | Coverage | Missing (expected)
  -------|-------------------|-------|----------|------------------
  HK     | 23                | 23    | 100%     | —
  SG     | 19                | 23    | 82.6%    | SURVEY_START × 2, TRANSFER_CONNECTED × 2
  UK     | 21                | 23    | 91.3%    | TRANSFER_CONNECTED × 2
  US     | 21                | 23    | 91.3%    | TRANSFER_CONNECTED × 2
  JP     | 19                | 23    | 82.6%    | SURVEY_START × 2, TRANSFER_CONNECTED × 2
```

### 3.4 Auto-Generated Test Cases

From the compatibility matrix, auto-generate parameterized tests:

```java
// Auto-generated from compatibility matrix
class MarketCompatibilityTest {

    @ParameterizedTest
    @MethodSource("allActiveTransitions")
    void shouldTransitionSuccessfully(String market, ConversationState from,
                                        ConversationFact event, ConversationState expectedTo) {
        StateMachineMarketConfig config = configLoader.load(market);
        StateContext<...> result = fireEvent(config, from, event);
        assertEquals(expectedTo, result.getTargetState());
    }

    @ParameterizedTest
    @MethodSource("allInactiveTransitions")
    void shouldRejectInactiveTransition(String market, ConversationState from, ConversationFact event) {
        StateMachineMarketConfig config = configLoader.load(market);
        assertThrows(StateMachineException.class, () -> fireEvent(config, from, event));
    }

    // Test data generated from TransitionResolver output
    static Stream<Arguments> allActiveTransitions() {
        return matrixGenerator.generate()
                .activeTransitions()
                .stream()
                .map(t -> Arguments.of(t.market(), t.from(), t.event(), t.to()));
    }
}
```

### 3.5 CI Integration

```yaml
# .github/workflows/config-diff.yml
name: Config Diff Analysis

on:
  pull_request:
    paths:
      - 'config/**/*.yaml'

jobs:
  diff-analysis:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Generate diff report
        run: ./mvnw exec:java -Dexec.mainClass="com.example.ConfigDiffTool" -Dexec.args="--pr"
      - name: Generate compatibility matrix
        run: ./mvnw exec:java -Dexec.mainClass="com.example.MatrixGenerator"
      - name: Post diff as PR comment
        uses: actions/github-script@v7
        with:
          script: |
            const diff = require('./diff-report.md');
            const matrix = require('./compatibility-matrix.md');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              body: `## Config Change Impact\n\n${diff}\n\n<details><summary>Compatibility Matrix</summary>\n\n${matrix}\n</details>`
            });
```

---

## 4. Implementation Roadmap

### Phase 1: Transition Resolver (0.5 day)
- [ ] Implement `TransitionResolver` with guard evaluation
- [ ] Implement `EffectiveTransitionMap` data structure
- [ ] Unit tests for guard resolution (true/false/null guard)

### Phase 2: Diff Reporter (1 day)
- [ ] Implement `MarketDiffReport` data structure
- [ ] Implement config-level diff (scalars, maps, lists)
- [ ] Implement transition-level diff (using TransitionResolver)
- [ ] Implement markdown report generator
- [ ] Unit tests for diff scenarios

### Phase 3: Compatibility Matrix (0.5 day)
- [ ] Implement `CompatibilityMatrixGenerator`
- [ ] Implement markdown matrix generator
- [ ] Implement coverage summary (IN_PROGRESS/total per market)
- [ ] Unit tests for matrix generation

### Phase 4: Test Generator & CI (1 day)
- [ ] Implement auto-generated test case generator
- [ ] Integrate diff report as PR comment in CI
- [ ] Integrate matrix generation in CI
- [ ] Add "redundant override" detection (market value == base value)

---

## 5. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Guard evaluation has side effects (not pure functions) | Medium | High | Guards must be pure functions (no I/O, no state mutation); enforce via code review and tests |
| Diff report too verbose, important changes buried | Medium | Medium | Categorize by impact (high/medium/low), highlight transition differences first |
| Matrix generation slow for many markets | Low | Low | Cache effective transition maps; markets with identical config share results |
| Auto-generated tests are flaky (guard depends on external state) | Medium | High | Guards only read from config + context; test context is fully controlled |
| Redundant override detection causes false positives | Low | Medium | Flag as warning, not error; allow `@IntentionalOverride` annotation in config comments |

---

## 6. Success Criteria

- [ ] Running `diff HK UK` produces a structured report in < 1 second
- [ ] Compatibility matrix shows every (state, event) combination for every market
- [ ] Every IN_PROGRESS transition in the matrix has a corresponding auto-generated test
- [ ] Config PRs automatically include an impact analysis report as a comment
- [ ] Redundant overrides (market value == base value) are flagged
- [ ] Missing transitions are explained with guard reasons (e.g., "genesysEnabled=false")
