---
name: requirements
description: Generate a requirements document from a validated Jira ticket + knowledge base. Injects domain knowledge, extracts functional/non-functional requirements, writes acceptance criteria, presents for human approval. Use after ticket-intake, or when you need to generate requirements from a ticket.
version: 1.0.0
author: CBOL Self-Development
tags: [requirements, spec, documentation, human-approval, poc]
triggers:
  - "generate requirements"
  - "write requirements doc"
  - "requirements from ticket"
  - "create spec"
arguments:
  - name: jira_key
    description: Jira ticket key (e.g., CBOL-123)
    required: true
---

# Requirements Skill

Generate requirements document from Jira ticket + knowledge base.

## References

- [genkovich/sdd](https://github.com/genkovich/sdd) — Socratic specify skill pattern
- [codemachine0121/sdd-skill](https://github.com/codemachine0121/sdd-skill) — Domain-driven design skill
- [POC Stage 2 Doc](../../stages/02-requirements.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 2 criteria
- [KB Integration](../../knowledge-integration.md) — KB read/write protocol

## Prerequisites

1. Stage 1 (ticket-intake) completed successfully
2. Normalized ticket exists: `docs/operations/{JIRA_KEY}/01-ticket-intake/ticket.json`
3. Knowledge base directories exist (`01-` through `06-`)
4. Operation directory exists: `docs/operations/{JIRA_KEY}/02-requirements/`

## Execution Steps

### Step 1: Read Ticket

```bash
cat "docs/operations/{JIRA_KEY}/01-ticket-intake/ticket.json"
```

Extract: summary, description, FRs, ACs, labels, components, type, priority.

### Step 2: Inject Knowledge Base

Per [`knowledge-integration.md`](../../knowledge-integration.md), read:

**Mandatory reads**:
- `01-CBOL-Domain-Knowledge/README.md` — Domain context
- `03-Design-Guidelines/06-design-process/sdd-template.md` — Requirements format

**Label-based reads** (search by ticket labels):
- `message-reception` → `01-CBOL-Domain-Knowledge/message-reception/`
- `message-management` → `01-CBOL-Domain-Knowledge/message-management/`
- `message-forwarding` → `01-CBOL-Domain-Knowledge/message-forwarding/`
- `websocket` → `02-Chat-Domain-Knowledge/websocket/`
- `state-machine` → `01-CBOL-Domain-Knowledge/state-machine/`
- `ai-processing` → `01-CBOL-Domain-Knowledge/ai-processing/`
- `agent-transfer` → `01-CBOL-Domain-Knowledge/agent-transfer/`

**Keyword search**:
```bash
grep -r "{keyword from ticket}" 01-CBOL-Domain-Knowledge/ 02-Chat-Domain-Knowledge/ --include="*.md" -l
```

### Step 3: Generate Requirements Document

Write `docs/operations/{JIRA_KEY}/02-requirements/requirements.md` using template:

```markdown
# Requirements — {JIRA_KEY}: {Summary}

**Ticket**: [{KEY}]({JIRA_URL})
**Type**: {Story/Task/Bug}
**Priority**: {High/Medium/Low}
**Generated**: {date}

## Executive Summary
{2-3 paragraph summary from ticket description + KB context}

## Functional Requirements

### FR-001: {Title}
**Description**: {detailed description}
**Source**: Ticket FR-001 / derived from ticket
**Priority**: Must/Should/Could

### FR-002: {Title}
...

## Non-Functional Requirements

### NFR-001: {Performance/Security/Scalability}
**Description**: {requirement}
**Metric**: {quantifiable target}
**Source**: Ticket / KB best practice

## User Stories
- As a {role}, I want to {action}, so that {benefit}

## Acceptance Criteria

### AC-001: {Scenario}
- **Given** {precondition}
- **When** {action}
- **Then** {expected result}

## Dependencies
- {CBOL-XXX}

## Out of Scope
- {explicit exclusions}

## Open Questions
- {question} — {owner}

## KB References
- `01-CBOL-Domain-Knowledge/...`
- `02-Chat-Domain-Knowledge/...`
```

### Step 4: Identify New Domain Terms

Review requirements for terms not in KB glossary:
```bash
# Search KB glossary for each domain term
grep -r "{term}" 01-CBOL-Domain-Knowledge/glossary/ --include="*.md" -l
```

If new terms found, draft glossary entries in `01-CBOL-Domain-Knowledge/glossary/{term}.md`.

### Step 5: Present for Human Approval

1. Display requirements doc summary to user
2. Ask user to review full doc
3. Wait for explicit approval (LGTM / Approved / looks good)
4. If user requests changes, incorporate and re-present (max 2 rejections, then escalate)

### Step 6: Record Approval

Write `docs/operations/{JIRA_KEY}/02-requirements/human-approval.md`:
```markdown
# Human Approval — Requirements

**Ticket**: {JIRA_KEY}
**Approver**: {name}
**Date**: {ISO timestamp}
**Decision**: Approved / Rejected
**Comments**: {optional}
```

### Step 7: Write KB Updates (if any)

If new domain terms identified and approved:
```bash
git add 01-CBOL-Domain-Knowledge/glossary/
git commit -m "docs(kb): add new domain terms from {JIRA_KEY} requirements"
```

### Step 8: Generate Verify Report + Update State

Write verify-report.md, update pipeline-state.json.

## Verify Gate (Human Approval)

| Criteria | Method | Evidence |
|----------|--------|----------|
| Requirements doc generated | File exists | `ls` output |
| All FRs from ticket captured | FR count match | Comparison in verify-report |
| All ACs from ticket captured | AC count match | Comparison in verify-report |
| KB docs injected (>= 3) | KB injection log | operation-log.md |
| Requirements doc follows template | Template pattern match | verify-report.md |
| Human explicitly approves | Approval record | human-approval.md |
| New domain terms added to KB (if any) | KB commit | Git log |
| Verify report generated | File exists | `ls` output |
| Pipeline state updated | State file | `cat pipeline-state.json` |

**PASS** → Human explicitly approves ✅ → Proceed to Stage 3 (SDD)
**FAIL** → Human rejects → incorporate feedback, regenerate (max 2 rejections, then escalate)

## KB Injection

**Read**:
- `01-CBOL-Domain-Knowledge/README.md`
- `01-CBOL-Domain-Knowledge/` (by ticket labels)
- `02-Chat-Domain-Knowledge/` (keyword search)
- `03-Design-Guidelines/06-design-process/sdd-template.md`

**Write**:
- New domain terms → `01-CBOL-Domain-Knowledge/glossary/`

## Socratic Questioning (Inspired by genkovich/sdd)

Before generating, ask user clarifying questions if ticket is ambiguous:
- "What is the primary user role for this feature?"
- "Are there any performance constraints we should know about?"
- "What does success look like for this feature?"
- "Are there any integrations or dependencies not mentioned in the ticket?"

Only ask if ticket is clearly incomplete. If ticket is well-specified, proceed directly.

## Error Handling

| Error | Resolution |
|-------|-----------|
| Ticket JSON not found | Run ticket-intake skill first |
| KB directory not found | Run `git pull`, verify KB exists |
| Human rejects 2 times | Escalate to tech lead, create escalation ticket |
| New term conflicts with existing KB | Ask user to resolve conflict |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/02-requirements/requirements.md` — Requirements doc
- `docs/operations/{JIRA_KEY}/02-requirements/human-approval.md` — Approval record
- `docs/operations/{JIRA_KEY}/02-requirements/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/02-requirements/operation-log.md` — Operation log
- Potential KB updates: `01-CBOL-Domain-Knowledge/glossary/*.md`

---

*Requirements Skill v1.0.0 — 2026-08-21*
