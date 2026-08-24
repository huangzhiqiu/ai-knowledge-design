---
name: sdd
description: Generate a Software Design Document (SDD) from approved requirements + knowledge base + codebase analysis. Covers architecture, data models, APIs, state machines, sequence diagrams, error handling. Requires explicit human review before proceeding to TDD. Use after requirements approval, or when you need to design a solution.
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash(grep:*)
  - Bash(find:*)
  - Bash(cat:*)
  - Bash(jq:*)
---

# SDD Skill

Generate Software Design Document. Research + design + human review.

## CRITICAL RULES

1. **HUMAN REVIEW REQUIRED**: Pipeline CANNOT proceed to Stage 4 (test cases) without explicit human review approval of SDD. Never auto-approve.
2. **RESEARCH BEFORE DESIGN**: Read codebase, KB, and similar implementations BEFORE writing design. Do NOT design in a vacuum.
3. **FOLLOW KB GUIDELINES**: Design MUST follow `03-Design-Guidelines/` and `04-Coding-Guidelines/`. Deviations require explicit justification.
4. **TRACE TO REQUIREMENTS**: Every design decision MUST trace to at least one FR/NFR. Do NOT add unrequested features.
5. **MANDATORY DIAGRAMS**: SDD MUST include at least: class diagram, sequence diagram, state machine (if applicable), API spec.
6. **NO PREMATURE OPTIMIZATION**: Design for clarity and correctness first. Performance optimizations only if NFR requires.
7. **NEW PATTERNS → KB**: If design introduces new patterns not in KB, draft KB entries and ask user to approve.

## References

