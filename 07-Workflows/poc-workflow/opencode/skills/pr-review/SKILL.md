---
name: pr-review
description: Create a Pull Request with structured description and run automated PR review. Reviews code across 5 axes (correctness, design, security, performance, tests). Requires human approval after auto review. Use after code-generation skill, or when you need to create and review a PR.
version: 1.0.0
author: CBOL Self-Development
tags: [pr, pull-request, code-review, auto-review, github, poc]
triggers:
  - "create PR"
  - "pull request review"
  - "auto review PR"
  - "open pull request"
arguments:
  - name: jira_key
    description: Jira ticket key (e.g., CBOL-123)
    required: true
---

# PR Review Skill

Create PR + automated review + human approval.

## References

- [fanioz/claude-code-pr-automation](https://github.com/fanioz/claude-code-pr-automation) — 5 specialized review agents, auto-fix
- [shubhesh07/claude-code-reviewer](https://github.com/shubhesh07/claude-code-reviewer) — gstack two-pass review methodology
- [gthimmes/code-reviewer](https://github.com/gthimmes/code-reviewer) — 5 axes review with confidence scoring
- [chanmuzi/git-claw](https://github.com/chanmuzi/git-claw) — /review-reply command
- [POC Stage 6 Doc](../../stages/06-pr-review.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 6 criteria

## Prerequisites

1. Stage 5 (code-generation) completed — all tests pass
2. Implementation code committed
3. GitHub remote configured
4. Operation directory exists: `docs/operations/{JIRA_KEY}/06-pr-review/`

## Execution Steps

### Step 1: Read Artifacts

```bash
cat "docs/operations/{JIRA_KEY}/03-sdd/sdd.md" | head -100
cat "docs/operations/{JIRA_KEY}/05-code-generation/implementation-summary.md"
```

### Step 2: Inject Knowledge Base

**Mandatory reads**:
- `04-Coding-Guidelines/06-quality-ops/` (quality gates, SonarQube)
- `04-Coding-Guidelines/05-security/` (security review checklist)
- `03-Design-Guidelines/04-security-design/` (security architecture)
- `03-Design-Guidelines/05-reliability/` (reliability patterns)

### Step 3: Create Feature Branch

```bash
git checkout main
git pull origin main
git checkout -b "feat/{JIRA_KEY}-{short-desc}"
# If code already on a branch, skip
```

### Step 4: Push to Remote

```bash
git push origin "feat/{JIRA_KEY}-{short-desc}"
```

### Step 5: Create PR

Using GitHub CLI or API:

```bash
gh pr create \
  --title "feat({scope}): {description} ({JIRA_KEY})" \
  --body-file "docs/operations/{JIRA_KEY}/06-pr-review/pr-description.md" \
  --base main \
  --head "feat/{JIRA_KEY}-{short-desc}"
```

**PR Description Template**:
```markdown
## {JIRA_KEY}: {Summary}

**Jira**: [{KEY}]({JIRA_URL})
**Type**: {Story/Task/Bug}
**Priority**: {High/Medium/Low}

## Summary
{2-3 sentence summary}

## Changes Made
- {change 1}
- {change 2}

## Artifacts
- [Requirements](../02-requirements/requirements.md)
- [SDD](../03-sdd/sdd.md)
- [Test Plan](../04-test-cases/test-plan.md)
- [Implementation Summary](../05-code-generation/implementation-summary.md)

## Test Results
- Total: {N} tests
- Passed: {N}
- Failed: 0
- Coverage: {X}% line / {Y}% branch

## Checklist
- [ ] All tests pass
- [ ] Coverage >= 80% line / 70% branch
- [ ] No Sonar critical/blocker issues
- [ ] Security guidelines followed
- [ ] Coding guidelines followed
- [ ] Documentation updated
- [ ] KB updated (if new patterns)

## Reviewers
@{reviewer1} @{reviewer2}
```

### Step 6: Run Auto PR Review

Review diff across 5 axes:

#### Axis 1: Correctness
- [ ] Logic errors?
- [ ] Edge cases handled?
- [ ] Error handling correct?
- [ ] Race conditions?
- [ ] Off-by-one errors?

#### Axis 2: Design
- [ ] Follows SDD architecture?
- [ ] Separation of concerns?
- [ ] Appropriate design patterns?
- [ ] No god classes?
- [ ] Dependency injection used?

#### Axis 3: Security
- [ ] No hardcoded secrets?
- [ ] Input validation on all external inputs?
- [ ] SQL injection prevention (parameterized queries)?
- [ ] Authentication/authorization checks?
- [ ] No sensitive data in logs?
- [ ] CSRF protection?
- [ ] XSS prevention?

#### Axis 4: Performance
- [ ] N+1 query problems?
- [ ] Appropriate caching?
- [ ] No unnecessary database calls?
- [ ] Efficient algorithms?
- [ ] Connection pooling?

#### Axis 5: Tests
- [ ] Coverage >= 80% line / 70% branch?
- [ ] Edge cases tested?
- [ ] Error paths tested?
- [ ] Test naming follows convention?
- [ ] Tests independent (no shared state)?
- [ ] No flaky tests?

**Review Methodology** (inspired by gstack two-pass):
1. **First pass**: Find CRITICAL issues (SQL safety, race conditions, injection, security) — these BLOCK merge
2. **Second pass**: Find INFORMATIONAL issues (style, naming, minor improvements) — these are suggestions

### Step 7: Generate Auto Review Report

Write `docs/operations/{JIRA_KEY}/06-pr-review/auto-review-report.md`:

```markdown
# Auto Review Report — {JIRA_KEY}

**PR**: {PR URL}
**Date**: {ISO timestamp}
**Result**: PASS / CHANGES REQUESTED

## Summary
- Critical issues: {N}
- Important issues: {N}
- Minor issues: {N}

## Critical Issues (BLOCK merge)

### Issue 1
- **File**: `{path}:{line}`
- **Axis**: Security
- **Issue**: {description}
- **Suggested fix**: {fix}

## Important Issues

### Issue 2
- **File**: `{path}:{line}`
- **Axis**: Performance
- **Issue**: N+1 query in {method}
- **Suggested fix**: Use JOIN or batch fetch

## Minor Issues
...

## 5-Axis Scores
| Axis | Score | Notes |
|------|-------|-------|
| Correctness | 9/10 | ... |
| Design | 8/10 | ... |
| Security | 10/10 | ... |
| Performance | 7/10 | ... |
| Tests | 9/10 | ... |
```

### Step 8: If Auto Review FAILS — Fix Issues

If critical/important issues found:
1. Return to Stage 5 (code-generation) to fix
2. Re-run tests
3. Push fixes to PR branch
4. Re-run auto review
5. Max 3 fix cycles, then escalate

### Step 9: Wait for CI

Check GitHub Actions / CI:
```bash
gh pr checks {PR_NUMBER}
```

Wait for all checks to pass. If CI fails, fix and push.

### Step 10: Request Human Review

1. Assign reviewers
2. Add comment: "Auto review PASS, ready for human review"
3. Wait for human approval

### Step 11: Record Human Approval

When human approves:
- Update `auto-review-report.md` with human approval
- Note any human review comments

### Step 12: Verify Report + State Update

## Verify Gate (Automated + Human)

| Criteria | Method | Evidence |
|----------|--------|----------|
| Feature branch created | Git branch exists | `git branch` |
| Branch pushed to remote | Remote branch exists | `git ls-remote` |
| PR created | GitHub PR exists | `gh pr view` |
| PR title follows format | Conventional commit format | PR title |
| PR description follows template | Template pattern match | PR body |
| PR linked to Jira | Jira key in title/body | PR content |
| CI checks pass | GitHub Checks API | `gh pr checks` |
| Auto review PASS | No critical/blocker issues | auto-review-report.md |
| 5-axis review completed | All axes scored | auto-review-report.md |
| No Sonar critical/blocker | SonarQube API | Sonar report |
| No security vulnerabilities | Security scan | Security report |
| Coverage >= 80%/70% | Coverage report | coverage-report.xml |
| Human explicitly approves | GitHub review: Approve | PR review status |
| KB docs injected | KB injection log | operation-log.md |

**PASS** → Auto review PASS + CI pass + human approves → Proceed to Stage 7 (deployment)
**FAIL** → Fix issues, push, re-review (max 3 cycles, then escalate)

## KB Injection

**Read**:
- `04-Coding-Guidelines/06-quality-ops/`
- `04-Coding-Guidelines/05-security/`
- `03-Design-Guidelines/04-security-design/`
- `03-Design-Guidelines/05-reliability/`

**Write**: None

## Review Confidence Scoring

Each issue gets a confidence score (inspired by gthimmes/code-reviewer):
- **High (90-100%)**: Clear violation of known best practice
- **Medium (70-89%)**: Likely issue, but context-dependent
- **Low (50-69%)**: Possible improvement, subjective

Only High confidence issues block merge. Medium/Low are suggestions.

## Error Handling

| Error | Resolution |
|-------|-----------|
| Code not committed | Run code-generation skill first |
| GitHub CLI not installed | Use GitHub API directly, or ask user to install gh |
| CI fails | Fix issues, push, wait for CI |
| Auto review finds critical issues | Fix in code-generation, re-run |
| Human rejects 2 times | Escalate to tech lead |
| PR merge conflicts | Rebase from main, resolve conflicts, force push |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/06-pr-review/pr-description.md` — PR description
- `docs/operations/{JIRA_KEY}/06-pr-review/auto-review-report.md` — Auto review report
- `docs/operations/{JIRA_KEY}/06-pr-review/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/06-pr-review/operation-log.md` — Operation log
- GitHub Pull Request (remote)

---

*PR Review Skill v1.0.0 — 2026-08-21*
