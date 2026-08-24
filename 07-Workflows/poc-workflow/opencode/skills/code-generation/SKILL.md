---
name: code-generation
description: TDD GREEN phase — write minimal implementation code to make RED tests pass. Reads failing tests, writes ONLY the code needed to pass, runs tests to verify GREEN, then optionally refactors while keeping tests green. NEVER modifies tests. Use after test-cases (RED) is verified, or when you need to implement code to pass tests.
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
  - Bash(git:*)
---

# Code Generation Skill (TDD GREEN Phase)

Write minimal code to pass tests. Verify GREEN. Refactor while green.

## CRITICAL RULES

1. **NEVER MODIFY TESTS**: Do NOT edit, delete, or modify any test file. Tests are the specification. If a test seems wrong, stop and report — do NOT "fix" the test.
2. **MINIMAL CODE**: Write ONLY the code needed to make tests pass. No speculative features, no premature optimization, no "nice to have" methods. YAGNI.
3. **RED MUST EXIST FIRST**: Before writing any implementation, verify that tests exist and are RED. If no RED tests, this is NOT TDD — stop and run test-cases skill first.
4. **ONE SLICE AT A TIME**: Implement one slice, verify GREEN, then next slice. Do NOT implement all slices at once.
5. **RUN TESTS AFTER EVERY CHANGE**: After each code change, run tests. If tests fail, revert immediately or fix. Do NOT accumulate changes.
6. **REFACTOR ONLY WHEN GREEN**: Refactoring happens ONLY after tests pass. Never refactor while tests are RED.
7. **FOLLOW CODING GUIDELINES**: Must follow `04-Coding-Guidelines/` (all). Code that passes tests but violates guidelines is NOT acceptable.
8. **TRACE TO SDD**: Implementation must match SDD design. Deviations require justification.
9. **NO SECRETS**: Never hardcode credentials, tokens, or sensitive data.

## References

