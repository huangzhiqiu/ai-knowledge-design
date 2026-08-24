---
name: pr-review
description: Create a Pull Request and run automated multi-axis code review with confidence scoring. Reviews across correctness, design, security, performance, and tests axes using find-then-verify pipeline. Posts review comments, requires human approval before merge. Use after code generation, or when you need to create and review a PR.
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash(git:*)
  - Bash(gh:*)
  - Bash(mvn:*)
  - Bash(curl:*)
  - Bash(jq:*)
  - Bash(grep:*)
  - Bash(cat:*)
---

# PR Review Skill

Create PR + 5-axis automated review with confidence scoring + human approval.

## CRITICAL RULES

1. **HUMAN APPROVAL REQUIRED**: PR CANNOT be merged without explicit human approval. Automated review is advisory only — never auto-merge.
2. **FIND-THEN-VERIFY**: Every finding MUST be verified before reporting. Phase 1: scan for potential issues. Phase 2: verify each is a real issue (not false positive). Report ONLY verified findings.
3. **CONFIDENCE SCORING**: Every finding carries a confidence score (0.0-1.0). Only report findings with confidence >= threshold (default 0.7).
4. **5 AXES ONLY**: Review across exactly 5 axes: correctness, design, security, performance, tests. Do NOT invent new axes.
5. **FOCUS ON CHANGES**: Review ONLY the diff (changed lines + necessary context). Do NOT review pre-existing issues outside the diff.
6. **NO NITPICKS**: Do NOT report style nits, formatting, or minor preferences. Only report substantive issues that could cause bugs, security vulnerabilities, or design problems.
7. **LINK TO CODE**: Every finding MUST include file path + line number + code snippet. Vague findings are not acceptable.
8. **SEVERITY CLASSIFICATION**: Every finding classified as CRITICAL (block merge), MAJOR (should fix), or MINOR (consider fixing).

## References

