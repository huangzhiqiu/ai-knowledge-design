# PR Creator

> Skill for Stage 5 of the ticket-to-deploy workflow: create branch, commit, push, create GitHub PR with structured body, and manage CI repair loop with 3-strike escalation.

## Purpose

Create a pull request for the implemented ticket, with a structured PR body that links to the SDD, Jira ticket, test report, and operation logs. If CI fails, enter a CI repair loop (analyze → fix → push → retry) with maximum 3 attempts before escalating.

**Design-in-Issues, Code-in-PRs (from ai-coding-workflow):**
- Design discussion lives in GitHub Issues (lightweight, no branches)
- Code lives in PRs (with branch isolation for parallel team development)
- PR body has `Closes #<design-issue>` to auto-close design issue on merge

**CI Repair Loop (from Forge):**
- Analyze failing CI checks
- Apply fixes
- Push updates
- Retry until ready for review or blocked with clear reason

## Input

| Parameter | Required | Description |
|-----------|----------|-------------|
| `jira_key` | Yes | Jira issue key, e.g. `CBOL-123` |
| `base_branch` | No | Base branch for PR (default: `main`) |
| `dry_run` | No | If true, prepare everything but don't push/create PR |

## Output

1. **PR**: GitHub pull request with structured body
2. **Operation log**: `docs/operations/{JIRA-KEY}/05-pr-v{N}.md`
3. **CI repair log**: `docs/operations/{JIRA-KEY}/ci-repair-loop.md` (if CI failed)

## PR Body Template

```markdown
## Summary
{One-paragraph summary of what this PR does}

**Jira:** [{JIRA-KEY}]({jira_url})
**SDD:** [{SDD title}]({sdd_path_or_url})
**Design Issue:** #{design_issue_number}

## Changes
### What Changed
- {change 1}
- {change 2}
- {change 3}

### Files Changed
| File | Change Type | Lines +/- |
|------|------------|-----------|
| {file} | {Added/Modified/Deleted} | +{n}/-{n} |

## Test Results
- ✅ All tests pass ({passed_count} passed, {failed_count} failed)
- Coverage: {line_coverage}% line, {branch_coverage}% branch
- Test report: `docs/operations/{JIRA-KEY}/test-report.md`

## SDD Reference
Key design decisions from SDD:
1. {decision 1}
2. {decision 2}
3. {decision 3}

Full SDD: `design/sdd/{JIRA-KEY}.md`

## Operation Logs
- Stage 0 (Ticket): `docs/operations/{JIRA-KEY}/00-ticket.md`
- Stage 1 (Requirements): `docs/operations/{JIRA-KEY}/01-requirements-v1.md`
- Stage 2 (SDD): `docs/operations/{JIRA-KEY}/02-sdd-v1.md`
- Stage 3 (Implementation): `docs/operations/{JIRA-KEY}/03-implementation-v1.md`
- Stage 4 (Test): `docs/operations/{JIRA-KEY}/04-test-v1.md`

## Checklist
- [ ] Code follows coding guidelines (`04-Coding-Guidelines/`)
- [ ] All tests pass
- [ ] Coverage meets thresholds (>=80% line, >=70% branch)
- [ ] No Sonar critical/blocker issues
- [ ] API changes documented in SDD
- [ ] Database changes have migration plan
- [ ] Security guidelines followed

## Closes
Closes #{design_issue_number}
```

## Workflow

1. **Read inputs**: SDD, test report, operation logs from previous stages
2. **Create branch**:
   - Branch name: `feat/{KEY}-{short-desc}` (from config pattern)
   - `git checkout -b {branch_name} {base_branch}`
3. **Stage and commit**:
   - `git add {changed files}`
   - Commit message: `{type}({scope}): {subject} ({KEY})`
   - Multiple commits if logical separation
4. **Push branch**:
   - `git push origin {branch_name}`
5. **Create PR**:
   - Title: `[{KEY}] {title}`
   - Body: from template above
   - Base: `{base_branch}`
   - Head: `{branch_name}`
   - Add labels: `ai-generated`, type label
   - Assign creator
6. **Wait for CI**:
   - Poll GitHub Checks API
   - Collect CI status (pending/passing/failing)
7. **If CI passes**:
   - Write operation log
   - Proceed to Gate 5 (peer review)
8. **If CI fails**:
   - Enter CI repair loop:
     a. Analyze failing check (read logs, identify cause)
     b. Apply fix (invoke tdd-implementer for code fix)
     c. Commit and push fix
     d. Wait for CI
     e. Log the attempt
     f. If pass → exit loop
     g. If fail → increment retry count
   - After 3 failed attempts:
     a. Escalate to human
     b. Post CI failure summary to PR comment
     c. Add label `ai-ci-failed`
     d. Pause workflow

## Branch Naming Rules

- Pattern: `{type}/{key}-{short-desc}`
- Types: `feat/` (story), `fix/` (bug), `chore/` (task), `epic/` (epic)
- Short description: kebab-case, max 30 chars
- Examples:
  - `feat/CBOL-123-message-forwarding`
  - `fix/CBOL-124-websocket-reconnect-bug`
  - `chore/CBOL-125-update-dependencies`

## Commit Message Rules

Conventional Commits format:
```
{type}({scope}): {subject} ({JIRA-KEY})

{optional body}

{optional footer}
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`

Examples:
```
feat(messaging): add message forwarding feature (CBOL-123)
fix(websocket): handle reconnect race condition (CBOL-124)
refactor(state-machine): extract transition logic to separate class (CBOL-125)
```

## CI Repair Loop Protocol

```
1. ANALYZE: Read CI check logs, identify failing step (build/test/lint/security)
2. DIAGNOSE: Determine root cause (code issue, config issue, flaky test, env issue)
3. FIX: Apply minimal fix (code change, config update, test fix)
4. COMMIT: `fix(ci): {description} ({KEY})`
5. PUSH: `git push origin {branch}`
6. WAIT: Poll CI status (timeout: 10 minutes)
7. LOG: Record attempt, action, result
8. DECIDE: Pass → exit | Fail → retry (max 3) | 3 failures → escalate
```

## Peer Review Gate (Gate 5)

After PR is created and CI passes:
- PR is ready for human peer review
- **Enforced**: Ticket creator cannot be the approver (from GitHub Copilot for Jira)
- Reviewer can: approve, request changes, comment
- If changes requested → fix loop (max 3) → escalate if still failing
- If approved → proceed to Stage 6 (Deploy & Doc Update)

## Usage

```
/pr-creator jira_key=CBOL-123
```

Dry run (prepare but don't push):
```
/pr-creator jira_key=CBOL-123 dry_run=true
```

## Related Skills

- `workflow-ticket-to-deploy` — Orchestrator that invokes this skill at Stage 5
- `test-verifier` — Stage 4: provides test report for PR body
- `tdd-implementer` — Used in CI repair loop to fix code
- `deploy-doc-updater` — Stage 6: deploy and update docs after PR merge

## References

- ai-coding-workflow design-in-Issues code-in-PRs: https://github.com/wenttt/ai-coding-workflow
- Forge CI repair loop: https://github.com/forge-sdlc/forge
- Conventional Commits: https://www.conventionalcommits.org/
- GitHub Checks API: https://docs.github.com/en/rest/checks
