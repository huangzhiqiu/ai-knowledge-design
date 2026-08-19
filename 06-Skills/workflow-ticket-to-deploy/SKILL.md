# Workflow: Ticket to Deploy

> Orchestration skill for the full Jira-driven AI development pipeline: Ticket → Requirements → SDD → Code → Test → PR → Deploy → Doc update.

## Purpose

This skill orchestrates the end-to-end development workflow for CBOL project tickets. It does NOT do the LLM work itself — it coordinates the pipeline, invokes specialized skills at each stage, enforces quality gates, and maintains operation logs.

**Philosophy (synthesized from Forge, Jira-Flow, ai-coding-workflow):**
- AI proposes, human approves at every critical transition
- Evidence-based completion — no "I think it works" without command output + exit code
- Bounded autonomy — auto-retry up to 3 times, then escalate to human
- Controlled write boundaries — agents work locally, external mutations happen at explicit steps
- Design before code — SDD reviewed and approved before implementation
- Resumable — state persisted after each gate, resume from breakpoint

## Pipeline Overview

```
Jira Ticket (CBOL-XXX)
    │
    ▼
[Stage 0: Ticket Intake]
    │  Skill: jira-ticket-fetcher
    │  Output: docs/operations/CBOL-XXX/00-ticket.md
    │
    ▼
[Gate 0: Ticket clarity check]
    │  Is the ticket well-defined? If not, post clarification questions to Jira, pause.
    │
    ▼
[Stage 1: Requirements Analysis]
    │  Skill: (inline) Extract acceptance criteria, propose 2-3 implementation approaches with trade-offs
    │  Inject: 01-CBOL-Domain-Knowledge/
    │  Output: docs/operations/CBOL-XXX/01-requirements-v{N}.md
    │
    ▼
[Gate 1: Human review requirements]
    │  Approve / request revisions / ask questions
    │  Max 3 revision loops, then escalate
    │
    ▼
[Stage 2: SDD Generation]
    │  Skill: sdd-generator
    │  Inject: 02-Chat-Domain-Knowledge/ + 03-Design-Guidelines/
    │  Output: design/sdd/CBOL-XXX.md + docs/operations/CBOL-XXX/02-sdd-v{N}.md
    │
    ▼
[Gate 2: Human review SDD]
    │  Approve / request revisions / ask questions
    │  Max 3 revision loops
    │
    ▼
[Stage 3: Implementation (TDD)]
    │  Skill: (inline) TDD mode — RED → GREEN → REFACTOR → Commit
    │  Inject: 04-Coding-Guidelines/ + 02/java-implementation/ + 02/code-templates/
    │  Output: code + unit tests + docs/operations/CBOL-XXX/03-implementation-v{N}.md
    │
    ▼
[Gate 3: Self review (automated)]
    │  Check: coding standards, security guidelines, concurrency guidelines, exception/logging
    │  Auto-fix minor issues, flag major issues for human
    │
    ▼
[Stage 4: Test & Verification]
    │  Run: mvn test → collect evidence (output + exit code)
    │  Failure → fix loop (max 3) → escalate if still failing
    │  Output: test report + docs/operations/CBOL-XXX/04-test-v{N}.md
    │
    ▼
[Gate 4: Human review test quality]
    │
    ▼
[Stage 5: PR Creation]
    │  Create branch: feat/CBOL-XXX-{short-desc}
    │  Commit, push, create PR (PR body links SDD + Jira)
    │  CI repair loop (max 3)
    │  Output: PR URL + docs/operations/CBOL-XXX/05-pr-v{N}.md
    │
    ▼
[Gate 5: Peer review (enforced)]
    │  Ticket creator cannot be approver
    │  Rejected → fix loop (max 3) → escalate
    │
    ▼
[Stage 6: Deploy & Doc Update]
    │  Generate/update deployment config
    │  Update relevant knowledge base docs
    │  Update Jira status → Done, post summary comment
    │  Output: docs/operations/CBOL-XXX/06-deploy-v{N}.md
    │
    ▼
[Complete]
```

