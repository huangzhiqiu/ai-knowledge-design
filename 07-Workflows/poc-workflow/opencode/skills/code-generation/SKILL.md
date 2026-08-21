---
name: code-generation
description: Generate implementation code from SDD + failing tests using TDD GREEN phase. Writes minimal code to make tests pass, verifies all tests pass, checks coverage >= 80%/70%, validates code meets SDD requirements. Do NOT modify tests from Stage 4. Use after test-cases skill, or when you need to implement code to pass failing tests.
version: 1.0.0
author: CBOL Self-Development
tags: [tdd, code-generation, green-phase, implementation, coverage, poc]
triggers:
  - "implement code"
  - "TDD green"
  - "make tests pass"
  - "generate implementation"
arguments:
  - name: jira_key
    description: Jira ticket key (e.g., CBOL-123)
    required: true
---

# Code Generation Skill (TDD GREEN Phase)

Generate implementation code to make failing tests pass. Do NOT modify tests.

## References

- [genkovich/sdd](https://github.com/genkovich/sdd) — implement TDD engine (SELECT→RED→GREEN→REFACTOR→GATE→COMMIT)
- [Upsolve-Labs/upstack](https://github.com/Upsolve-Labs/upstack) — /execute GREEN: implement, atomic commits
- [POC Stage 5 Doc](../../stages/05-code-generation.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 5 criteria

## Prerequisites

1. Stage 4 (test-cases) completed — failing tests exist
2. RED output confirms tests fail for right reason
3. SDD exists: `docs/operations/{JIRA_KEY}/03-sdd/sdd.md`
4. Knowledge base directories exist
5. Operation directory exists: `docs/operations/{JIRA_KEY}/05-code-generation/`

## TDD GREEN Rules (STRICT)

1. ✅ Write MINIMAL code to make tests pass
2. ✅ Do NOT modify tests from Stage 4
3. ✅ All tests MUST pass
4. ✅ Coverage >= 80% line, >= 70% branch
5. ✅ Follow `04-Coding-Guidelines/` (ALL relevant)
6. ✅ Follow `03-Design-Guidelines/` (architecture decisions)
7. ❌ NO code outside SDD scope
8. ❌ NO cheating (modifying tests, skipping tests, @Disabled)
9. ✅ After GREEN, REFACTOR for quality (keep tests green)
10. ✅ Conventional commit format

## Execution Steps

### Step 1: Read SDD + Tests

```bash
cat "docs/operations/{JIRA_KEY}/03-sdd/sdd.md"
cat "docs/operations/{JIRA_KEY}/04-test-cases/test-plan.md"
```

Extract: implementation plan tasks, component design, data model, API design.

### Step 2: Inject Knowledge Base

**Mandatory reads**:
- `04-Coding-Guidelines/` (ALL relevant: Java, Spring, WebSocket, DB, cache, queue)
- `03-Design-Guidelines/` (ALL: architecture, API, data, security, reliability)
- `01-CBOL-Domain-Knowledge/` (domain logic patterns)
- `02-Chat-Domain-Knowledge/` (IM implementation references)

**Analyze existing code patterns**:
```bash
# Find existing service implementations
find src/main/java -name "*ServiceImpl.java" | head -10

# Check existing WebSocket config
find src/main/java -name "*WebSocket*Config*" -o -name "*Netty*"

# Check existing state machine
find src/main/java -name "*StateMachine*" -o -name "*State*"
```

### Step 3: Implement Code (GREEN)

For each task in SDD implementation plan:

1. Read the failing test for this task
2. Write MINIMAL implementation code to make it pass
3. Follow coding guidelines:
   - Java 17+ features (records, sealed classes, pattern matching)
   - Spring Boot 3.x conventions
   - Dependency injection (constructor injection preferred)
   - Immutable objects where possible
   - Proper exception handling
   - Logging (SLF4J)
4. For WebSocket: follow `02-Chat-Domain-Knowledge/websocket/` patterns
5. For state machine: follow `01-CBOL-Domain-Knowledge/state-machine/` patterns
6. For database: follow `03-Design-Guidelines/03-data-design/` patterns
7. Run tests after each task:
   ```bash
   mvn test -Dtest="*{ComponentName}Test"
   ```

**IMPORTANT**: Do NOT modify any test files from Stage 4. If a test seems wrong, note it but do NOT change it — escalate to human.

### Step 4: Run All Tests (GREEN)

```bash
mvn test 2>&1 | tee "docs/operations/{JIRA_KEY}/05-code-generation/green-test-output.txt"
```

**Expected**: ALL tests pass (exit code 0).

If tests fail:
1. Analyze failure
2. Fix implementation code (NOT tests)
3. Re-run
4. Max 3 fix cycles, then escalate

### Step 5: Check Coverage

```bash
mvn jacoco:report
# Check coverage in target/site/jacoco/index.html
# Or parse CSV
cat target/site/jacoco/jacoco.csv | grep "{package}"
```

**Requirements**:
- Line coverage >= 80%
- Branch coverage >= 70%

If below target:
1. Identify uncovered code
2. Add more tests (but wait — this stage is GREEN, tests were written in RED)
3. If tests from Stage 4 are insufficient, note gap and ask user if more tests needed
4. Do NOT add tests in this stage without user approval

### Step 6: Refactor (Keep Tests Green)

If all tests pass and coverage is met:
1. Review code for quality:
   - Remove duplication
   - Improve naming
   - Extract methods if too long (> 50 lines)
   - Apply design patterns if appropriate
2. Run tests after each refactor:
   ```bash
   mvn test
   ```
3. Stop when tests still pass and code quality is acceptable

### Step 7: Verify Requirements Met

Check code meets all SDD requirements:
- [ ] All FRs implemented
- [ ] All ACs satisfied
- [ ] Data model matches SDD
- [ ] API endpoints match SDD
- [ ] State machine transitions match SDD
- [ ] No code outside SDD scope

### Step 8: Write Implementation Summary

Write `docs/operations/{JIRA_KEY}/05-code-generation/implementation-summary.md`:
- Tasks completed table
- Test results
- Coverage report
- Files changed (`git diff --stat`)
- KB references
- KB updates (if any)

### Step 9: Identify New Coding Patterns

If implementation reveals new patterns not in KB:
```bash
# Search KB for existing pattern
grep -r "{pattern}" 04-Coding-Guidelines/ --include="*.md" -l
```

If new, draft KB doc in appropriate `04-Coding-Guidelines/` subdirectory.

### Step 10: Commit

```bash
git add src/main/
git commit -m "feat({scope}): {description} ({JIRA_KEY})"
```

### Step 11: Verify Report + State Update

## Verify Gate (Automated)

| Criteria | Method | Evidence |
|----------|--------|----------|
| All tests pass | `mvn test` exit code 0 | green-test-output.txt |
| No test modifications | `git diff --name-only` shows no test file changes from Stage 4 | Git diff |
| Line coverage >= 80% | JaCoCo report | coverage-report.xml |
| Branch coverage >= 70% | JaCoCo report | coverage-report.xml |
| Code follows coding guidelines | Lint/format check | verify-report.md |
| Code follows design guidelines | Architecture review | verify-report.md |
| All SDD requirements met | Traceability check | implementation-summary.md |
| No code outside SDD scope | Diff scope check | Git diff analysis |
| No methods > 50 lines | Code analysis | verify-report.md |
| No classes > 500 lines | Code analysis | verify-report.md |
| No hardcoded secrets | Secret scan | verify-report.md |
| Input validation present | Code review | verify-report.md |
| Conventional commit format | Commit message check | Git log |
| KB docs injected | KB injection log | operation-log.md |

**PASS** → All tests pass + coverage met + requirements met → Proceed to Stage 6 (pr-review)
**FAIL** → Fix code (NOT tests), re-run (max 3 retries, then escalate)

## KB Injection

**Read**:
- `04-Coding-Guidelines/` (ALL relevant)
- `03-Design-Guidelines/` (ALL)
- `01-CBOL-Domain-Knowledge/` (domain logic)
- `02-Chat-Domain-Knowledge/` (IM patterns)

**Write**:
- New coding patterns → `04-Coding-Guidelines/` (relevant subdirectory)

## Implementation by Component Type

| Component | Guidelines | KB Reference |
|-----------|-----------|--------------|
| Controller | @RestController, @RequestMapping, proper HTTP status | `03-Design-Guidelines/02-api-design/` |
| Service | @Service, business logic, transaction management | `04-Coding-Guidelines/02-spring/` |
| Repository | @Repository, Spring Data JPA, custom queries | `04-Coding-Guidelines/04-database/` |
| WebSocket handler | Netty/Spring WebSocket, event handling | `02-Chat-Domain-Knowledge/websocket/` |
| State machine | Custom lightweight state machine | `01-CBOL-Domain-Knowledge/state-machine/` |
| Cache | Redis, cache annotations, eviction policies | `04-Coding-Guidelines/05-cache/` |
| Message queue | Kafka/RabbitMQ, producers/consumers | `04-Coding-Guidelines/06-queue/` |

## Error Handling

| Error | Resolution |
|-------|-----------|
| Tests don't pass | Fix implementation code (NOT tests), max 3 cycles |
| Coverage below target | Note gap, ask user if more tests needed (Stage 4 should have covered) |
| Test seems wrong | Do NOT modify — note in operation log, escalate to human |
| Code conflicts with existing patterns | Follow existing patterns, note discrepancy |
| SDD requirement unclear | Escalate to human, do NOT guess |

## Output Artifacts

- Implementation code in `src/main/java/.../`
- `docs/operations/{JIRA_KEY}/05-code-generation/green-test-output.txt` — GREEN phase output
- `docs/operations/{JIRA_KEY}/05-code-generation/coverage-report.xml` — Coverage report
- `docs/operations/{JIRA_KEY}/05-code-generation/implementation-summary.md` — Summary
- `docs/operations/{JIRA_KEY}/05-code-generation/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/05-code-generation/operation-log.md` — Operation log
- Potential KB updates: `04-Coding-Guidelines/`

---

*Code Generation Skill v1.0.0 — 2026-08-21*
