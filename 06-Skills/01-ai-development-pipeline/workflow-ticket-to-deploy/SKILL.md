# Workflow: Ticket to Deploy

> Orchestration skill for the Jira-driven AI development pipeline. One entry point, 7 stages, 6 approval gates, zero silent failures.

## Core Principles (Non-Negotiable)

1. **Design before code** — SDD must be human-approved before any production code
2. **Evidence over claims** — Every completion needs `command + output + exit code`
3. **3-strike escalation** — Auto-retry 3×, then STOP and ask human. Never infinite loop.
4. **Scope discipline** — Only implement what's in the approved SDD. If new requirements emerge, stop and escalate.
5. **State persistence** — After every gate, write state file. Resume from breakpoint, never from start.
6. **Knowledge injection** — Every stage reads relevant KB docs before acting.

---

## Pipeline

```
CBOL-XXX
  │
  ▼
[0] Ticket Intake ──── [Gate 0] Clarity? ──── No → post questions, pause
  │                                    │
  │                                   Yes
  ▼
[1] Requirements ───── [Gate 1] Approve? ─── No → revise (max 3) → escalate
  │                                    │
  │                                   Yes
  ▼
[2] SDD ───────────── [Gate 2] Approve? ─── No → revise (max 3) → escalate
  │                                    │
  │                                   Yes
  ▼
[3] TDD Implement ─── [Gate 3] Auto-review ── Fail → fix (max 3) → escalate
  │                                    │
  │                                   Pass
  ▼
[4] Test ──────────── [Gate 4] Approve? ─── No → fix (max 3) → escalate
  │                                    │
  │                                   Yes
  ▼
[5] PR ────────────── [Gate 5] Peer review ─ Reject → fix (max 3) → escalate
  │                                    │
  │                                   Approve
  ▼
[6] Deploy + Docs ──── Complete
```

---

## State Management

### State File

After every gate, write `.ai-workflow/state/{JIRA-KEY}.json`:

```json
{
  "jira_key": "CBOL-123",
  "current_stage": "stage2_sdd",
  "last_completed_gate": "gate1",
  "stages": {
    "stage0_ticket": { "status": "completed", "version": 1, "evidence": "docs/operations/CBOL-123/00-ticket.md" },
    "stage1_requirements": { "status": "completed", "version": 2, "evidence": "docs/operations/CBOL-123/01-requirements-v2.md" },
    "stage2_sdd": { "status": "in_progress", "version": 1, "evidence": null },
    "stage3_implementation": { "status": "pending", "version": 0, "evidence": null },
    "stage4_test": { "status": "pending", "version": 0, "evidence": null },
    "stage5_pr": { "status": "pending", "version": 0, "evidence": null },
    "stage6_deploy": { "status": "pending", "version": 0, "evidence": null }
  },
  "gates": {
    "gate0": { "status": "passed", "approved_at": "2026-08-19T10:00:00Z" },
    "gate1": { "status": "passed", "approved_at": "2026-08-19T11:00:00Z", "revision_count": 1 },
    "gate2": { "status": "pending" }
  },
  "retry_counts": {
    "stage2_sdd": 0,
    "stage3_implementation": 0,
    "stage4_test": 0,
    "stage5_pr": 0
  },
  "created_at": "2026-08-19T09:00:00Z",
  "updated_at": "2026-08-19T11:30:00Z"
}
```

### Resume Logic

On invocation:
1. Read state file (if exists)
2. If `current_stage` is set and not `completed`, resume from that stage
3. If state file doesn't exist, start from Stage 0
4. Always read the last completed stage's operation log for context

---

## Anti-Drift Checks (Run Before Every Stage)

Before starting any stage, verify:

| Check | Action if Failed |
|-------|-----------------|
| Previous stage has `status: completed` | Stop, report missing stage |
| Previous stage has evidence (operation log exists) | Stop, report missing evidence |
| Previous gate has `status: passed` | Stop, report unapproved gate |
| Current ticket matches state file `jira_key` | Stop, report mismatch |
| Retry count < 3 for current stage | Stop, escalate to human |
| SDD (if Stage 3+) is the approved version | Stop, report SDD mismatch |
| No out-of-scope changes in working tree | Stop, report uncommitted changes |

