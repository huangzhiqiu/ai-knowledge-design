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