- [or-ituran/claude-tdd-skill](https://github.com/or-ituran/claude-tdd-skill) — tdd-implementer, tdd-failure-analyzer, tdd-refactorer sub-agents
- [genkovich/sdd](https://github.com/genkovich/sdd) — Implement engine, minimal code principle
- [aliev/strict-tdd](https://github.com/aliev/strict-tdd) — Strict TDD, no test modification
- [hugo-bluecorn/claude-code-tdd-workflow](https://github.com/hugo-bluecorn/claude-code-tdd-workflow) — auto-run-tests.sh hook, validate-tdd-order.sh
- [POC Stage 5 Doc](../../stages/05-code-generation.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 5 criteria
- [KB Integration](../../knowledge-integration.md) — KB read/write protocol

## Prerequisites

1. Stage 4 (test-cases) completed — RED tests exist and verified
2. `docs/operations/{JIRA_KEY}/04-test-cases/tdd-progress.md` shows all slices RED_VERIFIED
3. Test files exist in `src/test/java/`
4. Java project with Maven build
5. Operation directory exists: `docs/operations/{JIRA_KEY}/05-code-generation/`

## Execution Steps

### Step 1: Verify RED State

```bash
# Read TDD progress
cat "docs/operations/{JIRA_KEY}/04-test-cases/tdd-progress.md"

# Run all new tests to confirm they are still RED
mvn test -Dtest={TestClass1},{TestClass2} -pl {module} -q 2>&1 | tail -20

echo "Exit code: $?"
```

**Verify**:
- All slices show RED_VERIFIED in progress
- Running tests produces failures (exit code != 0 or test failures)
- No test files modified since RED verification (`git status` shows test files as new/modified)

If RED state not confirmed → stop, run test-cases skill first.

### Step 2: Inject Knowledge Base

**Mandatory reads**:
- `04-Coding-Guidelines/` — All coding guidelines
- `03-Design-Guidelines/` — Design patterns
- SDD from Stage 3

**Existing code patterns**:
```bash
# Find similar implementation patterns
grep -rl "{keyword}" src/main/java/ --include="*.java" | head -10

# Read existing service/controller patterns
find src/main/java -name "*Service.java" | head -5
```

### Step 3: Initialize Implementation Progress

Write `implementation-progress.md`:
```markdown
# Implementation Progress — {JIRA_KEY}

**Status**: IN_PROGRESS
**Started**: {timestamp}

## Slices
| # | Slice | FR | Status | Implementation Files | GREEN Verified |
|---|-------|-----|--------|---------------------|---------------|
| 1 | {slice} | FR-001 | pending | — | — |

## Current Slice
{slice number} — {slice name}
```

### Step 4: For Each Slice — GREEN Phase

#### 4.1: Read Failing Tests for Slice

```bash
# Read test file for current slice
cat src/test/java/.../{TestClass}.java

# Read RED output for this slice
cat "docs/operations/{JIRA_KEY}/04-test-cases/slices/slice-{N}-red-output.txt"
```

Understand:
- What classes/methods the tests expect
- What behavior is being tested
- What the failure messages say

#### 4.2: Write Minimal Implementation

Create/modify implementation files:
```
src/main/java/com/selfdevelopment/ai/messaging/{module}/{ClassName}.java
```

**Principles**:
- Write ONLY what tests require
- Use existing patterns from codebase
- Follow coding guidelines
- No TODO comments, no placeholder code
- No logging beyond what guidelines require

**Interactive checkpoint** (before writing):
> Slice {N}: "{slice name}". Tests expect: {classes/methods}.
> Implementation plan: {brief description}.
> Options: [Write implementation] [Adjust plan] [View tests] [Stop]

#### 4.3: Run Tests — Verify GREEN

```bash
mvn test -Dtest={TestClass} -pl {module} -q 2>&1 | tee "docs/operations/{JIRA_KEY}/05-code-generation/slices/slice-{N}-green-output.txt"

echo "Exit code: $?"
```

**GREEN validation**:
- All tests in slice pass ✅
- No compilation errors ✅
- Exit code 0 (or BUILD SUCCESS) ✅
- No existing tests broken ✅

#### 4.4: If Tests Still Fail

**Invoke failure analysis** (like tdd-failure-analyzer sub-agent):

1. Read failure messages
2. Determine root cause:
   - Missing method → add method
   - Wrong return value → fix logic
   - Wrong exception → fix exception handling
   - Missing dependency → add dependency injection
   - Test expects different behavior → STOP — do NOT modify test, report discrepancy

3. Apply minimal fix
4. Re-run tests
5. Max 3 fix attempts per slice, then escalate

**IMPORTANT**: If failure is because test expects behavior different from SDD, STOP. Do NOT modify test. Report:
- Test expects: {behavior}
- SDD says: {behavior}
- Discrepancy: {description}
Ask user to resolve.

#### 4.5: REFACTOR Phase (Optional, Only When GREEN)

If code quality could be improved AND tests are GREEN:

1. Identify code smells:
   - Long methods (>20 lines)
   - Duplicate code
   - Magic numbers
   - Deep nesting
   - Unclear names

2. Apply ONE refactoring at a time
3. Run tests after each refactoring
4. If tests fail → revert immediately
5. Record refactoring in progress

```bash
# After each refactoring
mvn test -Dtest={TestClass} -pl {module} -q
echo "Exit code: $?"
# If 0 → continue. If != 0 → git revert the change
```

**Interactive checkpoint**:
> Slice {N} GREEN ✅. Code quality: {assessment}.
> Options: [Refactor] [Next slice] [View implementation] [View test output] [Stop]

#### 4.6: Record GREEN Verification

Update `implementation-progress.md`:
```markdown
| 1 | {slice} | FR-001 | GREEN_VERIFIED | {files} | ✅ |
```

Write `slice-{N}-green-report.md`:
```markdown
# GREEN Report — Slice {N}: {name}

**Implementation files**: {files}
**Test file**: {file}
**Run command**: `mvn test -Dtest=...`
**Exit code**: {code}
**GREEN verified**: ✅

## Tests Passed
1. {test name}
2. ...

## Refactoring Applied
- {refactoring 1} — {before → after}
- None

## Code Quality
- Lines of code: {N}
- Methods: {N}
- Code smells: {N}

## Evidence
- Output: `slice-{N}-green-output.txt`
```

#### 4.7: Repeat for All Slices

Continue until all slices GREEN_VERIFIED.

### Step 5: Run Full Test Suite

After all slices:
```bash
# Run full module test suite
mvn test -pl {module} -q 2>&1 | tee "docs/operations/{JIRA_KEY}/05-code-generation/full-test-output.txt"

echo "Exit code: $?"
```

**Verify**:
- All tests pass (including existing tests)
- No regressions
- Coverage meets threshold (>= 80% line, >= 70% branch)

### Step 6: Code Quality Checks

```bash
# Check for coding guideline violations
# (If SonarQube configured)
mvn sonar:sonar -pl {module} -q 2>&1 | tail -10

# Check compile warnings
mvn compile -pl {module} -q 2>&1 | grep -i "warning" | head -10

# Check for TODO/FIXME
grep -rn "TODO\|FIXME\|HACK" src/main/java/{module}/ | head -10
```

### Step 7: Generate Implementation Summary

Write `implementation-summary.md`:
```markdown
# Implementation Summary — {JIRA_KEY}

**Total slices**: {N}
**Total tests passing**: {M}
**Implementation files**: {N}
**Lines of code**: {N}
**Refactoring applied**: {N}

## Files Created/Modified
| File | Type | Lines | Slice |
|------|------|-------|-------|

## FR Traceability
| FR | Implementation | Tests |
|----|---------------|-------|

## Code Quality
- Sonar issues: {N} (critical: {N}, major: {N})
- Compile warnings: {N}
- TODO/FIXME: {N}
- Coverage: {N}% line, {N}% branch

## Deviations from SDD
- {deviation} — {justification}
- None

## KB Updates Needed
- {pattern} → {KB location}
```

### Step 8: Commit Code (If Pipeline Allows)

```bash
git add src/main/java/{module}/ src/test/java/{module}/
git commit -m "feat({module}): implement {JIRA_KEY} — {summary}

- {slice 1 description}
- {slice 2 description}
- Tests: {N} passing, coverage {N}%

Refs: {JIRA_KEY}"
```

## Verify Gate (Automated)

| Criteria | Method | Evidence |
|----------|--------|----------|
| RED state confirmed before implementation | Test output | Step 1 output |
| All slices GREEN verified | GREEN reports | slice-{N}-green-report.md |
| No test files modified | Git diff | `git diff --name-only src/test/` = only new files from Stage 4 |
| Minimal code (no speculative features) | Code review | implementation-summary.md |
| All tests pass (full suite) | Test output | full-test-output.txt |
| No existing tests broken | Test output | full-test-output.txt |
| Coverage >= 80% line, >= 70% branch | Coverage report | JaCoCo report |
| Follows coding guidelines | Quality checks | verify-report.md |
| Follows design guidelines | SDD traceability | implementation-summary.md |
| No secrets hardcoded | Grep check | `grep -rn "password\|secret\|token" src/main/` |
| No TODO/FIXME in production code | Grep check | `grep -rn "TODO\|FIXME" src/main/` |
| Sonar no critical/blocker | Sonar report | Sonar output |
| Implementation summary generated | File exists | implementation-summary.md |
| Code committed (if allowed) | Git log | `git log --oneline -3` |

**PASS** → All checks ✅ → Proceed to Stage 6 (PR review)
**FAIL** → Fix issues, re-verify (max 3 retries, then escalate)

## KB Injection

**Read**:
- `04-Coding-Guidelines/` — All coding guidelines
- `03-Design-Guidelines/` — Design patterns
- `01-CBOL-Domain-Knowledge/` — Domain patterns
- `02-Chat-Domain-Knowledge/` — IM patterns
- SDD from Stage 3

**Write**:
- New implementation patterns → `04-Coding-Guidelines/`
- New domain patterns → `01-CBOL-Domain-Knowledge/`

## Error Handling

| Error | Resolution |
|-------|-----------|
| RED state not confirmed | Run test-cases skill first, do NOT proceed |
| Tests expect behavior different from SDD | STOP, do NOT modify test, report discrepancy to user |
| Tests fail after 3 fix attempts | Escalate, create escalation ticket with failure analysis |
| Existing tests break | Analyze regression, fix implementation (not tests), max 3 attempts |
| Coverage below threshold | Add more tests (but tests must be RED first — go back to Stage 4) |
| Sonar critical issues | Fix code, re-run quality checks |
| Build fails (compilation) | Fix compilation errors, do NOT modify tests |
| Dependency injection issues | Check existing patterns, follow project conventions |
| Refactoring breaks tests | Revert immediately (`git checkout {file}`), try different approach |

## Output Artifacts

- `src/main/java/.../*.java` — Implementation files
- `docs/operations/{JIRA_KEY}/05-code-generation/implementation-progress.md` — Progress tracking
- `docs/operations/{JIRA_KEY}/05-code-generation/implementation-summary.md` — Summary
- `docs/operations/{JIRA_KEY}/05-code-generation/slices/slice-{N}-green-report.md` — Per-slice GREEN reports
- `docs/operations/{JIRA_KEY}/05-code-generation/slices/slice-{N}-green-output.txt` — Per-slice test output
- `docs/operations/{JIRA_KEY}/05-code-generation/full-test-output.txt` — Full test suite output
- `docs/operations/{JIRA_KEY}/05-code-generation/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/05-code-generation/operation-log.md` — Operation log
- Git commit with implementation code

---

*Code Generation Skill v2.0.0 — 2026-08-24*
*Optimized with: Implementer/failure-analyzer/refactorer sub-agent pattern, per-slice interactive checkpoints, progress persistence, strict no-test-modification enforcement, CRITICAL rules, precise allowed-tools*
