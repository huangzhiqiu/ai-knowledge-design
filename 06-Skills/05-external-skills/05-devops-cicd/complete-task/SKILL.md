---
name: complete-task
description: Complete Task — Commit changes, push to remote, create pull request, and transition issue to "Code Review" status. Includes Constitutional Review gate, PR body generation with spec/plan context, and CI/CD monitoring.
disable-model-invocation: true
---
# Complete Task

## Overview
Commit changes, push to remote, create pull request, and transition issue to "Code Review" status.

## Definitions
- **{TASK_KEY}**: Story/Issue ID from the issue tracker (e.g., `FB-6`, `PROJ-123`)
- **Branch Name Format**: `{type}/{TASK_KEY}` (e.g., `feat/FB-6`, `fix/PROJ-123`)
- **Spec Summary**: Content from `specs/{FEATURE_DOMAIN}/spec.md` (Blueprint + Contract)
- **Plan Summary**: Content from `.plans/{TASK_KEY}-*.plan.md` (implementation details)
- **Completed Checklist**: Markdown checklist posted as issue comment

## Prerequisites
Before proceeding, verify:
1. **MCP Status Validation**: Test each configured MCP server connection (Atlassian, GitHub). If any fails, STOP.
2. **Branch Verification**: Current branch matches `{type}/{TASK_KEY}`. If not, STOP.
3. **Test Verification**: All tests pass locally. If any fail, STOP and fix them.
4. **Documentation Files**: Check for spec at `specs/{FEATURE_DOMAIN}/spec.md` and plan at `.plans/{TASK_KEY}-*.plan.md`.
5. **Verify Spec Updated**: If code changes affected API contracts/data models, verify spec was updated (Same-Commit Rule).

## Steps

### 1. Prepare commit
- Check for linting errors and fix them
- Run all tests locally to ensure they pass
- Stage all changes
- Create conventional commit message: `{type}: {description} ({TASK_KEY})`
  - Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

### 2. Run Constitutional Review Gate
- **Check if AGENTS.md exists**: If exists, read Operational Boundaries (Tier 1: ALWAYS, Tier 2: ASK, Tier 3: NEVER)
- **Get code diff**: `git diff main...HEAD`
- **Perform Constitutional Review**: Validate code against Constitution tiers
  - For each violation: Category (CRITICAL/WARNING/INFO), Description, Impact, Remediation, Location
- **Gate Decision**:
  - **CRITICAL (Tier 3: NEVER)**: STOP immediately, display violations, instruct user to fix
  - **WARNING/INFO (Tier 2, Tier 1)**: Log, store report for PR body, proceed
  - **No violations**: PASSED, proceed
  - **AGENTS.md not found**: SKIPPED, proceed

### 3. Commit and push changes
- Commit staged changes with conventional commit message
- Push to remote branch
- If push fails (auth, network, conflicts), STOP and report

### 4. Create pull request (optional)
- PR creation is optional. Skip if user prefers manual PR creation.
- If creating PR: CI/CD is a PR gate. Local tests passing is prerequisite.

### 5. Create pull request (if proceeding)
- Get latest commit SHA: `git rev-parse HEAD`
- **Read documentation for PR body**:
  - Spec: extract Context (Blueprint), Definition of Done (Contract), key scenarios
  - Plan: extract Story, Context, Scope, Acceptance Criteria, Implementation Steps
- **Check if PR already exists**: Use GitHub MCP to check
- **Create completed checklist comment** for the issue:
  ```
  ## Completed Checklist
  - [x] Plan reviewed and implemented
  - [x] Code changes completed
  - [x] Unit tests written and passing
  - [x] Constitutional Review passed
  - [x] Spec updated (if behavior changed)
  - [x] Documentation updated
  - [x] Linting errors fixed
  - [x] All tests passing locally
  - [x] Changes committed and pushed
  - [x] PR created (if applicable)
  Pull Request: {PR_URL}
  ```
- **Create PR** with:
  - Title: `{type}: {description} ({TASK_KEY})`
  - Body includes:
    - Constitutional Review report (PASSED / WARNING / SKIPPED)
    - Feature Spec summary (if spec exists)
    - Implementation Plan summary (if plan exists)
    - Verification checklist
    - Link to issue: `Closes {TASK_KEY}`
  - Set base branch (typically `main` or `develop`)
- **Link PR to the issue**
- **Monitor CI/CD status**: Use GitHub MCP to check CI/CD status

### 6. Update issue
- **If PR was created**: Add PR link as comment to issue
- **If PR was not created**: Add comment indicating changes are committed and pushed
- **Transition issue to "Code Review" status**:
  - Get available transitions using Atlassian MCP
  - Find transition to "Code Review"
  - Transition issue
  - Verify status updated

## Tools

### MCP Tools (Atlassian)
- `mcp_atlassian_atlassianUserInfo` - Verify Atlassian MCP connection
- `mcp_atlassian_getAccessibleAtlassianResources` - Get cloudId
- `mcp_atlassian_getJiraIssue` - Fetch story details
- `mcp_atlassian_getTransitionsForJiraIssue` - Get available status transitions
- `mcp_atlassian_transitionJiraIssue` - Transition issue status
- `mcp_atlassian_addCommentToJiraIssue` - Add comments

### MCP Tools (GitHub)
- `mcp_github_list_branches` - List branches
- `mcp_github_list_commits` - Get latest commit SHA
- `mcp_github_get_commit` - Get commit details
- `mcp_github_get_pull_request` - Get PR details / status
- `mcp_github_create_pull_request` - Create new PR

### PR Body Template
```markdown
## Summary
[Brief description of changes]

## Constitutional Review
[PASSED | WARNING with violations | SKIPPED (AGENTS.md not found)]

## Feature Spec
[If spec exists, include Context and Definition of Done]

## Implementation Plan
[If plan exists, include key implementation steps]

## Verification
- Tests: All passing locally
- Linting: No errors
- Constitutional Review: [PASSED | WARNING]

## Related Issue
Closes {TASK_KEY}
```

## Common Failure Scenarios
- **Branch doesn't exist on remote**: Ensure branch was pushed successfully
- **PR already exists**: Check before creating
- **Permission errors**: Verify GitHub MCP authentication
- **Invalid base branch**: Verify base branch exists
- **Transition fails**: Check permissions and workflow rules
- **Push fails**: Check auth, network, conflicts

---
*Source: https://github.com/fancybread-com/sdlc-workflow-skills (skills/complete-task/)*
*Note: Part of SDLC Workflow Skills collection. Requires Atlassian and GitHub MCP servers for full functionality.*