- [genkovich/sdd](https://github.com/genkovich/sdd) — Research → Specify → Design → Implement engine, atomic design/data-model/sequences skills
- [SpillwaveSolutions/sdd-skill](https://github.com/SpillwaveSolutions/sdd-skill) — Spec-Driven Development v2.1.0, GitHub Spec-Kit, greenfield/brownfield, 10-point summaries, feature status tracking
- [kborovik/opencode-skills](https://github.com/kborovik/opencode-skills) — spec mutator: NEW/DISTILL/BACKPROP/AMEND/FOLD-IN modes, audit gates, write-time prune, monotonic IDs
- [gotalab/cc-sdd](https://github.com/gotalab/cc-sdd) — Contextual SDD with ADRs
- [codemachine0121/sdd-skill](https://github.com/codemachine0121/sdd-skill) — DDD, ubiquitous language, bounded contexts
- [POC Stage 3 Doc](../../stages/03-sdd.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 3 criteria
- [KB Integration](../../knowledge-integration.md) — KB read/write protocol

## External Skill Synergy

| External Skill | When to Use | How to Integrate |
|---------------|-------------|-----------------|
| `genkovich-sdd/design` + `data-model` + `sequences` | Atomic design tasks | Delegate specific design sub-tasks (data model, sequence diagrams) to atomic skills |
| `sdd-spec-driven` (SpillwaveSolutions) | Greenfield/brownfield SDD methodology | Use for SDD process guidance; inject CBOL KB via this skill |
| `spec-mutator` (kborovik) | Maintaining SPEC.md across iterations | Use DISTILL for brownfield, NEW for greenfield, BACKPROP for bug-driven design updates |
| `genkovich-sdd/decide-adr` | Architecture decision records | Use for ADR generation when design introduces new patterns |
| `excalibase-api-design` | API design sub-task | Delegate API design to external skill; integrate into SDD |

**Delegation pattern**: For complex architecture, use `genkovich-sdd/design` for structure, `data-model` for entities, `sequences` for flows. For SPEC.md maintenance, use `spec-mutator` with appropriate mode. This skill orchestrates and injects CBOL domain knowledge.

## Prerequisites

1. Stage 2 (requirements) completed and APPROVED
2. `docs/operations/{JIRA_KEY}/02-requirements/requirements.md` exists with status APPROVED
3. Codebase exists (or this is greenfield)
4. Knowledge base directories exist
5. Operation directory exists: `docs/operations/{JIRA_KEY}/03-sdd/`

## Execution Steps

### Step 1: Read Approved Requirements

```bash
cat "docs/operations/{JIRA_KEY}/02-requirements/requirements.md"
```

Extract all FRs, NFRs, ACs, dependencies, user roles, domain terms.

### Step 2: Research Phase

#### 2a: Knowledge Base Injection

Per [`knowledge-integration.md`](../../knowledge-integration.md):

**Mandatory reads**:
- `03-Design-Guidelines/` — All design guidelines
- `04-Coding-Guidelines/` — All coding guidelines
- `01-CBOL-Domain-Knowledge/` — Domain-specific patterns
- `02-Chat-Domain-Knowledge/` — IM patterns (websocket, message storage, etc.)

**Label-based reads**: Map ticket labels to KB directories.

**Keyword search**:
```bash
grep -rl "{keyword}" 01-CBOL-Domain-Knowledge/ 02-Chat-Domain-Knowledge/ 03-Design-Guidelines/ --include="*.md" | head -15
```

#### 2b: Codebase Analysis

```bash
# Find related code by keywords
grep -rl "{keyword}" src/ --include="*.java" | head -20

# Find related tests
grep -rl "{keyword}" src/test/ --include="*.java" | head -20

# Find existing APIs
grep -r "@RestController\|@Controller\|@MessageMapping" src/main/java/ --include="*.java" | head -20

# Find existing data models
find src/main/java -name "*Entity.java" -o -name "*Model.java" -o -name "*DTO.java" | head -20

# Find existing state machines
grep -rl "StateMachine\|@State\|state" src/main/java/ --include="*.java" | head -10
```

**Analyze**:
- Existing patterns and conventions
- Related modules and their structure
- Reusable components
- Integration points
- Existing tests patterns

#### 2c: Similar Implementation Reference

Search KB for similar designs:
```bash
grep -rl "{similar feature}" 05-References/ 02-Chat-Domain-Knowledge/ --include="*.md" | head -10
```

Read at least 1 similar implementation reference.

**Interactive checkpoint**:
> Research complete. Read {N} KB docs, found {M} related code files, {K} similar implementations. Continue to design?
> Options: [Continue to design] [Show research summary] [Read more code] [Stop]

### Step 3: Design Phase

Generate SDD at `docs/operations/{JIRA_KEY}/03-sdd/sdd.md`:

```markdown
# Software Design Document — {JIRA_KEY}: {Summary}

**Ticket**: [{KEY}]({JIRA_URL})
**Status**: DRAFT / REVIEWED
**Generated**: {date}
**Author**: AI (human review required)

## 1. Overview
### 1.1 Purpose
{What this design achieves}

### 1.2 Scope
{In scope / Out of scope}

### 1.3 Requirements Traceability
| FR/NFR | Design Section |
|--------|---------------|
| FR-001 | Section 3.1 |
| FR-002 | Section 4.2 |
| NFR-001 | Section 6 |

## 2. Architecture
### 2.1 High-Level Architecture
{Mermaid flowchart}

### 2.2 Component Diagram
{Mermaid component diagram}

### 2.3 Module Responsibilities
| Module | Responsibility |
|--------|---------------|

## 3. Data Models
### 3.1 Entity/Model Classes
{Mermaid class diagram}

### 3.2 Database Schema
{Table definitions / MongoDB collections}
{Indexes}

### 3.3 DTOs
{Request/Response objects}

## 4. API Design
### 4.1 REST APIs
| Method | Path | Description | Auth |
|--------|------|-------------|------|

### 4.2 WebSocket APIs
{Message types, payloads}

### 4.3 Internal APIs
{Service interfaces}

## 5. State Machine (if applicable)
### 5.1 States
{State list}

### 5.2 Transitions
{Mermaid state diagram}
{Transition table}

### 5.3 Guards and Actions
{Guard conditions, action callbacks}

## 6. Sequence Diagrams
### 6.1 Main Flow
{Mermaid sequence diagram}

### 6.2 Error Flows
{Mermaid sequence diagrams for error scenarios}

## 7. Error Handling
### 7.1 Error Codes
{Error code table}

### 7.2 Exception Hierarchy
{Exception classes}

### 7.3 Retry and Fallback
{Retry policies, circuit breakers}

## 8. Non-Functional Design
### 8.1 Performance
{Design decisions for NFR-001}

### 8.2 Security
{Auth, authz, input validation}

### 8.3 Scalability
{Horizontal scaling, sharding}

### 8.4 Observability
{Logging, metrics, tracing}

## 9. Integration Points
### 9.1 External Dependencies
{List and description}

### 9.2 Internal Dependencies
{Modules this depends on}

## 10. Testing Strategy
### 10.1 Unit Tests
{What to test, test patterns}

### 10.2 Integration Tests
{What to test}

### 10.3 Edge Cases
{List of edge cases to test}

## 11. Migration Plan (if applicable)
{Data migration, backward compatibility}

## 12. Design Decisions (ADRs)
### ADR-001: {Decision title}
- **Status**: Proposed/Accepted
- **Context**: {why}
- **Decision**: {what}
- **Consequences**: {pros/cons}

## 13. KB References
{List of KB docs read}

## 14. Codebase References
{List of existing code files analyzed}
```

### Step 4: Self-Review

Before presenting to human, run self-review checklist:
- [ ] All FRs/NFRs traced to design sections
- [ ] At least 3 KB docs injected
- [ ] Codebase analyzed (if not greenfield)
- [ ] Class diagram included
- [ ] Sequence diagram included
- [ ] State machine included (if applicable)
- [ ] API spec included
- [ ] Error handling section complete
- [ ] Testing strategy defined
- [ ] Follows `03-Design-Guidelines/`
- [ ] Follows `04-Coding-Guidelines/`
- [ ] No unrequested features
- [ ] New patterns identified for KB update

### Step 5: Present for Human Review

1. Display SDD summary:
   - Architecture: {high-level}
   - New modules: {N}
   - New APIs: {N}
   - New data models: {N}
   - State changes: {N}
   - ADRs: {N}
   - New patterns for KB: {N}

2. **Interactive checkpoint**:
   > SDD draft for `{JIRA_KEY}` ready. {N} new modules, {M} APIs, {K} data models.
   > Options: [Approve SDD] [Request changes] [View full document] [View diagrams] [View ADRs] [Stop]

3. If user requests changes → incorporate, re-present (max 2 rejection cycles, then escalate)

### Step 6: Record Review Approval

Write `human-review.md`:
```markdown
# Human Review — SDD

**Ticket**: {JIRA_KEY}
**Reviewer**: {name}
**Date**: {ISO timestamp}
**Decision**: Approved / Rejected
**Comments**: {optional}
**Rejection cycle**: {1/2}
```

Update SDD status to `REVIEWED`.

### Step 7: Write KB Updates (If Approved + New Patterns)

If new patterns identified AND user approves:
```bash
git add 03-Design-Guidelines/ 01-CBOL-Domain-Knowledge/
git commit -m "docs(kb): add new design patterns from {JIRA_KEY} SDD"
```

## Verify Gate (Human Review)

| Criteria | Method | Evidence |
|----------|--------|----------|
| SDD generated | File exists | `ls sdd.md` |
| All FRs/NFRs traced | Traceability table | sdd.md Section 1.3 |
| ≥3 KB docs injected | KB injection log | operation-log.md |
| Codebase analyzed (if exists) | Code references | sdd.md Section 14 |
| Architecture diagram | Mermaid flowchart | sdd.md Section 2 |
| Data model diagram | Mermaid class diagram | sdd.md Section 3 |
| API spec complete | REST + WebSocket tables | sdd.md Section 4 |
| State machine (if applicable) | Mermaid state diagram | sdd.md Section 5 |
| Sequence diagrams | Mermaid sequence diagrams | sdd.md Section 6 |
| Error handling complete | Error codes + exceptions | sdd.md Section 7 |
| Testing strategy defined | Unit + integration + edge | sdd.md Section 10 |
| ADRs documented | Decision records | sdd.md Section 12 |
| Follows design guidelines | Self-review checklist | verify-report.md |
| Follows coding guidelines | Self-review checklist | verify-report.md |
| Human explicitly reviews | Review record | human-review.md |
| KB updates committed (if any) | Git log | Git log |

**PASS** → Human explicitly approves ✅ → Proceed to Stage 4 (test cases / TDD RED)
**FAIL** → Human rejects → incorporate feedback, regenerate (max 2 rejections, then escalate)

## KB Injection

**Read**:
- `03-Design-Guidelines/` — All design guidelines
- `04-Coding-Guidelines/` — All coding guidelines
- `01-CBOL-Domain-Knowledge/` — Domain patterns
- `02-Chat-Domain-Knowledge/` — IM patterns
- `05-References/` — Similar implementations

**Write**:
- New design patterns → `03-Design-Guidelines/`
- New domain patterns → `01-CBOL-Domain-Knowledge/`

## Error Handling

| Error | Resolution |
|-------|-----------|
| Requirements not approved | Run requirements skill and get approval first |
| KB directory not found | Run `git pull`, verify KB exists |
| Codebase not found (greenfield) | Note as greenfield, skip codebase analysis |
| Design conflicts with existing code | Document conflict, propose resolution, ask user |
| Human rejects 2 times | Escalate to architect, create escalation ticket |
| New pattern conflicts with KB | Ask user to resolve, document decision |
| Missing diagram tool | Use Mermaid syntax (text-based, no tool needed) |

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Designing without researching codebase/KB | Mandatory: read codebase + KB + similar implementations before design. |
| Design decisions not traced to requirements | Every design decision must trace to at least one FR/NFR. No unrequested features. |
| Missing mandatory diagrams | SDD must include: class diagram, sequence diagram, state machine (if applicable), API spec. |
| Premature optimization | Design for clarity and correctness first. Performance optimizations only if NFR requires. |
| Ignoring existing architecture patterns | Check KB for established patterns. Deviations require explicit justification. |
| Not documenting ADRs | For significant architecture decisions, generate ADRs. Use genkovich-sdd/decide-adr. |
| Auto-approving SDD | Human review is mandatory. Never proceed to test cases without explicit review. |
| Over-engineering | Minimal architecture. Avoid unnecessary abstractions, layers, or patterns. |
| Not considering error handling | Design must include error handling, edge cases, and failure scenarios. |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/03-sdd/sdd.md` — Software Design Document
- `docs/operations/{JIRA_KEY}/03-sdd/human-review.md` — Review record
- `docs/operations/{JIRA_KEY}/03-sdd/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/03-sdd/operation-log.md` — Operation log
- Potential KB updates: `03-Design-Guidelines/*.md`, `01-CBOL-Domain-Knowledge/*.md`

---

*SDD Skill v2.0.0 — 2026-08-24*
*Optimized with: Research-before-design, interactive checkpoints, CRITICAL rules, precise allowed-tools, ADRs, human review enforcement*