## Operation Log Schema

Every stage writes a structured operation log to `docs/operations/{JIRA-KEY}/{NN}-{stage}-v{N}.md`:

```markdown
# {Stage Name} — {JIRA-KEY} (v{N})

## Did
{What was done in this stage}

## Impact
{Files changed, components affected, risk level}

## Could Not
{What was NOT done and why (blocked, out of scope, needs human)}

## Engineering Decisions
{Key decisions made, trade-offs considered, alternatives rejected}

## Evidence
- Command: `{command}`
- Output: {summary}
- Exit code: {0/1/...}

## Next
{What the next stage should do, any handoff notes}
```

## Gate Check Protocol

At each gate, the orchestrator must:
1. Read the operation log from the previous stage
2. Verify evidence exists (command + output + exit code)
3. Check for "Could Not" items that need human attention
4. Present summary to human for approval
5. On approval: advance to next stage
6. On revision request: loop back to stage with feedback (max 3 loops)
7. After 3 failed loops: escalate, pause workflow, post to Jira

## Knowledge Injection Map

| Stage | Must Read From Knowledge Base |
|-------|-------------------------------|
| Stage 0 (Ticket) | `01-CBOL-Domain-Knowledge/README.md` |
| Stage 1 (Requirements) | `01-CBOL-Domain-Knowledge/` (relevant subdirs) |
| Stage 2 (SDD) | `02-Chat-Domain-Knowledge/` (keyword search) + `03-Design-Guidelines/` (all) |
| Stage 3 (Implementation) | `04-Coding-Guidelines/` (all) + `02/java-implementation/` + `02/code-templates/` + `02/data-structures/` |
| Stage 4 (Test) | `04/code-quality.md` + `04/sonar-rules.md` |
| Stage 5 (PR) | `03/api-design-guidelines.md` (API consistency check) |
| Stage 6 (Deploy) | `01/configuration/` + `01/deployment-architecture/` |

## Usage

### Invocation (OpenCode / Claude Code / Cursor)

```
/workflow-ticket-to-deploy jira_key=CBOL-123
```

Or natural language:
```
Start the ticket-to-deploy workflow for CBOL-123
```

### Resuming from breakpoint

If the workflow was interrupted, re-run the same command. The orchestrator reads `docs/operations/CBOL-123/` to determine the last completed gate and resumes from there.

### Skipping stages (advanced)

For tickets where SDD already exists or implementation is trivial:
```
/workflow-ticket-to-deploy jira_key=CBOL-123 start_from=stage3
```

## Configuration

Create `.ai-workflow/config.yaml` in the project root:

```yaml
jira:
  base_url: https://your-domain.atlassian.net
  project_key: CBOL
  email: your-email@company.com
  api_token: ${JIRA_API_TOKEN}  # from env var

github:
  owner: huangzhiqiu
  repo: cbol-refactor
  default_branch: main

knowledge_base:
  path: ./  # relative path to this knowledge base

operations:
  log_dir: docs/operations
  sdd_dir: design/sdd

limits:
  max_revision_loops: 3
  max_test_retries: 3
  max_ci_repair_loops: 3

branch:
  pattern: "feat/{key}-{short_desc}"
  max_short_desc_length: 30
```

## Related Skills

- `jira-ticket-fetcher` — Stage 0: Fetch and structure Jira tickets
- `sdd-generator` — Stage 2: Generate SDD from requirements + knowledge base
- `code-analyzer` — Stage 3/4: Analyze existing code, check standards
- `doc-generator` — Stage 6: Generate/update documentation

## References

- Forge: https://github.com/forge-sdlc/forge (approval gates, container isolation, CI repair)
- Jira-Flow: https://github.com/jinx911/jira-flow (6 Phases+Gates, TDD discipline, evidence discipline, circuit breaker)
- ai-coding-workflow: https://github.com/wenttt/ai-coding-workflow (pull-based, design-in-Issues code-in-PRs, operation logs first-class)
- Full comparison: `05-References/ai-driven-development.md`
