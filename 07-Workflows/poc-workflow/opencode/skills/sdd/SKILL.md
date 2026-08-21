---
name: sdd
description: Generate a Software Design Document (SDD) from approved requirements + knowledge base + codebase analysis. Includes architecture diagrams, component design, data model, API design, state machine, implementation plan, ADRs. Requires human review approval. Use after requirements skill, or when you need to create a design document.
version: 1.0.0
author: CBOL Self-Development
tags: [sdd, design, architecture, data-model, api, human-review, poc]
triggers:
  - "generate SDD"
  - "create design document"
  - "software design document"
  - "design from requirements"
arguments:
  - name: jira_key
    description: Jira ticket key (e.g., CBOL-123)
    required: true
---

# SDD Skill

Generate Software Design Document from requirements + KB + codebase.

## References

- [genkovich/sdd](https://github.com/genkovich/sdd) — design skill with Arc42 + C4 + ADRs
- [codemachine0121/sdd-skill](https://github.com/codemachine0121/sdd-skill) — domain-driven design skill
- [gotalab/cc-sdd](https://github.com/gotalab/cc-sdd/) — design.md with Mermaid + File Structure Plan
- [Legrandk/claude](https://github.com/Legrandk/claude) — sdd-start, sdd-research commands
- [POC Stage 3 Doc](../../stages/03-sdd.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 3 criteria

## Prerequisites

1. Stage 2 (requirements) completed and human-approved
2. Requirements doc exists: `docs/operations/{JIRA_KEY}/02-requirements/requirements.md`
3. Knowledge base directories exist
4. Codebase accessible (for architecture analysis)
5. Operation directory exists: `docs/operations/{JIRA_KEY}/03-sdd/`

## Execution Steps

### Step 1: Read Requirements

```bash
cat "docs/operations/{JIRA_KEY}/02-requirements/requirements.md"
```

Extract: FRs, NFRs, ACs, dependencies, out of scope.

### Step 2: Analyze Codebase

Use architecture analysis to understand existing patterns:
```bash
# Analyze project structure
find src/main/java -name "*.java" | head -50

# Find existing patterns
grep -r "@RestController\|@Service\|@Repository" src/main/java --include="*.java" -l

# Check existing WebSocket config
find src/main/java -name "*WebSocket*" -o -name "*Netty*"

# Check existing state machine
find src/main/java -name "*State*" -o -name "*StateMachine*"
```

If `06-Skills/02-code-analysis/architecture-analyzer/` exists, use it.

### Step 3: Inject Knowledge Base

**Mandatory reads**:
- `03-Design-Guidelines/` (ALL subdirectories)
- `01-CBOL-Domain-Knowledge/state-machine/` (if ticket involves conversation states)
- `02-Chat-Domain-Knowledge/websocket/` (if ticket involves WebSocket)
- `02-Chat-Domain-Knowledge/` (search by keyword)

**Label-based reads**: same as requirements skill.

### Step 4: Generate SDD

Write `docs/operations/{JIRA_KEY}/03-sdd/sdd.md` with sections:

1. **Context and Goals** — what we're building and why
2. **Architecture Overview** — Mermaid flowchart (components, interactions)
3. **Component Design** — each component's responsibility, interfaces, dependencies
4. **Data Model** — entities, ER diagram (Mermaid), schema, migrations
5. **API Design** — REST endpoints, WebSocket events, request/response contracts
6. **State Machine Design** — if applicable (Mermaid stateDiagram)
7. **WebSocket Protocol Design** — if applicable (connection, events, heartbeat, reconnection)
8. **Security Considerations** — auth, input validation, data protection
9. **Performance Considerations** — expected load, caching, scaling
10. **Implementation Plan** — task breakdown table (ID, description, estimate, depends on)
11. **Testing Strategy** — unit, integration, E2E, coverage target
12. **ADRs** — Architecture Decision Records (context, decision, consequences)
13. **KB References** — docs read during design

**Mermaid diagrams required**:
- Architecture flowchart (flowchart TB)
- ER diagram (erDiagram) — if data model changes
- State diagram (stateDiagram-v2) — if state machine involved
- Sequence diagram (sequenceDiagram) — for key flows

### Step 5: Identify New ADRs

If design introduces new patterns not in existing ADRs:
```bash
ls 03-Design-Guidelines/06-design-process/adr/
```

Draft new ADRs in `03-Design-Guidelines/06-design-process/adr/NNNN-title.md`.

### Step 6: Present for Human Review

1. Display SDD summary + key diagrams
2. Ask user to review full doc
3. Wait for explicit approval (LGTM / Approved)
4. If changes requested, incorporate and re-present (max 2 rejections, then escalate)

### Step 7: Record Review + Write KB Updates

Write `human-review.md`, commit ADRs to KB (if any).

### Step 8: Verify Report + State Update

## Verify Gate (Human Review)

| Criteria | Method | Evidence |
|----------|--------|----------|
| SDD generated | File exists | `ls` output |
| Architecture diagram included | Mermaid flowchart present | SDD content check |
| Component design section present | Section exists | verify-report.md |
| Data model defined (entities + ER) | Schema + Mermaid erDiagram | verify-report.md |
| API design defined (endpoints/events) | REST + WebSocket contracts | verify-report.md |
| State machine included (if applicable) | Mermaid stateDiagram | verify-report.md |
| Implementation plan included | Task breakdown table | verify-report.md |
| Testing strategy defined | Test approach + coverage target | verify-report.md |
| ADRs section present | ADRs documented | verify-report.md |
| All FRs addressed (traceability) | FR-to-component mapping | verify-report.md |
| KB docs injected (>= 5) | KB injection log | operation-log.md |
| Human explicitly approves | Review record | human-review.md |
| New ADRs added to KB (if any) | KB commit | Git log |

**PASS** → Human explicitly approves ✅ → Proceed to Stage 4 (test-cases)
**FAIL** → Human requests changes → incorporate, regenerate (max 2 rejections, then escalate)

## KB Injection

**Read**:
- `03-Design-Guidelines/` (ALL)
- `01-CBOL-Domain-Knowledge/` (by labels)
- `02-Chat-Domain-Knowledge/` (by keyword)
- `06-Skills/02-code-analysis/architecture-analyzer/`

**Write**:
- New ADRs → `03-Design-Guidelines/06-design-process/adr/`

## Implementation Plan Template

| Task ID | Description | Estimate | Depends On | FRs Addressed |
|---------|-------------|----------|------------|---------------|
| T001 | Add data model + migration | 0.5d | — | FR-001, FR-002 |
| T002 | Implement service layer | 1d | T001 | FR-003, FR-004 |
| T003 | Add API endpoints | 0.5d | T002 | FR-005 |
| T004 | WebSocket event handling | 1d | T002 | FR-006 |
| T005 | Integration tests | 0.5d | T003, T004 | All |

## Error Handling

| Error | Resolution |
|-------|-----------|
| Requirements doc not found | Run requirements skill first |
| Codebase analysis fails | Proceed with KB-only design, note assumption |
| Human rejects 2 times | Escalate to architect, create escalation ticket |
| Conflicting ADRs in KB | Ask user to resolve, document decision in new ADR |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/03-sdd/sdd.md` — SDD document
- `docs/operations/{JIRA_KEY}/03-sdd/human-review.md` — Review record
- `docs/operations/{JIRA_KEY}/03-sdd/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/03-sdd/operation-log.md` — Operation log
- Potential KB updates: `03-Design-Guidelines/06-design-process/adr/NNNN-*.md`

---

*SDD Skill v1.0.0 — 2026-08-21*
