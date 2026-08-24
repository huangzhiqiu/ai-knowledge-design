---
name: test-cases
description: TDD RED phase — generate failing test cases from reviewed SDD + knowledge base. Writes tests FIRST, verifies they fail for the RIGHT reason (assertion failure, not compile error), then stops. Does NOT write implementation code. Use after SDD approval, or when you need to write tests first in TDD.
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash(mvn:*)
  - Bash(java:*)
  - Bash(grep:*)
  - Bash(find:*)
  - Bash(cat:*)
---

# Test Cases Skill (TDD RED Phase)

Write failing tests first. Verify RED. Stop before implementation.

## CRITICAL RULES

1. **TESTS FIRST, ALWAYS**: Write tests BEFORE any implementation code. If implementation exists for this feature, this is NOT TDD — stop and report.
2. **RED MUST BE CORRECT**: Test must fail for the RIGHT reason — assertion failure or missing method/class. A compile error due to missing class IS valid RED (compile-time RED). A compile error due to syntax error is NOT valid RED — fix the test syntax.
3. **NO IMPLEMENTATION CODE**: This skill writes ONLY test files. Do NOT create or modify production code. If test needs a class that doesn't exist, that's expected RED.
4. **DO NOT MODIFY EXISTING TESTS**: Only create new test files or add new test methods. Never modify existing passing tests to make them fail.
5. **VERIFY RED BEFORE STOPPING**: Run tests and confirm they fail. Do NOT assume they fail.
6. **ONE SLICE AT A TIME**: Write tests for one functional slice, verify RED, then next slice. Do NOT write all tests at once.
7. **FOLLOW TEST GUIDELINES**: Must follow `04-Coding-Guidelines/09-testing/unit-testing-guidelines.md`.
8. **TRACE TO SDD**: Every test must trace to an SDD section and FR/AC.

## References

