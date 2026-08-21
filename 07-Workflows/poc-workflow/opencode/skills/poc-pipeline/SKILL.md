---
name: poc-pipeline
description: POC AI-driven development pipeline skill. Orchestrates 7-stage workflow from Jira ticket to deployment with verify gates, TDD enforcement, knowledge base integration, and 3-strike escalation. Use when user wants to run the full POC workflow for a Jira ticket.
version: 1.0.0
author: CBOL Self-Development
tags: [pipeline, workflow, jira, tdd, sdd, deployment, poc]
triggers:
  - "run poc workflow"
  - "poc pipeline"
  - "jira to deploy"
  - "ticket to deployment"
---

# POC Pipeline Skill

Orchestrates the 7-stage POC AI-driven development workflow.

## When to Use

- User wants to run the full POC workflow for a Jira ticket
- User says "run poc workflow", "poc pipeline", "jira to deploy"
- User provides a Jira ticket key and wants end-to-end development

## Prerequisites Check

Before starting, verify:

```bash
# Check KB directories exist
ls -d 01-CBOL-Domain-Knowledge 02-Chat-Domain-Knowledge 03-Design-Guidelines 04-Coding-Guidelines 06-Skills

# Check workflow docs exist
ls 07-Workflows/poc-workflow/workflow-spec.md

# Check git initialized
git rev-parse --is-inside-work-tree
```

If any check fails, report to user and stop.

## Pipeline Execution

### Phase 0: Initialization

1. **Read pipeline spec**: `07-Workflows/poc-workflow/workflow-spec.md`
2. **Read ticket spec**: `07-Workflows/poc-workflow/jira-ticket-spec.md`
3. **Read verify checklist**: `07-Workflows/poc-workflow/verify-checklist.md`
4. **Read KB integration**: `07-Workflows/poc-workflow/knowledge-integration.md`
5. **Create operation directory**:
   ```bash
   mkdir -p docs/operations/{JIRA_KEY}/{01-ticket-intake,02-requirements,03-sdd,04-test-cases,05-code-generation,06-pr-review,07-deployment}
   ```
6. **Initialize state**: Write `pipeline-state.json`

### Phase 1: Stage Execution Loop

For each stage (1-7):

#### Step A: Read Stage Documentation

```bash
cat 07-Workflows/poc-workflow/stages/0{STAGE}-*.md
```

#### Step B: Inject Knowledge Base

Per `knowledge-integration.md`, read stage-specific KB docs:

| Stage | KB Docs to Read |
|-------|-----------------|
| 1 | `06-Skills/02-code-analysis/`, `jira-ticket-spec.md` |
| 2 | `01-CBOL-Domain-Knowledge/README.md`, `02-Chat-Domain-Knowledge/` (search by label) |
| 3 | `03-Design-Guidelines/` (ALL), `01-CBOL-Domain-Knowledge/state-machine/`, `02-Chat-Domain-Knowledge/websocket/` |
| 4 | `04-Coding-Guidelines/07-testing/` (ALL) |
| 5 | `04-Coding-Guidelines/` (ALL relevant), `03-Design-Guidelines/` (ALL) |
| 6 | `04-Coding-Guidelines/06-quality-ops/`, `04-Coding-Guidelines/05-security/` |
| 7 | `03-Design-Guidelines/05-reliability/` (ALL) |

Search KB for ticket-specific keywords:
```bash
grep -r "{keyword}" 01-CBOL-Domain-Knowledge/ 02-Chat-Domain-Knowledge/ 03-Design-Guidelines/ 04-Coding-Guidelines/ --include="*.md" -l
```

#### Step C: Execute Stage

Follow stage-specific execution steps from `stages/0{STAGE}-*.md`.

**Stage 1 (Ticket Intake)**:
- Fetch Jira ticket via API
- Validate against ticket spec
- Normalize to JSON
- Save artifacts

**Stage 2 (Requirements)**:
- Generate requirements doc from ticket + KB
- Present to human for approval
- If approved, proceed; if rejected, incorporate feedback
- Write new domain terms to KB (if any)

