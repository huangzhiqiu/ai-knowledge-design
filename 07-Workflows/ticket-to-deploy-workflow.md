# Ticket-to-Deploy Workflow Specification

> Complete specification of the CBOL Jira ticket-to-deploy AI development pipeline. One entry point, 7 stages, 6 approval gates, zero silent failures.

## Overview

The Ticket-to-Deploy pipeline is an AI-orchestrated software development workflow that takes a Jira ticket from intake to production deployment. It enforces design-before-code, test-driven development, evidence-based completion, and human approval at critical gates.

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

## Core Principles (Non-Negotiable)

| # | Principle | Description |
|---|-----------|-------------|
| 1 | **Design before code** | SDD must be human-approved before any production code is written |
| 2 | **Evidence over claims** | Every completion claim needs `command + output + exit code` |
| 3 | **3-strike escalation** | Auto-retry 3×, then STOP and ask human. Never infinite loop |
| 4 | **Scope discipline** | Only implement what's in the approved SDD. New requirements → stop and escalate |
| 5 | **State persistence** | After every gate, write state file. Resume from breakpoint, never from start |
| 6 | **Knowledge injection** | Every stage reads relevant KB docs before acting |
| 7 | **Human-in-the-loop** | Critical decisions require human approval at gates |
| 8 | **No silent failures** | Every error is logged, escalated, or handled — never swallowed |

---

## Stage 0: Ticket Intake

### Purpose
Fetch and structure the Jira ticket, verify clarity, and establish the work context.

### Steps
1. Fetch ticket from Jira using `jira-ticket-fetcher` skill
2. Parse ticket fields: summary, description, acceptance criteria, story points, epic link, sprint
3. Map custom fields using `config.yaml` custom_fields mapping
4. Verify ticket clarity:
   - Is the problem statement clear?
   - Are acceptance criteria defined?
   - Is the scope bounded (not too broad)?
   - Are dependencies identified?
5. If unclear → post clarifying questions to Jira, pause pipeline
6. Create working branch: `feat/CBOL-XXX-{short-desc}`
7. Initialize state file: `.ai-workflow/state/CBOL-XXX.json`

### Output
- Structured ticket summary (`docs/operations/CBOL-XXX/00-ticket.md`)
- Working branch created
- State file initialized

### Gate 0: Clarity Check
- **Pass**: Ticket is clear, acceptance criteria defined, scope bounded
- **Fail**: Post questions to Jira, pause pipeline, wait for human response

---

## Stage 1: Requirements Analysis

### Purpose
Translate the ticket into structured functional and non-functional requirements, identify edge cases, and define success criteria.

### Steps
1. Read relevant domain knowledge: `01-CBOL-Domain-Knowledge/`
2. Decompose ticket into:
   - Functional requirements (FR-001, FR-002, ...)
   - Non-functional requirements (NFR-001, ...)
   - User stories
   - Edge cases and error scenarios
   - Dependencies and assumptions
3. Identify affected modules and systems
4. Define acceptance criteria (testable, measurable)
5. Write requirements document

### Output
- Requirements document (`docs/operations/CBOL-XXX/01-requirements-v{N}.md`)
- Requirements traceability matrix (FR → acceptance criteria → test)

### Gate 1: Requirements Approval
- **Human reviews**: Are requirements complete? Is scope correct? Are edge cases covered?
- **Pass**: Human approves → proceed to SDD
- **Fail**: Revise requirements (max 3 loops) → if still failing, escalate

---

## Stage 2: Software Design Document (SDD)

### Purpose
Create a comprehensive design document covering architecture, API, data model, security, reliability, and testing strategy.

### Steps
1. Read knowledge base:
   - `02-Chat-Domain-Knowledge/` (keyword search for relevant patterns)
   - `03-Design-Guidelines/` (all categories)
   - Related ADRs
2. Generate SDD using `sdd-generator` skill with 14 sections:
   1. Context & Problem Statement
   2. Requirements (FR + NFR)
   3. Architecture Overview (with diagram)
   4. Detailed Design (class diagram, sequence diagrams, state machines)
   5. API Design (REST + WebSocket)
   6. Data Design (schema, cache, queue)
   7. Security Design (auth, threat model)
   8. Reliability & Performance (HA, scaling, resilience)
   9. Testing Strategy
   10. Deployment & Operations
   11. Alternatives Considered
   12. Risks & Mitigations
   13. Open Questions
   14. References
3. Self-review against design guidelines checklist
4. Write SDD document

### Output
- SDD document (`design/sdd/CBOL-XXX-sdd-v{N}.md`)
- Architecture diagrams (Mermaid/PlantUML)
- API contract (OpenAPI if applicable)

