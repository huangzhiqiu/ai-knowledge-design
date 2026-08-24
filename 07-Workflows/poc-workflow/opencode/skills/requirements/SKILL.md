---
name: requirements
description: Generate a requirements document from a validated Jira ticket + knowledge base. Uses Socratic questioning for ambiguous tickets, injects domain knowledge, extracts functional/non-functional requirements and acceptance criteria. Requires explicit human approval before proceeding. Use after ticket-intake, or when you need to generate requirements from a ticket.
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash(grep:*)
  - Bash(cat:*)
---

# Requirements Skill

Generate requirements document. Socratic questioning + KB injection + human approval.

## CRITICAL RULES

1. **HUMAN APPROVAL REQUIRED**: Pipeline CANNOT proceed to Stage 3 (SDD) without explicit human approval of requirements doc. Never auto-approve.
2. **SOCRATIC FIRST**: If ticket is ambiguous or incomplete, ask clarifying questions BEFORE generating requirements. Do NOT guess missing requirements.
3. **TRACE TO TICKET**: Every FR and AC MUST trace back to the original ticket. Do NOT add requirements not in ticket without explicit user confirmation.
4. **KB INJECTION MANDATORY**: Read at least 3 relevant KB docs before generating. Domain knowledge must inform requirements.
5. **NO SCOPE CREEP**: Do not expand beyond ticket scope. If ticket says "out of scope", respect it.
6. **NEW TERMS → KB**: If requirements introduce new domain terms not in KB, draft glossary entries and ask user to approve KB update.

## References