**Stage 3 (SDD)**:
- Analyze codebase (use architecture-analyzer skill if available)
- Generate SDD from requirements + KB + codebase
- Include Mermaid diagrams (architecture, ER, state machine)
- Present to human for review
- If approved, proceed; if rejected, incorporate feedback
- Write new ADRs to KB (if any)

**Stage 4 (Test Cases — TDD RED)**:
- Generate test plan with traceability matrix
- Write test cases ONLY (no production code)
- Run tests, confirm they FAIL
- Verify failure is for right reason (assertion, not compile error)
- Save RED output

**Stage 5 (Code Generation — TDD GREEN)**:
- Write minimal code to make tests pass
- Do NOT modify tests from Stage 4
- Run tests, confirm ALL pass
- Check coverage >= 80% line / 70% branch
- Refactor if needed (keep tests green)
- Verify code meets SDD requirements
- Write new coding patterns to KB (if any)
- Commit with conventional format

**Stage 6 (PR + Auto Review)**:
- Create feature branch
- Push to remote
- Create PR with structured description
- Run auto PR review (code quality, security, test quality, architecture)
- Wait for CI to pass
- Request human review
- If approved, proceed; if changes requested, fix and push

**Stage 7 (Deployment)**:
- Trigger deployment (CI/CD pipeline)
- Wait for rollout complete
- Run health check
- Run smoke tests
- Monitor logs/metrics
- If healthy, mark complete; if not, rollback + escalate

#### Step D: Verify Gate

Run verify checks per `verify-checklist.md` for current stage.

For each check:
1. Execute check (command or manual)
2. Record result (PASS/FAIL)
3. Save evidence (command + output + exit code)

If ALL checks PASS → verify gate PASS
If ANY check FAILS → verify gate FAIL

#### Step E: Save Artifacts

Write all stage outputs to `docs/operations/{JIRA_KEY}/0{STAGE}-*/`:
- Stage-specific artifacts (ticket.json, requirements.md, sdd.md, etc.)
- `verify-report.md` — Verify gate results
- `operation-log.md` — Step-by-step log with commands + output + exit codes

#### Step F: Update State

Update `pipeline-state.json`:
```json
{
  "current_stage": {NEXT_STAGE},
  "stages": {
    "{STAGE}_name": {
      "status": "completed",
      "verify_passed": true,
      "retry_count": {N},
      "completed_at": "{ISO_TIMESTAMP}"
    }
  }
}
```

#### Step G: Check Verify Result

- **PASS** → Proceed to next stage
- **FAIL** → Increment retry count:
  - If retry < 3 → Re-execute stage (go to Step C)
  - If retry = 3 → Escalate (see Phase 2)

### Phase 2: Escalation

If any stage fails verify 3 times:

1. **STOP** pipeline execution
2. **Save** current state to `pipeline-state.json`
3. **Create** escalation ticket in Jira:
   - Type: `Escalation`
   - Title: `[ESCALATION] Pipeline failed at Stage {N} for {JIRA_KEY}`
   - Description: Pipeline ID, stage, last 3 verify reports, suggested resolutions
4. **Notify** human with summary
5. **Wait** for human intervention
6. **Resume** from saved state after human resolves

### Phase 3: Completion

After Stage 7 verify PASS:

1. Mark pipeline complete in `pipeline-state.json`
2. Commit all operation logs:
   ```bash
   git add docs/operations/{JIRA_KEY}/
   git commit -m "docs(operations): pipeline complete for {JIRA_KEY}"
   ```
3. Update Jira ticket status (if applicable)
4. Summarize pipeline execution:
   - Pipeline ID
   - Jira key
   - Total duration
   - Stages completed
   - KB updates written
   - PR URL
   - Deployment status

## TDD Enforcement (Critical)

Stages 4-5 enforce strict TDD:

### RED Rules (Stage 4)
- ✅ Write tests FIRST
- ✅ Tests MUST fail (run them to confirm)
- ✅ Failure MUST be assertion failure (not compile error)
- ❌ NO production code in Stage 4
- ❌ NO modifying tests to make them pass