### Gate 2: SDD Approval
- **Human reviews**: Is design correct? Complete? Consistent with existing architecture?
- **Pass**: Human approves → proceed to implementation
- **Fail**: Revise SDD (max 3 loops) → if still failing, escalate

---

## Stage 3: TDD Implementation

### Purpose
Implement the feature using Test-Driven Development: RED → GREEN → REFACTOR → Commit.

### Steps
1. Read coding guidelines:
   - `04-Coding-Guidelines/` (all categories)
   - `02-Chat-Domain-Knowledge/java-implementation/`
   - `02-Chat-Domain-Knowledge/code-templates/`
   - `02-Chat-Domain-Knowledge/data-structures/`
2. For each task in SDD implementation plan:
   - **RED**: Write a failing test that defines the desired behavior
   - Verify test fails (run test, confirm failure)
   - **GREEN**: Write minimal production code to make test pass
   - Verify test passes (run test, confirm success)
   - **REFACTOR**: Clean up code while keeping tests green
   - **Commit**: `{type}({scope}): {subject} (CBOL-XXX)`
3. Run auto-review:
   - Code quality checks (SonarQube, SpotBugs)
   - Coverage check (line >= 80%, branch >= 70%)
   - Security scan (Semgrep, dependency check)
   - Linting and formatting

### TDD Cycle Rules
- No production code before a failing test exists
- Each commit must have passing tests
- Max 50 TDD cycles per ticket (prevents infinite implementation)
- If stuck on a test for 3 attempts → escalate to human

### Output
- Implementation code (committed to feature branch)
- Test code (unit + integration)
- Auto-review report

### Gate 3: Auto-Review
- **Automated checks**: All tests pass, coverage >= threshold, no critical Sonar issues, no security vulnerabilities
- **Pass**: Proceed to test verification
- **Fail**: Auto-fix (max 3 loops) → if still failing, escalate

---

## Stage 4: Test Verification

### Purpose
Verify the implementation meets all requirements through comprehensive testing.

### Steps
1. Read quality guidelines:
   - `04-Coding-Guidelines/code-quality.md`
   - `04-Coding-Guidelines/sonar-rules.md`
   - `04-Coding-Guidelines/unit-testing-guidelines.md`
2. Run full test suite:
   - Unit tests (all)
   - Integration tests (Testcontainers)
   - API tests (REST Assured)
   - Performance tests (if NFR requires)
3. Verify requirements traceability:
   - Every FR has corresponding test
   - Every acceptance criteria is verified
   - Edge cases covered
4. Generate test report:
   - Coverage report (line, branch, method)
   - Test results (pass/fail/skip)
   - Performance metrics (if applicable)
5. Run SonarQube analysis

### Output
- Test report (`docs/operations/CBOL-XXX/04-test-v{N}.md`)
- Coverage report
- SonarQube analysis results

### Gate 4: Test Approval
- **Human reviews**: Are all requirements tested? Is coverage adequate? Are edge cases covered?
- **Pass**: Human approves → proceed to PR
- **Fail**: Fix tests/implementation (max 3 loops) → if still failing, escalate

---

## Stage 5: Pull Request

### Purpose
Create a pull request, facilitate peer review, and incorporate feedback.

### Steps
1. Read API design guidelines:
   - `03-Design-Guidelines/api-design-guidelines.md`
2. Create PR using `pr-creator` skill:
   - Title: `[CBOL-XXX] {ticket summary}`
   - Body from template (`.ai-workflow/templates/pr-body.md`):
     - Summary
     - Changes (files, modules)
     - SDD link
     - Test results + coverage
     - Screenshots (if UI)
     - Checklist (design approved, tests pass, docs updated)
   - Labels: `ai-generated`, type labels
   - Assignee: PR creator
   - Reviewers: required reviewers (if configured)
3. Enforce no self-approval (ticket creator cannot approve)
4. Address review feedback:
   - For each review comment: fix or respond with rationale
   - Push fixes as new commits
   - Re-request review
5. After approval → merge (squash or rebase per project convention)

### Output
- Pull request (GitHub)
- Review feedback addressed
- Merged to main branch

### Gate 5: Peer Review
- **Human peer review**: Code quality, correctness, maintainability, security, consistency
- **Approve**: Merge to main
- **Request changes**: Fix (max 3 loops) → if still failing, escalate

---

## Stage 6: Deployment & Documentation

### Purpose
Deploy to production and update documentation.

### Steps
1. Read deployment knowledge:
   - `01-CBOL-Domain-Knowledge/configuration/`
   - `01-CBOL-Domain-Knowledge/deployment-architecture/`
