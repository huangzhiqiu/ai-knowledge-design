# Test Verifier

> Skill for Stage 4 of the ticket-to-deploy workflow: run tests, collect evidence, verify test quality, and manage the test failure fix loop with 3-strike escalation.

## Purpose

Verify that the implementation from Stage 3 passes all tests, meets coverage thresholds, and produces verifiable evidence. If tests fail, enter a fix loop (developer → tester) with a maximum of 3 retries before escalating to a human.

**Evidence Discipline (from Jira-Flow best practices):**
- Any completion claim MUST be backed by: command + output summary + exit code
- No evidence = not accepted
- Test failures trigger a fix loop, not "skip and move on"

**3-Strike Escalation (from Forge + ai-coding-workflow):**
- Auto-retry up to 3 times
- After 3 failures: escalate to human, pause workflow, post to Jira

## Input

| Parameter | Required | Description |
|-----------|----------|-------------|
| `jira_key` | Yes | Jira issue key, e.g. `CBOL-123` |
| `test_scope` | No | `full` (default) or `specific:{TestClass}` |
| `retry_count` | No | Current retry count (for resuming fix loop) |

## Output

1. **Test report**: `docs/operations/{JIRA-KEY}/test-report.md`
2. **Operation log**: `docs/operations/{JIRA-KEY}/04-test-v{N}.md`
3. **Fix loop log**: `docs/operations/{JIRA-KEY}/test-fix-loop.md` (if failures occurred)

## Test Report Template

```markdown
# Test Report — {JIRA-KEY} (v{N})

## Summary
- Status: ✅ PASSED / ❌ FAILED
- Total tests: {count}
- Passed: {count}
- Failed: {count}
- Skipped: {count}
- Duration: {seconds}s
- Exit code: {0/1}

## Coverage
- Line coverage: {percentage}% (threshold: 80%)
- Branch coverage: {percentage}% (threshold: 70%)
- Coverage status: ✅ MET / ❌ BELOW THRESHOLD

## Test Execution
### Command
```bash
{command}
```

### Output (summary)
```
{test output summary}
```

## Failed Tests (if any)
| # | Test Class | Test Method | Failure Message |
|---|-----------|-------------|----------------|
| 1 | {class} | {method} | {message} |

## Fix Loop (if applicable)
| Attempt | Action | Result | Exit Code |
|---------|--------|--------|-----------|
| 1 | {fix description} | ✅/❌ | {code} |
| 2 | {fix description} | ✅/❌ | {code} |
| 3 | {fix description} | ✅/❌ | {code} |

## Evidence
- Command: `{command}`
- Output: {summary}
- Exit code: {0/1}
- Coverage report: `{path/to/jacoco-report}`

## Next
{handoff to Stage 5 or escalation note}
```

## Workflow

1. **Read configuration**: Get test command from `.ai-workflow/config.yaml`
2. **Run tests**:
   - Full suite: `./mvnw test -q`
   - Specific: `./mvnw test -Dtest={TestClass} -q`
3. **Collect evidence**: Capture command, output summary, exit code, duration
4. **Generate coverage report**: Run JaCoCo if configured
5. **Check coverage thresholds**:
   - Line coverage >= 80% (configurable)
   - Branch coverage >= 70% (configurable)
6. **If all pass**:
   - Write test report
   - Write operation log
   - Proceed to Gate 4 (human review)
7. **If tests fail**:
   - Enter fix loop:
     a. Analyze failure (read stack trace, identify root cause)
     b. Fix the code (invoke tdd-implementer for the fix)
     c. Re-run tests
     d. Log the attempt
     e. If pass → exit loop, proceed
     f. If fail → increment retry count
   - After 3 failed attempts:
     a. Escalate to human
     b. Post failure summary to Jira as comment
     c. Add label `ai-test-failed`
     d. Pause workflow
     e. Wait for human intervention

## Test Quality Checks

Beyond pass/fail, verify:

- [ ] **No flaky tests**: Tests pass consistently (run 3 times if suspected)
- [ ] **Test naming**: Test methods follow `should{Behavior}When{Condition}` pattern
- [ ] **Test isolation**: No test depends on another test's state
- [ ] **No ignored tests without reason**: `@Disabled` tests must have justification
- [ ] **Edge cases covered**: Boundary conditions, null inputs, error paths
- [ ] **Integration tests**: Key flows have integration test coverage
- [ ] **No test-only code in production**: No `if (isTest())` patterns

## Fix Loop Protocol

When a test fails, the fix loop follows:

```
1. ANALYZE: Read stack trace, identify failing assertion, trace root cause
2. FIX: Apply minimal fix (TDD cycle if new behavior, bug fix if regression)
3. VERIFY: Re-run the failing test specifically
4. REGRESSION: Run full test suite to ensure no new failures
5. LOG: Record attempt, action, result, exit code
6. DECIDE: Pass → exit loop | Fail → retry (max 3) | 3 failures → escalate
```

## Escalation Template

When escalating after 3 failures, post this to Jira:

```
🤖 AI Test Verification Failed — Escalation Required

Ticket: {JIRA-KEY}
Stage: 4 — Test Verification
Attempts: 3 failed

## Failed Tests
{list of failed tests with messages}

## What We Tried
1. {fix attempt 1} → {result}
2. {fix attempt 2} → {result}
3. {fix attempt 3} → {result}

## Root Cause Analysis
{best guess at root cause, or "unable to determine"}

## Recommended Next Steps
- [ ] Human review the failing tests
- [ ] Check for environment-specific issues
- [ ] Review recent changes that may have caused regression

## Evidence
- Last test command: `{command}`
- Exit code: {code}
- Full log: docs/operations/{JIRA-KEY}/test-fix-loop.md
```

## Usage

```
/test-verifier jira_key=CBOL-123
```

Run specific test:
```
/test-verifier jira_key=CBOL-123 test_scope=specific:MessageServiceTest
```

## Related Skills

- `workflow-ticket-to-deploy` — Orchestrator that invokes this skill at Stage 4
- `tdd-implementer` — Stage 3: provides the implementation to verify
- `pr-creator` — Stage 5: creates PR after tests pass
- `code-analyzer` — Analyzes code quality alongside tests

## References

- Jira-Flow evidence discipline: https://github.com/jinx911/jira-flow
- Forge CI repair loop: https://github.com/forge-sdlc/forge
- ai-coding-workflow 3-strike escalation: https://github.com/wenttt/ai-coding-workflow
- JaCoCo: https://www.jacoco.org/
- CBOL code quality guidelines: `04-Coding-Guidelines/code-quality.md`