**If any check fails**: Do NOT proceed. Report the failure to the human and wait for instructions.

---

## Stage Execution Protocol

For every stage:

1. **Pre-check**: Run anti-drift checks
2. **Load context**: Read state file + last operation log + relevant KB docs
3. **Execute**: Run the stage's skill/inline logic
4. **Collect evidence**: Capture command + output + exit code
5. **Write operation log**: `docs/operations/{KEY}/{NN}-{stage}-v{N}.md`
6. **Update state**: Set stage status to `completed`, increment version, update evidence path
7. **Present for gate**: Summarize and wait for human approval

---

## Operation Log Schema

```markdown
# {Stage Name} — {JIRA-KEY} (v{N})

## Did
{What was done}

## Impact
{Files changed, components, risk}

## Could Not
{What was NOT done and why}

## Engineering Decisions
{Key decisions + trade-offs}

## Evidence
- Command: `{command}`
- Output: {summary}
- Exit code: {0/1}

## Next
{Handoff to next stage}
```

---

## Knowledge Injection Map

| Stage | Must Read |
|-------|-----------|
| 0 — Ticket | `01-CBOL-Domain-Knowledge/README.md` |
| 1 — Requirements | `01-CBOL-Domain-Knowledge/` (relevant subdirs) |
| 2 — SDD | `02-Chat-Domain-Knowledge/` (keyword search) + `03-Design-Guidelines/` (all) |
| 3 — TDD | `04-Coding-Guidelines/` (all) + `02/java-implementation/` + `02/code-templates/` + `02/data-structures/` |
| 4 — Test | `04/code-quality.md` + `04/sonar-rules.md` |
| 5 — PR | `03/api-design-guidelines.md` |
| 6 — Deploy | `01/configuration/` + `01/deployment-architecture/` |

---

## Usage

```bash
# Start or resume pipeline
/workflow-ticket-to-deploy jira_key=CBOL-123

# Force start from a specific stage (bypasses resume logic)
/workflow-ticket-to-deploy jira_key=CBOL-123 start_from=stage3

# Show current state without executing
/workflow-ticket-to-deploy jira_key=CBOL-123 status
```

---

## Configuration

See `.ai-workflow/config.example.yaml` for full configuration. Copy to `.ai-workflow/config.yaml` and fill in your values.

Key config sections:
- `jira`: Jira Cloud/Server connection + custom field mapping
- `github`: Repository + API token
- `knowledge_base.stage_injection`: KB docs per stage
- `limits`: max revision loops, test retries, CI repair loops
- `branch`: branch naming pattern
- `tdd`: test framework, coverage thresholds
- `build`: Maven/Gradle commands

---

## Stage Skills

| Stage | Skill | Type |
|-------|-------|------|
| 0 — Ticket | `jira-ticket-fetcher` | External skill |
| 1 — Requirements | (inline) | Built-in |
| 2 — SDD | `sdd-generator` | External skill |
| 3 — TDD | `tdd-implementer` | External skill |
| 4 — Test | `test-verifier` | External skill |
| 5 — PR | `pr-creator` | External skill |
| 6 — Deploy | `deploy-doc-updater` | External skill |

---

## Escalation Template

When 3-strike limit is hit, post to Jira and pause:

```
🤖 AI Pipeline Escalation — {JIRA-KEY}

Stage: {stage_name}
Attempts: 3 failed

## What We Tried
1. {attempt 1} → {result}
2. {attempt 2} → {result}
3. {attempt 3} → {result}

## Root Cause (best guess)
{analysis or "unable to determine"}

## Recommended Next Steps
- [ ] Human review the failure
- [ ] Check environment/config
- [ ] Review recent changes

State file: .ai-workflow/state/{JIRA-KEY}.json
Logs: docs/operations/{JIRA-KEY}/
```

---

## References

- Forge (approval gates, CI repair): https://github.com/forge-sdlc/forge
- Jira-Flow (TDD, evidence, circuit breaker): https://github.com/jinx911/jira-flow
- ai-coding-workflow (pull-based, operation logs): https://github.com/wenttt/ai-coding-workflow
- Full comparison: `05-References/ai-driven-development.md`
