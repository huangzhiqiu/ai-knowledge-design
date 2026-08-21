# Jira Ticket Specification

> Standardized Jira ticket format for the POC workflow. All tickets MUST conform to this spec before the pipeline can proceed.

## 1. Ticket Types

| Type | Key | Description | Pipeline Path |
|------|-----|-------------|---------------|
| Story | `Story` | New feature or functionality | Full 7-stage pipeline |
| Task | `Task` | Technical task or improvement | Full 7-stage pipeline |
| Bug | `Bug` | Defect or issue fix | Full 7-stage pipeline (test-first for regression) |
| Spike | `Spike` | Research or investigation | Stages 1-3 only (no code) |
| Chore | `Chore` | Maintenance or tooling | Stages 1, 5-7 (skip requirements/SDD) |

## 2. Mandatory Fields

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| `summary` | String | ✅ | One-line summary of the ticket | "Implement message forwarding between users" |
| `description` | Rich Text | ✅ | Detailed description (see template below) | See Section 3 |
| `issuetype` | Select | ✅ | Ticket type (Story/Task/Bug/Spike/Chore) | Story |
| `priority` | Select | ✅ | Priority level | High |
| `labels` | Labels | ✅ | At least one domain label | `message-forwarding`, `websocket` |
| `assignee` | User | ✅ | Person responsible | john.doe |
| `components` | Components | ✅ | Affected component(s) | `message-service` |

## 3. Description Template

### 3.1 Story/Task Template

```markdown
## Overview
{2-3 sentence description of what needs to be built and why}

## User Story
As a {user role}, I want to {action} so that {benefit}.

## Requirements
### Functional Requirements
- FR-001: {requirement description}
- FR-002: {requirement description}

### Non-Functional Requirements
- NFR-001: {performance/security/scalability requirement}

## Acceptance Criteria
### Scenario 1: {scenario name}
- **Given** {precondition}
- **When** {action}
- **Then** {expected result}

### Scenario 2: {scenario name}
...

## Dependencies
- {depends on CBOL-XXX}
- {requires API from service X}

## Out of Scope
- {explicitly excluded items}

## References
- {KB doc link}
- {design doc link}
```

### 3.2 Bug Template

```markdown
## Overview
{2-3 sentence description of the bug}

## Environment
- **Service**: {service name}
- **Version**: {version}
- **Environment**: {dev/staging/prod}

## Steps to Reproduce
1. {step 1}
2. {step 2}
3. {step 3}

## Expected Behavior
{what should happen}

## Actual Behavior
{what actually happens}

## Error Logs
```
{paste relevant error logs}
```

## Impact
{who is affected, severity}

## Suggested Fix (optional)
{if known, describe the fix}
```

## 4. Labels Convention

### 4.1 Domain Labels (required, at least one)

| Label | Domain | KB Directory |
|-------|--------|--------------|
| `message-reception` | Message receiving/ingestion | `01-CBOL-Domain-Knowledge/message-reception/` |
| `message-management` | Message storage/retrieval | `01-CBOL-Domain-Knowledge/message-management/` |
| `message-forwarding` | Message forwarding/routing | `01-CBOL-Domain-Knowledge/message-forwarding/` |
| `websocket` | WebSocket protocol/connection | `02-Chat-Domain-Knowledge/websocket/` |
| `state-machine` | Conversation state management | `01-CBOL-Domain-Knowledge/state-machine/` |
| `ai-processing` | AI message processing | `01-CBOL-Domain-Knowledge/ai-processing/` |
| `agent-transfer` | Human agent transfer | `01-CBOL-Domain-Knowledge/agent-transfer/` |
| `database` | Database design/migration | `03-Design-Guidelines/03-data-design/` |
| `api` | API design/implementation | `03-Design-Guidelines/02-api-design/` |
| `security` | Security/authentication | `03-Design-Guidelines/04-security-design/` |
| `performance` | Performance/scalability | `03-Design-Guidelines/05-reliability/` |

### 4.2 Type Labels (optional)

| Label | Meaning |
|-------|---------|
| `poc` | Proof of concept |
| `refactor` | Code refactoring |
| `tech-debt` | Technical debt |
| `experimental` | Experimental feature |

## 5. Priority Levels

| Priority | Meaning | SLA |
|----------|---------|-----|
| `Highest` | Critical, blocks release | Same day |
| `High` | Important, should be in current sprint | 3 days |
| `Medium` | Normal priority | 1 week |
| `Low` | Nice to have | 2 weeks |
| `Lowest` | Backlog | When available |

## 6. Ticket Validation (Stage 1 Verify)

The pipeline validates tickets against this spec. A ticket is VALID if:

1. ✅ All mandatory fields present and non-empty
2. ✅ `issuetype` is one of: Story, Task, Bug, Spike, Chore
3. ✅ `priority` is one of: Highest, High, Medium, Low, Lowest
4. ✅ At least one domain label present (from Section 4.1)
5. ✅ `description` follows the template for the ticket type
6. ✅ For Story/Task: has at least 1 Functional Requirement and 1 Acceptance Criteria scenario
7. ✅ For Bug: has Steps to Reproduce, Expected Behavior, Actual Behavior

### 6.1 Validation Output

```json
{
  "valid": true,
  "ticket_key": "CBOL-123",
  "type": "Story",
  "priority": "High",
  "domain_labels": ["message-forwarding", "websocket"],
  "functional_requirements_count": 3,
  "acceptance_criteria_count": 4,
  "validation_errors": [],
  "warnings": [
    "No NFR specified — consider adding performance requirements"
  ]
}
```

---

## References

- [Workflow Specification](./workflow-spec.md)
- [Stage 1: Ticket Intake](./stages/01-ticket-intake.md)
- [CBOL Project Overview](../../00-Project-Overview/)

---

*Jira Ticket Spec v1.0.0 — 2026-08-21*
