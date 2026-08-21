---
name: poc-workflow
description: Run the POC AI-driven development workflow for a Jira ticket. 7 stages: ticket intake → requirements → SDD → test cases (TDD) → code generation → PR review → deployment. Every stage has verify gates. Knowledge base is read at every stage and updated when new patterns are discovered.
arguments:
  - name: jira_key
    description: Jira ticket key (e.g., CBOL-123)
    required: true
  - name: stage
    description: Start from a specific stage (1-7). Default: 1 (full pipeline)
    required: false
  - name: resume
    description: Resume from saved pipeline state (true/false). Default: false
    required: false
---

# POC Workflow Command

Run the POC AI-driven development workflow for a Jira ticket.

## Usage

```bash
/poc-workflow jira_key=CBOL-123
/poc-workflow jira_key=CBOL-123 stage=3
/poc-workflow jira_key=CBOL-123 resume=true
```

## Prerequisites

Before running, ensure:
1. Jira API credentials configured in `.ai-workflow/config.yaml`
2. Knowledge base directories exist (`01-` through `06-`)
3. Git repository initialized with remote
4. OpenCode skills loaded (`06-Skills/`)

## Pipeline Stages

| Stage | Name | Verify Type | KB Access |
|-------|------|-------------|-----------|
| 1 | Ticket Intake | Automated | Read |
| 2 | Requirements | Human | Read + Write |
| 3 | SDD | Human | Read + Write |
| 4 | Test Cases (TDD RED) | Automated | Read |
| 5 | Code Generation (TDD GREEN) | Automated | Read + Write |
| 6 | PR + Auto Review | Automated + Human | Read |
| 7 | Deployment | Automated | Read |

## Execution Instructions

### Step 1: Initialize

1. Read `07-Workflows/poc-workflow/workflow-spec.md` for full pipeline specification
2. Read `07-Workflows/poc-workflow/jira-ticket-spec.md` for ticket validation rules
3. Read `07-Workflows/poc-workflow/verify-checklist.md` for verify gate criteria
4. Read `07-Workflows/poc-workflow/knowledge-integration.md` for KB read/write protocol
5. Create operation directory: `docs/operations/{JIRA_KEY}/`
6. Initialize `pipeline-state.json`

### Step 2: Run Stages Sequentially

For each stage (starting from `stage` argument or 1):

1. **Read stage doc**: `07-Workflows/poc-workflow/stages/0{stage}-*.md`
2. **Inject KB**: Read relevant KB docs per `knowledge-integration.md`
3. **Execute stage**: Follow stage execution steps
4. **Verify gate**: Run verify checks per `verify-checklist.md`
5. **Save artifacts**: Write all outputs to `docs/operations/{JIRA_KEY}/0{stage}-*/`
6. **Update state**: Update `pipeline-state.json`
7. **Check verify result**:
   - PASS → proceed to next stage
   - FAIL → retry (max 3), then escalate

### Step 3: Handle Human Gates

For Stages 2 (requirements) and 3 (SDD):
1. Generate document
2. Present to human for review
3. Wait for explicit approval
4. If approved → proceed
5. If rejected → incorporate feedback, regenerate, re-request (max 2 rejections)

### Step 4: Handle TDD (Stages 4-5)

**Stage 4 (RED)**:
1. Write test cases ONLY (no production code)
2. Run tests, confirm they FAIL for right reason (assertion failure, not compile error)
3. Save RED output

**Stage 5 (GREEN)**:
1. Write minimal code to make tests pass
2. Do NOT modify tests
3. Run tests, confirm ALL pass
4. Check coverage >= 80% line / 70% branch
5. Refactor if needed (keep tests green)

### Step 5: Handle Escalation

If any stage fails verify 3 times:
1. STOP the pipeline
2. Save current state
3. Create escalation ticket in Jira
4. Notify human with:
   - Pipeline ID and Jira key
   - Stage that failed
   - Last 3 verify reports
   - Suggested resolution options
5. Wait for human intervention

### Step 6: Complete

After Stage 7 verify PASS:
1. Mark pipeline complete in `pipeline-state.json`
2. Commit all operation logs
3. Update Jira ticket status (if applicable)
4. Summarize pipeline execution

## State Management

Pipeline state is saved in `docs/operations/{JIRA_KEY}/pipeline-state.json`:

```json
{
  "pipeline_id": "{JIRA_KEY}-{timestamp}-{hash}",
  "jira_key": "{JIRA_KEY}",
  "current_stage": 3,
  "status": "in_progress",
  "stages": {
    "1_ticket_intake": { "status": "completed", "verify_passed": true },
    "2_requirements": { "status": "completed", "verify_passed": true },
    "3_sdd": { "status": "in_progress", "verify_passed": false }
  }
}
```

To resume: use `resume=true` argument, pipeline reads state and continues from current stage.

## Knowledge Base Integration

At every stage:
1. **Read** relevant KB docs (see `knowledge-integration.md` for stage-specific mappings)
2. **Write** new patterns/discoveries back to KB (Stages 2, 3, 5)
3. **Log** all KB reads/writes in operation log

KB directories:
- `01-CBOL-Domain-Knowledge/` — CBOL-specific domain
- `02-Chat-Domain-Knowledge/` — Generic IM + Java refs
- `03-Design-Guidelines/` — Architecture/API/data/security
- `04-Coding-Guidelines/` — Java/Spring/WS/DB/cache/queue
- `06-Skills/` — OpenCode-compatible skills

## References

- [Workflow Specification](../workflow-spec.md)
- [Jira Ticket Spec](../jira-ticket-spec.md)
- [Stage Documents](../stages/)
- [Verify Checklist](../verify-checklist.md)
- [Knowledge Integration](../knowledge-integration.md)
- [POC Workflow Skill](../skills/poc-pipeline/SKILL.md)

---

*POC Workflow Command v1.0.0 — 2026-08-21*