- [genkovich/sdd](https://github.com/genkovich/sdd) — Socratic specify skill, depth dial (easy/medium/hard)
- [codemachine0121/sdd-skill](https://github.com/codemachine0121/sdd-skill) — Domain-driven design, ubiquitous language
- [POC Stage 2 Doc](../../stages/02-requirements.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 2 criteria
- [KB Integration](../../knowledge-integration.md) — KB read/write protocol

## Prerequisites

1. Stage 1 (ticket-intake) completed — normalized ticket exists
2. `docs/operations/{JIRA_KEY}/01-ticket-intake/ticket.json` exists
3. Knowledge base directories exist (`01-` through `06-`)
4. Operation directory exists: `docs/operations/{JIRA_KEY}/02-requirements/`

## Execution Steps

### Step 1: Read Ticket

```bash
cat "docs/operations/{JIRA_KEY}/01-ticket-intake/ticket.json"
```

Extract: summary, description, FRs, ACs, labels, components, type, priority, dependencies, out_of_scope.

### Step 2: Assess Ticket Completeness

Check for ambiguities:
- [ ] FRs clearly defined with acceptance criteria?
- [ ] User roles identified?
- [ ] Non-functional requirements mentioned?
- [ ] Dependencies clear?
- [ ] Out of scope explicitly defined?
- [ ] Domain terms defined?

**If ambiguous** → Run Socratic questioning (Step 3).
**If complete** → Skip to Step 4.

### Step 3: Socratic Questioning (If Ambiguous)

Ask clarifying questions, ONE at a time. Use depth dial:

**Easy depth** (ticket mostly complete, 1-2 questions):
- "You mentioned {feature} — should this apply to all user roles or specific ones?"
- "Is there a performance expectation for {feature} (e.g., response time, throughput)?"

**Medium depth** (ticket has gaps, 3-5 questions):
- "What is the primary user role for this feature?"
- "Are there any integrations or dependencies not mentioned?"
- "What does success look like — how will we know this feature is working?"
- "Are there any edge cases or error scenarios we should handle?"
- "Is there a specific SLA or performance requirement?"

**Hard depth** (ticket very vague, 5+ questions):
- Full requirements workshop style questioning
- Explore all dimensions: users, flows, errors, performance, security, scalability, integrations

**Interactive checkpoint after each question**:
Wait for user answer before asking next. Do NOT batch questions.

### Step 4: Inject Knowledge Base

Per [`knowledge-integration.md`](../../knowledge-integration.md):

**Mandatory reads**:
- `01-CBOL-Domain-Knowledge/README.md` — Domain context
- `03-Design-Guidelines/06-design-process/sdd-template.md` — Requirements format

**Label-based reads** (search by ticket labels):
```bash
# Map labels to KB directories
# message-reception → 01-CBOL-Domain-Knowledge/message-reception/
# message-forwarding → 01-CBOL-Domain-Knowledge/message-forwarding/
# websocket → 02-Chat-Domain-Knowledge/websocket/
# state-machine → 01-CBOL-Domain-Knowledge/state-machine/
# ... etc
```

**Keyword search**:
```bash
grep -rl "{keyword from ticket}" 01-CBOL-Domain-Knowledge/ 02-Chat-Domain-Knowledge/ --include="*.md" | head -10
```

Read at least 3 relevant KB docs. Log all reads in operation log.

### Step 5: Generate Requirements Document

Write `docs/operations/{JIRA_KEY}/02-requirements/requirements.md`:

```markdown
# Requirements — {JIRA_KEY}: {Summary}

**Ticket**: [{KEY}]({JIRA_URL})
**Type**: {Story/Task/Bug}
**Priority**: {High/Medium/Low}
**Generated**: {date}
**Status**: DRAFT / APPROVED

## Executive Summary
{2-3 paragraph summary from ticket description + KB context}

## User Roles
- {Role 1}: {description}
- {Role 2}: {description}

## Functional Requirements

### FR-001: {Title}
**Description**: {detailed description}
**Source**: Ticket FR-001 / derived from ticket / user clarification
**Priority**: Must/Should/Could
**User Role**: {role}

### FR-002: {Title}
...

## Non-Functional Requirements

### NFR-001: {Performance/Security/Scalability}
**Description**: {requirement}
**Metric**: {quantifiable target}
**Source**: Ticket / KB best practice / user clarification

## User Stories
- As a {role}, I want to {action}, so that {benefit}

## Acceptance Criteria

### AC-001: {Scenario}
- **Given** {precondition}
- **When** {action}
- **Then** {expected result}
- **Traceability**: FR-001

## Dependencies
- {CBOL-XXX}: {description}

## Out of Scope
- {explicit exclusions from ticket}

## Open Questions
- {question} — {owner} — {status: open/resolved}

## Domain Terms
- {Term}: {definition} — {new / existing in KB}

## KB References
- `01-CBOL-Domain-Knowledge/...`
- `02-Chat-Domain-Knowledge/...`
```

### Step 6: Identify New Domain Terms

```bash
# Check if terms exist in KB glossary
for term in "{term1}" "{term2}"; do
  if grep -rql "$term" 01-CBOL-Domain-Knowledge/glossary/ --include="*.md" 2>/dev/null; then
    echo "EXISTS: $term"
  else
    echo "NEW: $term"
  fi
done
```

For NEW terms, draft glossary entry in `01-CBOL-Domain-Knowledge/glossary/{term}.md`.

### Step 7: Present for Human Review

1. Display requirements summary:
   - Total FRs: {N}
   - Total ACs: {M}
   - NFRs: {N}
   - New domain terms: {N}
   - Open questions: {N}

2. **Interactive checkpoint**:
   > Requirements draft for `{JIRA_KEY}` ready. {N} FRs, {M} ACs identified.
   > Options: [Approve requirements] [Request changes] [View full document] [View new domain terms] [Stop]

3. If user requests changes → incorporate, re-present (max 2 rejection cycles, then escalate)

### Step 8: Record Approval

Write `human-approval.md`:
```markdown
# Human Approval — Requirements

**Ticket**: {JIRA_KEY}
**Approver**: {name}
**Date**: {ISO timestamp}
**Decision**: Approved / Rejected
**Comments**: {optional}
**Rejection cycle**: {1/2}
```

Update requirements doc status to `APPROVED`.

### Step 9: Write KB Updates (If Approved + New Terms)

If new domain terms identified AND user approves:
```bash
git add 01-CBOL-Domain-Knowledge/glossary/
git commit -m "docs(kb): add new domain terms from {JIRA_KEY} requirements"
```

### Step 10: Verify Report + State Update

## Verify Gate (Human Approval)

| Criteria | Method | Evidence |
|----------|--------|----------|
| Requirements doc generated | File exists | `ls requirements.md` |
| All FRs from ticket captured | FR count match | verify-report.md |
| All ACs from ticket captured | AC count match | verify-report.md |
| ≥3 KB docs injected | KB injection log | operation-log.md |
| Requirements doc follows template | Template pattern match | verify-report.md |
| User roles identified | Section exists | requirements.md |
| NFRs included (at least 1) | Section exists | requirements.md |
| Dependencies documented | Section exists | requirements.md |
| Out of scope documented | Section exists | requirements.md |
| Socratic questions asked (if ambiguous) | Q&A log | operation-log.md |
| New domain terms identified | Glossary drafts | verify-report.md |
| Human explicitly approves | Approval record | human-approval.md |
| KB updates committed (if any) | Git log | Git log |

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

## Error Handling

| Error | Resolution |
|-------|-----------|
| Ticket JSON not found | Run ticket-intake skill first |
| KB directory not found | Run `git pull`, verify KB exists |
| Ticket very ambiguous | Run hard-depth Socratic questioning, ask user to provide more info |
| Human rejects 2 times | Escalate to tech lead, create escalation ticket |
| New term conflicts with existing KB | Ask user to resolve conflict, document decision |
| User adds requirements not in ticket | Confirm with user, note as "user clarification" in source field |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/02-requirements/requirements.md` — Requirements doc
- `docs/operations/{JIRA_KEY}/02-requirements/human-approval.md` — Approval record
- `docs/operations/{JIRA_KEY}/02-requirements/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/02-requirements/operation-log.md` — Operation log
- Potential KB updates: `01-CBOL-Domain-Knowledge/glossary/*.md`

---

*Requirements Skill v2.0.0 — 2026-08-24*
*Optimized with: Socratic questioning (depth dial), interactive checkpoints, CRITICAL rules, precise allowed-tools, human approval enforcement*