### GREEN Rules (Stage 5)
- ✅ Write MINIMAL code to make tests pass
- ✅ Do NOT modify tests from Stage 4
- ✅ All tests MUST pass
- ✅ Coverage >= 80% line / 70% branch
- ❌ NO code outside SDD scope
- ❌ NO cheating (modifying tests, skipping tests)

### Verification
After Stage 5, verify:
```bash
# Check no test modifications from Stage 4
git diff --name-only HEAD~1 HEAD | grep -E "test/|Test\.java"
# Should show NO test file modifications (tests were added in Stage 4, not modified in Stage 5)

# Check all tests pass
mvn test
# Exit code must be 0

# Check coverage
mvn jacoco:report
# Line coverage >= 80%, branch coverage >= 70%
```

## Knowledge Base Write-Back

At Stages 2, 3, 5, write new knowledge back to KB:

### Stage 2 → `01-CBOL-Domain-Knowledge/glossary/`
- New domain terms discovered in requirements
- Format: `term-name.md` with definition, context, references

### Stage 3 → `03-Design-Guidelines/06-design-process/adr/`
- New ADRs (Architecture Decision Records)
- Format: `NNNN-title.md` with context, decision, consequences

### Stage 5 → `04-Coding-Guidelines/` (relevant subdir)
- New coding patterns, conventions
- Format: follow existing doc format in target directory

### Write Rules
1. Only write genuinely new knowledge (search KB first)
2. Follow existing format in target directory
3. Commit separately from code changes
4. Reference in operation log

## Operation Log Format

Every stage writes `operation-log.md`:

```markdown
# Operation Log — Stage {N}: {Stage Name}

**Pipeline ID**: {PIPELINE_ID}
**Jira Key**: {JIRA_KEY}
**Started At**: {ISO_TIMESTAMP}
**Completed At**: {ISO_TIMESTAMP}
**Retry Count**: {N}

## KB Docs Read
- `{path}` — {purpose}

## KB Search Queries
- "{query}" → {N} docs found

## Steps Executed

### Step 1: {Description}
- **Command**: `{command}`
- **Output**: `{output summary}`
- **Exit Code**: {0/1}

### Step 2: {Description}
...

## KB Updates Written
- `{path}` — {description}
- Commit: `{commit_hash}`

## Artifacts Produced
- `{path}` — {description}

## Verify Gate
- **Status**: PASS / FAIL
- **Checks Passed**: {N}/{M}
- **Failed Checks**: [list]
- **Evidence**: [command + output + exit code]
```

## Error Handling

### Common Errors and Resolutions

| Error | Resolution |
|-------|-----------|
| Jira API auth failed | Check `.ai-workflow/config.yaml` credentials |
| KB directory not found | Run `git pull` to get latest KB |
| Tests don't fail (Stage 4) | Check if implementation already exists — may be out of scope |
| Tests fail with compile error (Stage 4) | Fix test to reference existing classes, or create stubs |
| Coverage below threshold (Stage 5) | Add more tests for uncovered code |
| PR CI fails | Check CI logs, fix issues, push |
| Deployment health check fails | Check logs, rollback, escalate if persistent |

### Retry Logic

- Stage failure → retry same stage (max 3)
- Each retry starts from stage beginning (not partial)
- After 3 failures → escalate to human

## References

- [Workflow Specification](../../workflow-spec.md)
- [Jira Ticket Spec](../../jira-ticket-spec.md)
- [Stage Documents](../../stages/)
- [Verify Checklist](../../verify-checklist.md)
- [Knowledge Integration](../../knowledge-integration.md)
- [POC Workflow Command](../commands/poc-workflow.md)
- [CBOL Ticket-to-Deploy Pipeline](../../ticket-to-deploy-workflow.md)
- [Reference Workflows](../../reference-workflows.md)
- [Best Practices](../../best-practices.md)

---

*POC Pipeline Skill v1.0.0 — 2026-08-21*
