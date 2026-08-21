---
name: test-cases
description: Generate test cases from approved SDD using TDD RED phase. Writes failing tests FIRST, verifies they fail for the right reason (assertion failure, not compile error). Enforces test naming conventions, coverage targets, traceability to SDD requirements. Use after SDD skill, or when you need to write tests before implementation.
version: 1.0.0
author: CBOL Self-Development
tags: [tdd, testing, red-phase, test-cases, coverage, poc]
triggers:
  - "write tests first"
  - "TDD red"
  - "generate test cases"
  - "write failing tests"
arguments:
  - name: jira_key
    description: Jira ticket key (e.g., CBOL-123)
    required: true
---

# Test Cases Skill (TDD RED Phase)

Generate failing test cases from SDD. Tests MUST fail before code.

## References

- [genkovich/sdd](https://github.com/genkovich/sdd) — plan-tests + implement TDD engine
- [Upsolve-Labs/upstack](https://github.com/Upsolve-Labs/upstack) — /execute RED: write failing tests
- [Strict TDD Skill](https://gist.github.com/aliev/3f402f7a2b84febe65da4910aab6a97c) — human-in-the-loop checkpoints
- [tdd-workflow](https://skillsmp.com/creators/doodooms/everything-copilot/github-skills-tdd-workflow) — RED-GREEN-REFACTOR with 80%+ coverage gate
- [POC Stage 4 Doc](../../stages/04-test-cases.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 4 criteria

## Prerequisites

1. Stage 3 (SDD) completed and human-approved
2. SDD exists: `docs/operations/{JIRA_KEY}/03-sdd/sdd.md`
3. Test framework configured (JUnit 5 for Java)
4. Operation directory exists: `docs/operations/{JIRA_KEY}/04-test-cases/`

## TDD RED Rules (STRICT)

1. ✅ Write tests FIRST — no production code in this stage
2. ✅ Tests MUST fail when run (prove they test something real)
3. ✅ Failure MUST be assertion failure (expected behavior not implemented)
4. ❌ Failure must NOT be compile error (fix test to reference existing classes or create stubs)
5. ❌ NO production code — only test files and minimal stubs/interfaces
6. ✅ Each test traces to a specific SDD requirement
7. ✅ Test naming: `test{Method}_{Scenario}_{ExpectedResult}`
8. ✅ Arrange-Act-Assert pattern
9. ✅ Coverage target: >= 80% line, >= 70% branch

## Execution Steps

### Step 1: Read SDD

```bash
cat "docs/operations/{JIRA_KEY}/03-sdd/sdd.md"
```

Extract: implementation plan tasks, FRs, data model, API design, state machine.

### Step 2: Inject Knowledge Base

**Mandatory reads**:
- `04-Coding-Guidelines/07-testing/unit-testing-guidelines.md`
- `04-Coding-Guidelines/07-testing/` (ALL docs)
- `02-Chat-Domain-Knowledge/` (test patterns for IM)

**Search for existing test patterns**:
```bash
find src/test -name "*Test.java" | head -20
cat src/test/java/.../ExistingTest.java 2>/dev/null | head -50
```

### Step 3: Generate Test Plan

Write `docs/operations/{JIRA_KEY}/04-test-cases/test-plan.md`:

```markdown
# Test Plan — {JIRA_KEY}

## Coverage Target
- Line: >= 80%
- Branch: >= 70%

## Traceability Matrix

| Test ID | SDD Requirement | Test Type | Test Class | Priority |
|---------|-----------------|-----------|------------|----------|
| T001 | FR-001 | Unit | MessageForwarderTest | High |
| T002 | FR-001 | Integration | MessageForwardingIT | High |
| T003 | FR-002 | Unit | ... | Medium |

## Unit Tests
### {ComponentName}Test
- testForwardMessage_ValidMessage_MessageDelivered — FR-001
- testForwardMessage_InvalidRecipient_ThrowsException — FR-001
- ...

## Integration Tests
...

## Edge Cases
- Empty message body
- Very long message (> 10MB)
- Concurrent forwarding
- Network failure
- ...

## Error Handling Tests
- Invalid recipient
- Permission denied
- Rate limit exceeded
- Service unavailable
- ...
```

### Step 4: Write Test Cases

For each task in SDD implementation plan:

1. Create test class in `src/test/java/.../`
2. Write test methods following:
   - Naming: `test{Method}_{Scenario}_{ExpectedResult}`
   - Pattern: Arrange-Act-Assert
   - Each test has comment: `// FR-001: {requirement description}`
3. Use existing test utilities, base classes, fixtures
4. For WebSocket: use test client or mock
5. For state machine: test all state transitions
6. For database: use test containers or H2 in-memory

**Example**:
```java
@Test
// FR-001: Message forwarding between users
void testForwardMessage_ValidMessage_MessageDelivered() {
    // Arrange
    Message message = new Message("user1", "user2", "Hello");
    when(messageRepository.save(any())).thenReturn(message);

    // Act
    Message result = messageForwarder.forward(message);

    // Assert
    assertNotNull(result);
    assertEquals("user2", result.getRecipientId());
    verify(messageRepository).save(message);
}
```

### Step 5: Create Minimal Stubs (if needed)

If tests reference non-existent classes, create minimal stubs:
- Interfaces with method signatures
- Empty class skeletons
- DO NOT implement any logic

```java
// Stub only — no implementation
public interface MessageForwarder {
    Message forward(Message message);
}
```

### Step 6: Run Tests (RED)

```bash
# Run tests for this ticket's test classes
mvn test -Dtest="*{ComponentName}Test,*{ComponentName}IT"

# Or run all tests
mvn test
```

**Expected**: Tests FAIL.

### Step 7: Verify RED

Analyze test output:
- ✅ **Assertion failure** — `expected: X but was: Y` → CORRECT RED
- ❌ **Compile error** — `cannot find symbol` → FIX test (create stub or reference existing class)
- ❌ **Test passes** → implementation already exists, check scope

If compile errors, fix tests (NOT implementation) and re-run.

### Step 8: Save RED Output

```bash
mvn test -Dtest="*{ComponentName}Test" 2>&1 | tee "docs/operations/{JIRA_KEY}/04-test-cases/red-test-output.txt"
```

### Step 9: Verify Report + State Update

## Verify Gate (Automated)

| Criteria | Method | Evidence |
|----------|--------|----------|
| Test plan generated | File exists | `ls` output |
| Test cases written | Test files exist in src/test | `find src/test -name "*{Component}*"` |
| Each test traces to SDD req | Traceability matrix | test-plan.md |
| Test naming follows convention | Naming check | verify-report.md |
| Tests use AAA pattern | Code review | verify-report.md |
| Tests run and FAIL | `mvn test` exit code != 0 | red-test-output.txt |
| Failure is assertion (not compile) | Output analysis | red-test-output.txt |
| No production code written | `git diff --stat` shows only test files | Git diff |
| Coverage target defined | >= 80% line / 70% branch | test-plan.md |
| Edge cases covered | Edge case section | test-plan.md |
| Error paths covered | Error handling section | test-plan.md |

**PASS** → Tests exist + fail for right reason + no production code → Proceed to Stage 5 (code-generation)
**FAIL** → Fix tests (NOT code), re-run (max 3 retries, then escalate)

## KB Injection

**Read**:
- `04-Coding-Guidelines/07-testing/` (ALL)
- `02-Chat-Domain-Knowledge/` (test patterns)
- `03-Design-Guidelines/05-reliability/` (test strategy)

**Write**: None (this stage doesn't write to KB)

## Test Types by Component

| Component Type | Test Types | Tools |
|----------------|-----------|-------|
| Service layer | Unit tests | JUnit 5 + Mockito |
| Controller/API | Integration tests | MockMvc / TestRestTemplate |
| WebSocket | Integration tests | Spring WebSocket test client |
| State machine | Unit tests | State machine test framework |
| Database | Integration tests | Testcontainers / H2 |
| Repository | Integration tests | @DataJpaTest |
| Message queue | Integration tests | Embedded Kafka / RabbitMQ |

## Error Handling

| Error | Resolution |
|-------|-----------|
| SDD not found | Run sdd skill first |
| Tests don't fail | Check if implementation exists — may be out of scope, ask user |
| Compile errors | Create minimal stubs/interfaces, re-run |
| Test framework not configured | Ask user to configure JUnit 5 + Mockito |
| Coverage can't reach target | Note gap in test plan, ask user for waiver |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/04-test-cases/test-plan.md` — Test plan + traceability
- Test files in `src/test/java/.../`
- `docs/operations/{JIRA_KEY}/04-test-cases/red-test-output.txt` — RED phase output
- `docs/operations/{JIRA_KEY}/04-test-cases/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/04-test-cases/operation-log.md` — Operation log

---

*Test Cases Skill v1.0.0 — 2026-08-21*