- [or-ituran/claude-tdd-skill](https://github.com/or-ituran/claude-tdd-skill) — Sub-agent isolation, progress persistence, interactive checkpoints
- [Upsolve-Labs/upstack](https://github.com/Upsolve-Labs/upstack) — /execute RED/GREEN strict TDD
- [aliev/strict-tdd](https://github.com/aliev/strict-tdd) — Strict TDD enforcement
- [hugo-bluecorn/claude-code-tdd-workflow](https://github.com/hugo-bluecorn/claude-code-tdd-workflow) — validate-tdd-order.sh hook, auto-run-tests.sh
- [doodooms/everything-copilot tdd-workflow](https://github.com/doodooms/everything-copilot) — Runtime RED + Compile-time RED validation
- [POC Stage 4 Doc](../../stages/04-test-cases.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 4 criteria
- [KB Integration](../../knowledge-integration.md) — KB read/write protocol

## Prerequisites

1. Stage 3 (SDD) completed and REVIEWED
2. `docs/operations/{JIRA_KEY}/03-sdd/sdd.md` exists with status REVIEWED
3. Java project with Maven build (`pom.xml` exists)
4. Test framework configured (JUnit 5 + Mockito)
5. Operation directory exists: `docs/operations/{JIRA_KEY}/04-test-cases/`

## Execution Steps

### Step 1: Read SDD and Requirements

```bash
cat "docs/operations/{JIRA_KEY}/03-sdd/sdd.md"
cat "docs/operations/{JIRA_KEY}/02-requirements/requirements.md"
```

Extract:
- Test slices from SDD Section 10 (Testing Strategy)
- FRs and ACs to trace tests to
- Data models, APIs, state machines from SDD
- Edge cases from SDD Section 10.3

### Step 2: Inject Knowledge Base

**Mandatory reads**:
- `04-Coding-Guidelines/09-testing/unit-testing-guidelines.md`
- `04-Coding-Guidelines/09-testing/integration-testing-guidelines.md`
- `04-Coding-Guidelines/09-testing/test-pyramid.md`

**Label-based reads**: Map ticket labels to relevant testing patterns.

**Existing test patterns**:
```bash
# Find existing test files to understand conventions
find src/test/java -name "*Test.java" | head -10
cat src/test/java/.../ExampleTest.java  # Read one example
```

### Step 3: Initialize TDD Session

Create progress tracking:

```bash
mkdir -p "docs/operations/{JIRA_KEY}/04-test-cases/slices"
```

Write `tdd-progress.md`:
```markdown
# TDD RED Progress — {JIRA_KEY}

**Status**: IN_PROGRESS
**Started**: {timestamp}

## Test Slices
| # | Slice | FR | Status | Test File | RED Verified |
|---|-------|-----|--------|-----------|-------------|
| 1 | {slice name} | FR-001 | pending | — | — |
| 2 | {slice name} | FR-002 | pending | — | — |

## Current Slice
{slice number} — {slice name}
```

### Step 4: For Each Test Slice — RED Phase

#### 4.1: Design Test Cases for Slice

For current slice, list test cases:
- Happy path
- Edge cases (null, empty, boundary, max/min)
- Error cases (invalid input, timeout, network failure)
- State transitions (if state machine)
- Concurrency (if applicable)

**Interactive checkpoint**:
> Slice {N}: "{slice name}". Planned {M} test cases: {list}.
> Options: [Write tests] [Add more cases] [Skip slice] [Stop]

#### 4.2: Write Test File

Create test file following project conventions:
```
src/test/java/com/selfdevelopment/ai/messaging/{module}/{ClassName}Test.java
```

**Test structure** (per unit-testing-guidelines):
```java
@DisplayName("{Feature} tests")
class {ClassName}Test {

    @Nested
    @DisplayName("{Slice name}")
    class {SliceName}Tests {

        @Test
        @DisplayName("should {expected behavior} when {condition}")
        void should{Behavior}When{Condition}() {
            // Given
            // When
            // Then
        }
    }
}
```

**Traceability comment** at top of each test method:
```java
// FR-001, AC-001, SDD Section 4.1
```

#### 4.3: Run Tests and Verify RED

```bash
# Run specific test class
mvn test -Dtest={ClassName}Test -pl {module} -q 2>&1 | tee "docs/operations/{JIRA_KEY}/04-test-cases/slices/slice-{N}-red-output.txt"

echo "Exit code: $?"
```

**RED validation** (per doodooms tdd-workflow):

**Runtime RED** (preferred):
- Test compiles successfully ✅
- Test is actually executed ✅
- Result is RED (test fails) ✅
- Failure reason is assertion failure or expected exception (NOT syntax error) ✅

**Compile-time RED** (valid when class/method doesn't exist):
- Test newly instantiates/references the target code path ✅
- Compile failure is due to missing class/method (NOT syntax error in test) ✅
- The missing class/method is exactly what implementation will create ✅

**Check failure reason**:
```bash
# Extract failure messages
grep -A 5 "FAILED\|ERROR\|BUILD FAILURE" "docs/operations/{JIRA_KEY}/04-test-cases/slices/slice-{N}-red-output.txt" | head -20
```

#### 4.4: If RED Not Correct

| Problem | Action |
|---------|--------|
| Test passes (GREEN) | Implementation already exists — this is NOT TDD. Report and stop. |
| Compile error in test syntax | Fix test syntax, re-run. |
| Compile error in import | Fix import, re-run. |
| Test fails for wrong reason | Adjust test to fail for intended reason. |
| Test not executed (skipped) | Remove @Disabled, fix test discovery. |

**Max 3 fix attempts per slice**, then escalate.

#### 4.5: Record RED Verification

Update `tdd-progress.md`:
```markdown
| 1 | {slice name} | FR-001 | RED_VERIFIED | {file} | ✅ {failure reason} |
```

Write `slice-{N}-red-report.md`:
```markdown
# RED Report — Slice {N}: {name}

**Test file**: {path}
**Test count**: {N}
**Run command**: `mvn test -Dtest=...`
**Exit code**: {code}
**RED verified**: ✅ / ❌

## Failure Reasons
1. {test name}: {failure message}
2. ...

## Compile-time RED (if applicable)
- Missing class: {class name}
- Missing method: {method signature}

## Evidence
- Output: `slice-{N}-red-output.txt`
```

**Interactive checkpoint**:
> Slice {N} RED verified ✅. {N} tests failing for correct reason.
> Options: [Next slice] [View test code] [View RED output] [Stop]

#### 4.6: Repeat for All Slices

Continue until all slices have RED-verified tests.

### Step 5: Generate Test Summary

Write `test-summary.md`:
```markdown
# Test Summary — {JIRA_KEY}

**Total slices**: {N}
**Total tests**: {M}
**RED verified**: {N}/{N} slices ✅

## Test Coverage by FR
| FR | Test Count | Slices |
|----|-----------|--------|
| FR-001 | {N} | {slices} |

## Test Files
- {file 1} — {N} tests
- {file 2} — {N} tests

## Edge Cases Covered
- {edge case 1}
- {edge case 2}

## Not Covered (deferred)
- {item} — {reason}
```

### Step 6: Verify Gate

Run all new tests together to confirm all RED:
```bash
mvn test -Dtest={TestClass1},{TestClass2} -pl {module} -q 2>&1 | tee "docs/operations/{JIRA_KEY}/04-test-cases/all-red-output.txt"
```

Confirm: all new tests fail, no existing tests broken.

## Verify Gate (Automated)

| Criteria | Method | Evidence |
|----------|--------|----------|
| SDD reviewed and approved | Status check | sdd.md status = REVIEWED |
| Test guidelines injected | KB read log | operation-log.md |
| Test slices defined | Progress file | tdd-progress.md |
| Each slice RED verified | RED reports | slice-{N}-red-report.md |
| Tests fail for RIGHT reason | Failure analysis | RED reports + output files |
| No implementation code written | Git diff check | `git diff --name-only src/main/` = empty |
| No existing tests modified | Git diff check | `git diff --name-only` shows only new files |
| Tests trace to FR/AC | Traceability comments | Test file grep |
| Tests follow naming convention | Pattern check | verify-report.md |
| Tests use Given-When-Then | Structure check | verify-report.md |
| Edge cases covered | Test summary | test-summary.md |
| All new tests RED (combined run) | Test output | all-red-output.txt |
| No existing tests broken | Test output | all-red-output.txt |
| Test summary generated | File exists | test-summary.md |

**PASS** → All checks ✅ → Proceed to Stage 5 (code generation / TDD GREEN)
**FAIL** → Fix issues, re-verify (max 3 retries, then escalate)

## KB Injection

**Read**:
- `04-Coding-Guidelines/09-testing/unit-testing-guidelines.md`
- `04-Coding-Guidelines/09-testing/integration-testing-guidelines.md`
- `04-Coding-Guidelines/09-testing/test-pyramid.md`
- `01-CBOL-Domain-Knowledge/` (domain-specific test patterns)
- `02-Chat-Domain-Knowledge/` (IM test patterns)

**Write**: None (tests are code, not KB)

## Error Handling

| Error | Resolution |
|-------|-----------|
| SDD not reviewed | Run sdd skill and get review approval first |
| pom.xml not found | Verify project structure, ask user for build config |
| Test framework not configured | Check pom.xml for JUnit/Mockito, ask user to configure |
| Tests pass (already implemented) | This is NOT TDD — report to user, ask whether to skip TDD or delete implementation |
| RED fails for wrong reason | Adjust test, max 3 attempts, then escalate |
| Build takes too long | Use `-pl {module}` to build only relevant module, use `-q` for quiet |
| Existing tests break | Check if new tests affect shared state, isolate tests |
| Test discovery fails | Check class name ends with `Test`, check @Test annotations |

## Output Artifacts

- `src/test/java/.../*Test.java` — New test files (RED)
- `docs/operations/{JIRA_KEY}/04-test-cases/tdd-progress.md` — TDD progress tracking
- `docs/operations/{JIRA_KEY}/04-test-cases/test-summary.md` — Test summary
- `docs/operations/{JIRA_KEY}/04-test-cases/slices/slice-{N}-red-report.md` — Per-slice RED reports
- `docs/operations/{JIRA_KEY}/04-test-cases/slices/slice-{N}-red-output.txt` — Per-slice test output
- `docs/operations/{JIRA_KEY}/04-test-cases/all-red-output.txt` — Combined RED output
- `docs/operations/{JIRA_KEY}/04-test-cases/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/04-test-cases/operation-log.md` — Operation log

---

*Test Cases Skill v2.0.0 — 2026-08-24*
*Optimized with: Sub-agent isolation pattern, progress persistence, interactive checkpoints per slice, Runtime+Compile-time RED validation, CRITICAL rules, precise allowed-tools*