- [gthimmes/code-reviewer](https://github.com/gthimmes/code-reviewer) — 5-axis review, find-then-verify pipeline, confidence scoring
- [fanioz/claude-code-pr-automation](https://github.com/fanioz/claude-code-pr-automation) — 5-agent PR automation (creator, reviewer, security, performance, summary)
- [anthropics/claude-code code-review plugin](https://github.com/anthropics/claude-code) — Confidence-based scoring (threshold 80), CLAUDE.md compliance, git blame context
- [chanmuzi/git-claw](https://github.com/chanmuzi/git-claw) — /code-review multi-agent severity-based review
- [jjscannell/code-review](https://github.com/jjscannell/code-review) — Multiple specialized agents in parallel, prioritized remediation plan
- [POC Stage 6 Doc](../../stages/06-pr-review.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 6 criteria
- [KB Integration](../../knowledge-integration.md) — KB read/write protocol

## Prerequisites

1. Stage 5 (code-generation) completed — code implemented and tests pass
2. `docs/operations/{JIRA_KEY}/05-code-generation/implementation-summary.md` exists
3. Git repository with remote configured
4. `gh` CLI installed and authenticated (for GitHub PR creation)
5. Branch created for this ticket: `feat/CBOL-XXX-{desc}` or `fix/CBOL-XXX-{desc}`
6. Operation directory exists: `docs/operations/{JIRA_KEY}/06-pr-review/`

## Execution Steps

### Step 1: Prepare Branch and Commit

```bash
# Verify on correct branch
git branch --show-current

# If not on feature branch, create it
git checkout -b "feat/{JIRA_KEY}-{short-desc}"

# Stage and commit all changes
git add -A
git status
git commit -m "feat({module}): implement {JIRA_KEY} — {summary}

{detailed description from implementation-summary.md}

Refs: {JIRA_KEY}"

# Push branch
git push origin "feat/{JIRA_KEY}-{short-desc}"
```

### Step 2: Create Pull Request

```bash
# Generate PR description
cat > /tmp/pr-body.md << 'EOF'
## Summary
{2-3 sentence summary}

## Changes
- {change 1}
- {change 2}

## Testing
- {N} unit tests passing
- Coverage: {N}% line, {N}% branch
- Full test suite: ✅

## Related Ticket
[{JIRA_KEY}]({JIRA_URL})

## Checklist
- [ ] Tests pass
- [ ] Code follows guidelines
- [ ] Documentation updated
- [ ] No secrets committed
EOF

# Create PR via gh CLI
gh pr create \
  --title "feat({module}): {JIRA_KEY} — {summary}" \
  --body-file /tmp/pr-body.md \
  --base main \
  --head "feat/{JIRA_KEY}-{short-desc}" \
  --label "enhancement" \
  --assignee "@me" 2>&1

echo "Exit code: $?"
```

**Record PR URL** in operation log.

### Step 3: Get PR Diff

```bash
# Get diff for review
gh pr diff {PR_NUMBER} > "docs/operations/{JIRA_KEY}/06-pr-review/pr.diff"

# Get changed files list
gh pr view {PR_NUMBER} --json files --jq '.files[].path' > "docs/operations/{JIRA_KEY}/06-pr-review/changed-files.txt"

# Get PR metadata
gh pr view {PR_NUMBER} --json title,body,author,baseRefName,headRefName,additions,deletions,changedFiles > "docs/operations/{JIRA_KEY}/06-pr-review/pr-meta.json"
```

### Step 4: Find-Then-Verify Review Pipeline

#### Phase 1: FIND — Scan for Potential Issues

Run 5-axis scan in parallel (or sequentially). For each axis, scan the diff:

**Axis 1: Correctness**
- Logic errors in changed code
- Missing null checks
- Off-by-one errors
- Incorrect error handling
- Race conditions
- Resource leaks (unclosed streams, connections)

**Axis 2: Design**
- Violations of SOLID principles
- Tight coupling
- Missing abstractions
- Inconsistent with existing patterns
- Violations of `03-Design-Guidelines/`

**Axis 3: Security**
- SQL injection
- XSS vulnerabilities
- Input validation gaps
- Authentication/authorization issues
- Sensitive data exposure
- Hardcoded secrets
- Insecure deserialization
- Violations of `04-Coding-Guidelines/security/`

**Axis 4: Performance**
- N+1 queries
- Inefficient algorithms (O(n²) where O(n) possible)
- Missing indexes
- Memory leaks
- Blocking calls in async context
- Violations of `04-Coding-Guidelines/performance/`

**Axis 5: Tests**
- Missing test coverage for new code
- Tests not following AAA pattern
- Missing edge case tests
- Flaky test patterns
- Tests testing implementation instead of behavior
- Violations of `04-Coding-Guidelines/09-testing/`

**For each potential finding, record**:
```json
{
  "axis": "correctness",
  "file": "path/to/file.java",
  "line": 42,
  "code_snippet": "the code",
  "issue": "description of potential issue",
  "confidence_raw": 0.8,
  "severity_raw": "major"
}
```

#### Phase 2: VERIFY — Validate Each Finding

For EACH potential finding:

1. **Read context**: Read surrounding code (10 lines before/after)
2. **Check if real issue**:
   - Is this actually a bug, or is it handled elsewhere?
   - Is this a false positive due to missing context?
   - Does existing code already handle this?
3. **Check KB guidelines**: Is this actually a violation, or an accepted pattern?
4. **Assign confidence**:
   - 0.9-1.0: Definitely a real issue, clear evidence
   - 0.7-0.8: Likely a real issue, some uncertainty
   - 0.5-0.6: Possible issue, significant uncertainty
   - <0.5: Probably false positive, discard
5. **Assign severity**:
   - CRITICAL: Will cause bugs, security vulnerabilities, or data loss. Blocks merge.
   - MAJOR: Significant issue, should be fixed before merge.
   - MINOR: Minor improvement, consider fixing.

**Discard** findings with confidence < 0.7.

**Interactive checkpoint**:
> Review complete. Found {N} potential issues, {M} verified (confidence >= 0.7).
> CRITICAL: {C}, MAJOR: {J}, MINOR: {N}
> Options: [Post review comments] [View findings] [Adjust confidence threshold] [Stop]

### Step 5: Post Review Comments

For each verified finding, post as PR review comment:

```bash
# Post review comment via gh CLI
gh pr comment {PR_NUMBER} --body "## [{axis}] {severity}: {issue title}

**File**: \`{file}:{line}\`
**Confidence**: {confidence}/1.0

### Issue
{description}

### Code
\`\`\`java
{code_snippet}
\`\`\`

### Suggested Fix
{suggestion}

### References
- {KB guideline link}
"
```

**Or use inline review comments** (if supported):
```bash
gh api repos/{owner}/{repo}/pulls/{PR_NUMBER}/comments \
  -f body="{comment body}" \
  -f commit_id="{head_sha}" \
  -f path="{file}" \
  -f line="{line}"
```

### Step 6: Generate Review Summary

Write `review-summary.md`:

```markdown
# PR Review Summary — {JIRA_KEY}

**PR**: #{PR_NUMBER} — {title}
**URL**: {pr_url}
**Reviewer**: AI Automated Review
**Date**: {timestamp}
**Confidence threshold**: 0.7

## Review Statistics
| Axis | Potential | Verified | CRITICAL | MAJOR | MINOR |
|------|-----------|----------|----------|-------|-------|
| Correctness | {N} | {N} | {N} | {N} | {N} |
| Design | {N} | {N} | {N} | {N} | {N} |
| Security | {N} | {N} | {N} | {N} | {N} |
| Performance | {N} | {N} | {N} | {N} | {N} |
| Tests | {N} | {N} | {N} | {N} | {N} |
| **Total** | **{N}** | **{N}** | **{C}** | **{J}** | **{N}** |

## CRITICAL Issues (Must Fix)
1. **[{axis}] {title}** — {file}:{line} (confidence: {c})
   {description}

## MAJOR Issues (Should Fix)
...

## MINOR Issues (Consider Fixing)
...

## False Positives Discarded
- {N} findings with confidence < 0.7

## Review Verdict
- **CRITICAL issues**: {C} → {"BLOCKS MERGE" if C > 0 else "None"}
- **Recommendation**: {"Request changes" if C > 0 or J > 2 else "Approve with comments"}

## KB References Used
- {KB doc 1}
- {KB doc 2}
```

### Step 7: Present for Human Approval

1. Display review summary
2. **Interactive checkpoint**:
   > PR #{PR_NUMBER} created and reviewed. {C} CRITICAL, {J} MAJOR, {N} MINOR issues found.
   > Options: [Approve PR] [Request changes] [View full review] [View PR on GitHub] [Fix issues] [Stop]

3. If human requests changes → go back to Stage 5 (code generation) to fix, then re-run review

### Step 8: Record Human Decision

Write `human-decision.md`:
```markdown
# Human Decision — PR Review

**Ticket**: {JIRA_KEY}
**PR**: #{PR_NUMBER}
**Decision Maker**: {name}
**Date**: {ISO timestamp}
**Decision**: Approved / Requested changes / Rejected
**Comments**: {optional}
```

## Verify Gate (Automated + Human)

| Criteria | Method | Evidence |
|----------|--------|----------|
| Branch created and pushed | Git check | `git branch -a` |
| Code committed | Git log | `git log --oneline -3` |
| PR created | gh CLI | PR URL in operation log |
| PR diff retrieved | File exists | pr.diff |
| 5-axis review completed | Review summary | review-summary.md |
| Find-then-verify applied | Verified findings count | review-summary.md |
| Confidence threshold applied (>=0.7) | Discarded count | review-summary.md |
| Every finding has file:line | Comment format | PR comments |
| CRITICAL issues identified | Summary section | review-summary.md |
| Security axis completed | Security findings | review-summary.md |
| No secrets in PR | Grep check | `grep -rn "password\|secret\|token\|api_key" pr.diff` |
| Review comments posted | gh CLI | PR comment count |
| Review summary generated | File exists | review-summary.md |
| Human explicitly approves | Decision record | human-decision.md |
| No CRITICAL issues at approval | Decision check | human-decision.md + review-summary.md |

**PASS** → Human explicitly approves ✅ AND no CRITICAL issues → Proceed to Stage 7 (deployment)
**FAIL** → Human requests changes → fix code (Stage 5), re-review (max 2 review cycles, then escalate)

## KB Injection

**Read**:
- `04-Coding-Guidelines/` — All coding guidelines (for review criteria)
- `03-Design-Guidelines/` — Design guidelines (for design axis)
- `04-Coding-Guidelines/security/` — Security guidelines (for security axis)
- `04-Coding-Guidelines/09-testing/` — Testing guidelines (for tests axis)
- `AGENTS.md` — Project-specific rules

**Write**:
- New review patterns / common issues → `04-Coding-Guidelines/`

## Error Handling

| Error | Resolution |
|-------|-----------|
| gh CLI not installed | Install gh CLI, or use git + curl for PR creation |
| gh not authenticated | Run `gh auth login`, or use GitHub API with token |
| PR creation fails | Check branch exists, check permissions, retry |
| Diff too large (>1000 lines) | Review in chunks, focus on most changed files first |
| No changes in PR | Verify code was committed, check branch |
| Review finds CRITICAL issues | Post comments, request changes, do NOT proceed to deployment |
| Human rejects 2 times | Escalate to tech lead, create escalation ticket |
| False positives high | Increase confidence threshold to 0.8, add more context reading |
| API rate limited | Wait and retry, or use cached diff |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/06-pr-review/pr.diff` — PR diff
- `docs/operations/{JIRA_KEY}/06-pr-review/changed-files.txt` — Changed files list
- `docs/operations/{JIRA_KEY}/06-pr-review/pr-meta.json` — PR metadata
- `docs/operations/{JIRA_KEY}/06-pr-review/review-summary.md` — Review summary
- `docs/operations/{JIRA_KEY}/06-pr-review/human-decision.md` — Human decision record
- `docs/operations/{JIRA_KEY}/06-pr-review/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/06-pr-review/operation-log.md` — Operation log
- GitHub PR with review comments

---

*PR Review Skill v2.0.0 — 2026-08-24*
*Optimized with: 5-axis find-then-verify pipeline, confidence scoring (>=0.7 threshold), severity classification, CRITICAL rules, precise allowed-tools, human approval enforcement*
