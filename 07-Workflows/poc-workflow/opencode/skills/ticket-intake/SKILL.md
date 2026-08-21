---
name: ticket-intake
description: Fetch and validate a Jira ticket for the POC workflow. Retrieves ticket details via Jira REST API, validates against ticket specification, normalizes into structured JSON. Use when starting a new pipeline from a Jira ticket, or when you need to fetch and validate ticket content.
version: 1.0.0
author: CBOL Self-Development
tags: [jira, ticket, intake, validation, poc]
triggers:
  - "fetch jira ticket"
  - "ticket intake"
  - "validate jira ticket"
  - "get ticket CBOL"
arguments:
  - name: jira_key
    description: Jira ticket key (e.g., CBOL-123)
    required: true
---

# Ticket Intake Skill

Fetch and validate a Jira ticket for the POC workflow.

## References

- [Jira Skill for Claude Code](https://github.com/illarion/claude-jira-skill) — Jira CLI integration pattern
- [Jira MCP Server](https://github.com/rui-branco/jira-mcp) — MCP tool pattern (jira_get_ticket)
- [Jira Integration Skill](https://skillselion.com/skills/affaan-m/ecc/jira-integration) — MCP + REST fallback pattern
- [POC Jira Ticket Spec](../../jira-ticket-spec.md) — Ticket validation specification
- [POC Stage 1 Doc](../../stages/01-ticket-intake.md) — Stage documentation

## Prerequisites

1. Jira API credentials configured in `.ai-workflow/config.yaml`:
   - `jira.base_url` — Jira instance URL
   - `jira.email` — Atlassian account email
   - `jira.api_token` — API token
2. Network access to Jira instance
3. Operation directory exists: `docs/operations/{JIRA_KEY}/01-ticket-intake/`

## Execution Steps

### Step 1: Read Configuration

```bash
# Read Jira config from .ai-workflow/config.yaml
cat .ai-workflow/config.yaml | grep -A5 "jira:"
```

If config not found, ask user for Jira credentials and create config.

### Step 2: Fetch Jira Ticket

```bash
# Fetch ticket via Jira REST API
JIRA_KEY="{JIRA_KEY}"
JIRA_BASE_URL="{from config}"
JIRA_EMAIL="{from config}"
JIRA_API_TOKEN="{from config}"

curl -s -u "$JIRA_EMAIL:$JIRA_API_TOKEN" \
  -H "Content-Type: application/json" \
  "$JIRA_BASE_URL/rest/api/3/issue/$JIRA_KEY" \
  -o "docs/operations/$JIRA_KEY/01-ticket-intake/ticket-raw.json"

echo "Exit code: $?"
```

**Verify**: HTTP response 200, ticket-raw.json exists and is valid JSON.

### Step 3: Parse and Normalize Ticket

Extract fields into normalized structure:

```json
{
  "key": "CBOL-123",
  "type": "Story",
  "priority": "High",
  "summary": "...",
  "description": "...",
  "labels": ["message-forwarding", "websocket"],
  "components": ["message-service"],
  "assignee": "john.doe",
  "functional_requirements": [{"id": "FR-001", "description": "..."}],
  "acceptance_criteria": [{"id": "AC-001", "scenario": "...", "given": "...", "when": "...", "then": "..."}]
}
```

Write to `docs/operations/{JIRA_KEY}/01-ticket-intake/ticket.json`.

### Step 4: Validate Against Ticket Spec

Per [`jira-ticket-spec.md`](../../jira-ticket-spec.md):

**Mandatory field checks**:
- [ ] `summary` present and non-empty
- [ ] `description` present and non-empty
- [ ] `issuetype` valid (Story/Task/Bug/Spike/Chore)
- [ ] `priority` valid (Highest/High/Medium/Low/Lowest)
- [ ] At least one domain label (from spec Section 4.1)
- [ ] `assignee` present
- [ ] `components` present

**Type-specific checks**:
- For Story/Task: at least 1 FR + 1 AC
- For Bug: Steps to Reproduce + Expected + Actual behavior

### Step 5: Generate Verify Report

Write `docs/operations/{JIRA_KEY}/01-ticket-intake/verify-report.md`:

```markdown
# Verify Report — Ticket Intake

**Ticket**: {JIRA_KEY}
**Date**: {ISO timestamp}
**Result**: PASS / FAIL

## Mandatory Fields
| Field | Status | Notes |
|-------|--------|-------|
| summary | ✅/❌ | ... |
...

## Type-Specific Checks
...

## Validation Errors
- {error 1}
- {error 2}

## Warnings
- {warning 1}
```

### Step 6: Update Pipeline State

Update `docs/operations/{JIRA_KEY}/pipeline-state.json`:
```json
{
  "current_stage": 2,
  "stages": {
    "1_ticket_intake": {
      "status": "completed",
      "verify_passed": true/false,
      "completed_at": "{ISO timestamp}"
    }
  }
}
```

## Verify Gate

| Criteria | Method | Evidence |
|----------|--------|----------|
| Ticket fetched successfully | HTTP 200 + valid JSON | `curl` output + exit code |
| All mandatory fields present | Field validation | verify-report.md |
| Valid issue type | Type in allowed list | verify-report.md |
| At least one domain label | Label check | verify-report.md |
| Type-specific checks pass | FR/AC or bug fields | verify-report.md |
| Normalized ticket JSON saved | File exists | `ls` output |
| Verify report generated | File exists | `ls` output |
| Pipeline state updated | State file updated | `cat pipeline-state.json` |

**PASS** → All checks ✅ → Proceed to Stage 2 (requirements)
**FAIL** → Report errors, retry (max 3), then escalate

## KB Injection

**Read**:
- `07-Workflows/poc-workflow/jira-ticket-spec.md` — Validation spec
- `06-Skills/02-code-analysis/` — Ticket parsing patterns

**Write**: None (this stage doesn't write to KB)

## Error Handling

| Error | Resolution |
|-------|-----------|
| Jira API auth failed | Check credentials in config, re-run |
| Ticket not found (404) | Verify ticket key, ask user for correct key |
| Network timeout | Retry after 5s, max 3 retries |
| Invalid JSON response | Check Jira API version, re-fetch |
| Missing mandatory fields | Report to user, ask to update ticket in Jira |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/01-ticket-intake/ticket-raw.json` — Raw Jira response
- `docs/operations/{JIRA_KEY}/01-ticket-intake/ticket.json` — Normalized ticket
- `docs/operations/{JIRA_KEY}/01-ticket-intake/verify-report.md` — Validation report
- `docs/operations/{JIRA_KEY}/01-ticket-intake/operation-log.md` — Step-by-step log

---

*Ticket Intake Skill v1.0.0 — 2026-08-21*