2. Deployment (per project CI/CD):
   - Merge to main triggers CI/CD pipeline
   - Build and package
   - Deploy to dev → staging → prod (promotion model)
   - Or: Kubernetes manifest deployment
   - Verify deployment health (readiness probe, smoke test)
3. Update documentation using `deploy-doc-updater` skill:
   - Update API documentation (if API changed)
   - Update runbooks (if operational procedure changed)
   - Update architecture diagrams (if architecture changed)
   - Update ADRs (if new architectural decision)
4. Post-deployment verification:
   - Monitor metrics (error rate, latency)
   - Check logs for errors
   - Verify feature works in production
5. Close Jira ticket (move to Done)

### Output
- Deployed to production
- Documentation updated
- Jira ticket closed
- Post-deployment report

### Completion
- Pipeline complete
- State file marked as `completed`
- Final operation log written

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

### Stage Status Values

| Status | Meaning |
|--------|---------|
| `pending` | Not started yet |
| `in_progress` | Currently executing |
| `completed` | Finished, evidence written, gate passed |
| `failed` | Failed after max retries, escalated |
| `skipped` | Not applicable for this ticket |

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
| 4 — Test | `04/code-quality.md` + `04/sonar-rules.md` + `04/unit-testing-guidelines.md` |
| 5 — PR | `03/api-design-guidelines.md` |
| 6 — Deploy | `01/configuration/` + `01/deployment-architecture/` |

---

## Escalation Protocol

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

### Escalation Triggers

| Trigger | Action |
|---------|--------|
| 3 failed revision loops at any gate | Post escalation, pause |
| 3 failed test attempts | Post escalation, pause |
| 3 failed CI repair loops | Post escalation, pause |
| Max TDD cycles (50) reached | Post escalation, pause |
| Anti-drift check failure | Stop immediately, report |
| Out-of-scope requirement detected | Stop, ask human for direction |
| SDD mismatch (approved vs current) | Stop, report |

---

## Configuration

See `.ai-workflow/config.example.yaml` for full configuration. Copy to `.ai-workflow/config.yaml` and fill in values.

Key config sections:

| Section | Purpose |
|---------|---------|
| `jira` | Jira Cloud/Server connection + custom field mapping |
| `github` | Repository + API token |
| `knowledge_base.stage_injection` | KB docs per stage |
| `limits` | Max revision loops, test retries, CI repair loops, TDD cycles |
| `branch` | Branch naming pattern |
| `commit` | Commit message convention |
| `pr` | PR title, body template, labels, reviewers |
| `tdd` | Test framework, coverage thresholds, mock framework |
| `build` | Maven/Gradle commands |
| `deployment` | Deployment tool, environments, rollback |
| `observability` | Logging, key log points |

---

## Usage

### OpenCode

```bash
# Start or resume pipeline
/workflow jira_key=CBOL-123

# Force start from a specific stage (bypasses resume logic)
/workflow jira_key=CBOL-123 start_from=stage3

# Show current state without executing
/workflow jira_key=CBOL-123 status
```

### Direct Skill Invocation

```bash
# Invoke orchestration skill directly
/workflow-ticket-to-deploy jira_key=CBOL-123
```

### Individual Stage Skills

| Stage | Skill | Command |
|-------|-------|---------|
| 0 — Ticket | `jira-ticket-fetcher` | `/skill jira-ticket-fetcher` |
| 2 — SDD | `sdd-generator` | `/skill sdd-generator` |
| 3 — TDD | `tdd-implementer` | `/skill tdd-implementer` |
| 4 — Test | `test-verifier` | `/skill test-verifier` |
| 5 — PR | `pr-creator` | `/skill pr-creator` |
| 6 — Deploy | `deploy-doc-updater` | `/skill deploy-doc-updater` |

---

## Cross-Project Support

For tickets spanning multiple projects (e.g., backend + frontend):

1. Use `project_mapping.yaml` to define project relationships
2. Contract-first design: define API contracts (OpenAPI/Protobuf/GraphQL/AsyncAPI) before implementation
3. Implementation order: backend (producer) → frontend (consumer) → integration test
4. Integration test stage (4.5) runs after all repos implemented
5. Each project has its own state file, coordinated by the orchestrator

---

## References

- Forge (approval gates, CI repair): https://github.com/forge-sdlc/forge
- Jira-Flow (TDD, evidence, circuit breaker): https://github.com/jinx911/jira-flow
- ai-coding-workflow (pull-based, operation logs): https://github.com/wenttt/ai-coding-workflow
- Full comparison: `./reference-workflows.md`
- Best practices: `./best-practices.md`

---

*Last updated: 2026-08-21*
*Version: 1.0*
