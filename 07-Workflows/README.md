# 07-Workflows

> AI-driven development workflows for CBOL Messaging Hub. Contains the Jira ticket-to-deploy pipeline specification, reference workflows from industry projects, and best practices for AI-assisted software development.

## Documents

| Document | Description |
|----------|-------------|
| [ticket-to-deploy-workflow.md](./ticket-to-deploy-workflow.md) | Complete specification of the CBOL Jira ticket-to-deploy AI pipeline: 7 stages, 6 approval gates, state management, anti-drift checks, knowledge injection, escalation protocol |
| [reference-workflows.md](./reference-workflows.md) | Comparison of 8+ industry AI development workflows from GitHub and open source projects, with star counts, key features, and lessons learned |
| [best-practices.md](./best-practices.md) | Consolidated best practices for AI-driven development: pipeline design, human-in-the-loop, evidence-based completion, anti-drift, knowledge injection, tooling recommendations |

## Workflow Overview

```
CBOL-XXX (Jira Ticket)
  │
  ▼
[0] Ticket Intake ──── [Gate 0] Clarity?
  │
  ▼
[1] Requirements ───── [Gate 1] Approve?
  │
  ▼
[2] SDD ───────────── [Gate 2] Approve?
  │
  ▼
[3] TDD Implement ─── [Gate 3] Auto-review
  │
  ▼
[4] Test ──────────── [Gate 4] Approve?
  │
  ▼
[5] PR ────────────── [Gate 5] Peer review
  │
  ▼
[6] Deploy + Docs ──── Complete
```

## Core Principles

1. **Design before code** — SDD must be human-approved before any production code
2. **Evidence over claims** — Every completion needs `command + output + exit code`
3. **3-strike escalation** — Auto-retry 3×, then STOP and ask human
4. **Scope discipline** — Only implement what's in the approved SDD
5. **State persistence** — Resume from breakpoint, never from start
6. **Knowledge injection** — Every stage reads relevant KB docs before acting
7. **Human-in-the-loop** — Critical decisions require human approval gates

## Related Files

- `.ai-workflow/config.example.yaml` — Pipeline configuration template
- `.ai-workflow/project_mapping.yaml` — Jira-to-GitHub project mapping
- `.ai-workflow/templates/pr-body.md` — PR body template
- `.ai-workflow/state/` — Pipeline state files (gitignored)
- `.opencode/commands/workflow.md` — OpenCode `/workflow` command
- `06-Skills/01-ai-development-pipeline/workflow-ticket-to-deploy/` — Orchestration skill
- `AGENTS.md` — AI agent instructions (pipeline section)

---

*Last updated: 2026-08-21*
