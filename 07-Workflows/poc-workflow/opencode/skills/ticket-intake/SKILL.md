---
name: ticket-intake
description: Fetch and validate a Jira ticket for the POC workflow. Supports MCP (Atlassian/Jira MCP) and REST API dual-path with automatic fallback. Validates against ticket specification, normalizes into structured JSON. Use when starting a pipeline from a Jira ticket, or when you need to fetch and validate ticket content.
allowed-tools:
  - Bash(curl:*)
  - Bash(jq:*)
  - Read
  - Write
  - Edit
  - Glob
  - Grep
---

# Ticket Intake Skill

Fetch and validate a Jira ticket. MCP-first with REST fallback.

## CRITICAL RULES

1. **MCP FIRST**: Always try Atlassian MCP (`mcp__*Atlassian__*` or `mcp__jira__*`) before REST API. Fall back to REST only if MCP unavailable or fails.
2. **VALIDATE BEFORE PROCEED**: Ticket MUST pass all validation checks before pipeline can proceed to Stage 2. Never skip validation.
3. **NO SPECULATION**: If ticket fields are missing or ambiguous, report errors — do NOT guess or fill in assumed values.
4. **PRESERVE ORIGINAL**: Always save raw Jira response before normalization. Never overwrite raw data.
5. **EVIDENCE REQUIRED**: Every validation result needs command + output + exit code.

## References

- [illarion/claude-jira-skill](https://github.com/illarion/claude-jira-skill) — Multi-instance, ADF handling, transitions
- [rui-branco/jira-mcp](https://github.com/rui-branco/jira-mcp) — MCP server pattern (jira_get_ticket)
- [Lumyk/jira-planner-skill](https://github.com/Lumyk/jira-planner-skill) — MCP + acli dual-path
- [POC Jira Ticket Spec](../../jira-ticket-spec.md) — Validation specification
- [POC Stage 1 Doc](../../stages/01-ticket-intake.md) — Stage documentation

## Prerequisites

1. Jira access configured via ONE of:
   - **MCP** (preferred): Atlassian MCP server or Jira MCP server configured
   - **REST** (fallback): `.ai-workflow/config.yaml` with `jira.base_url`, `jira.email`, `jira.api_token`
2. Operation directory exists: `docs/operations/{JIRA_KEY}/01-ticket-intake/`
3. `jq` installed for JSON parsing (or use Python as fallback)

## Execution Steps

### Step 0: Create Operation Directory

```bash
JIRA_KEY="{JIRA_KEY}"
mkdir -p "docs/operations/$JIRA_KEY/01-ticket-intake"
```

### Step 1: Fetch Ticket (MCP-First)

#### Path A: MCP (Preferred)

Check for available MCP tools:
- `mcp__*Atlassian__getJiraIssue` or similar
- `mcp__jira__get_ticket` or similar
- `mcp__*atlassian__*getAccessibleAtlassianResources`

If MCP available:
1. Call MCP tool with `issueKey={JIRA_KEY}`
2. Save response to `ticket-raw.json`
3. Proceed to Step 2

#### Path B: REST API (Fallback)

If MCP unavailable or fails:

```bash
# Read config
JIRA_BASE_URL=$(grep "base_url" .ai-workflow/config.yaml | head -1 | awk -F': ' '{print $2}' | tr -d '"')
JIRA_EMAIL=$(grep "email" .ai-workflow/config.yaml | head -1 | awk -F': ' '{print $2}' | tr -d '"')
JIRA_API_TOKEN=$(grep "api_token" .ai-workflow/config.yaml | head -1 | awk -F': ' '{print $2}' | tr -d '"')

# Fetch ticket
curl -s -u "$JIRA_EMAIL:$JIRA_API_TOKEN" \
  -H "Content-Type: application/json" \
  "$JIRA_BASE_URL/rest/api/3/issue/$JIRA_KEY?expand=renderedFields,names,schema" \
  -o "docs/operations/$JIRA_KEY/01-ticket-intake/ticket-raw.json"

echo "Exit code: $?"
echo "File size: $(wc -c < docs/operations/$JIRA_KEY/01-ticket-intake/ticket-raw.json) bytes"
```

**Verify**: HTTP 200, valid JSON, file size > 0.

**Interactive checkpoint**:
> Fetched ticket `{JIRA_KEY}`: "{summary}". Continue to validation?
> Options: [Continue] [Show raw ticket] [Stop]

### Step 2: Parse and Normalize

Extract fields into normalized structure. Use `jq` or Python:

```bash
cd "docs/operations/$JIRA_KEY/01-ticket-intake"

# Extract key fields
jq '{
  key: .key,
  type: .fields.issuetype.name,
  priority: .fields.priority.name,
  summary: .fields.summary,
  description: .fields.description,
  labels: .fields.labels,
  components: [.fields.components[].name],
  assignee: .fields.assignee.displayName,
  status: .fields.status.name,
  created: .fields.created,
  updated: .fields.updated
}' ticket-raw.json > ticket.json
```

**For description parsing** (ADF format in Jira API v3):
- If description is an object (ADF), convert to markdown text
- If description is a string, use as-is
- Extract FRs, ACs, dependencies using pattern matching

**Normalized ticket structure**:
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
  "non_functional_requirements": [{"id": "NFR-001", "description": "..."}],
  "acceptance_criteria": [{"id": "AC-001", "scenario": "...", "given": "...", "when": "...", "then": "..."}],
  "dependencies": ["CBOL-100"],
  "out_of_scope": ["..."]
}
```

### Step 3: Validate Against Ticket Spec

Run validation checks per [`jira-ticket-spec.md`](../../jira-ticket-spec.md):

**Mandatory field checks**:
- [ ] `summary` present and non-empty
- [ ] `description` present and non-empty
- [ ] `issuetype` valid: Story, Task, Bug, Spike, Chore
- [ ] `priority` valid: Highest, High, Medium, Low, Lowest
- [ ] At least one domain label (from spec Section 4.1)
- [ ] `assignee` present
- [ ] `components` present

**Type-specific checks**:
- **Story/Task**: ≥1 FR + ≥1 AC scenario
- **Bug**: Steps to Reproduce + Expected + Actual behavior
- **Spike**: Research question defined
- **Chore**: Maintenance task description

**Domain label validation**:
```bash
# Check if any label matches known domain labels
KNOWN_LABELS="message-reception message-management message-forwarding websocket state-machine ai-processing agent-transfer database api security performance"
HAS_DOMAIN_LABEL=false
for label in $(jq -r '.labels[]' ticket.json); do
  if echo "$KNOWN_LABELS" | grep -qw "$label"; then
    HAS_DOMAIN_LABEL=true
    break
  fi
done
echo "Has domain label: $HAS_DOMAIN_LABEL"
```

### Step 4: Generate Verify Report

Write `verify-report.md`:

```markdown
# Verify Report — Ticket Intake

**Ticket**: {JIRA_KEY}
**Date**: {ISO timestamp}
**Fetch method**: MCP / REST API
**Result**: PASS / FAIL

## Mandatory Fields
| Field | Status | Value/Notes |
|-------|--------|-------------|
| summary | ✅/❌ | "{value}" |
| description | ✅/❌ | {length} chars |
| issuetype | ✅/❌ | {value} |
| priority | ✅/❌ | {value} |
| labels | ✅/❌ | {count} labels, {has_domain_label} domain label |
| assignee | ✅/❌ | {value} |
| components | ✅/❌ | {values} |

## Type-Specific Checks ({type})
...

## Validation Errors
- {error 1}
- {error 2}

## Warnings
- {warning 1}

## Evidence
- Fetch command: `{command}`
- Fetch exit code: {0/1}
- Raw file: `ticket-raw.json` ({size} bytes)
- Normalized: `ticket.json`
```

### Step 5: Handle Validation Result

#### If PASS:
1. Update `pipeline-state.json`: mark Stage 1 complete
2. Write operation log
3. **Interactive checkpoint**:
   > Ticket `{JIRA_KEY}` validated ✅. {N} FRs, {M} ACs found. Proceed to Stage 2 (requirements)?
   > Options: [Proceed to Stage 2] [Stop here] [View normalized ticket]

#### If FAIL:
1. Report all validation errors to user
2. **Do NOT proceed** to Stage 2
3. Ask user to:
   - Update ticket in Jira and re-run, OR
   - Provide missing information manually
4. Max 3 retries, then escalate

### Step 6: Write Operation Log

Write `operation-log.md` with:
- KB docs read
- Fetch method (MCP/REST)
- Commands executed + output + exit codes
- Validation results
- Artifacts produced

## Verify Gate

| Criteria | Method | Evidence |
|----------|--------|----------|
| Ticket fetched successfully | MCP response / HTTP 200 | ticket-raw.json + exit code |
| Raw response saved | File exists > 0 bytes | `ls -la ticket-raw.json` |
| All mandatory fields present | Field validation | verify-report.md |
| Valid issue type | Type in allowed list | verify-report.md |
| At least one domain label | Label check | verify-report.md |
| Type-specific checks pass | FR/AC or bug fields | verify-report.md |
| Normalized ticket JSON saved | Valid JSON file | `jq . ticket.json` |
| Verify report generated | File exists | `ls verify-report.md` |
| Pipeline state updated | State file | `cat ../pipeline-state.json` |

**PASS** → All checks ✅ → Proceed to Stage 2 (requirements)
**FAIL** → Report errors, retry (max 3), then escalate

## KB Injection

**Read**:
- `07-Workflows/poc-workflow/jira-ticket-spec.md` — Validation spec
- `06-Skills/02-code-analysis/` — Ticket parsing patterns

**Write**: None

## Error Handling

| Error | Resolution |
|-------|-----------|
| MCP tool not found | Fall back to REST API (Step 1 Path B) |
| REST API auth failed (401) | Check credentials in config, ask user to verify |
| Ticket not found (404) | Verify ticket key, ask user for correct key |
| Network timeout | Retry after 5s, max 3 retries |
| Invalid JSON response | Check Jira API version, re-fetch with different API version |
| Missing mandatory fields | Report to user, ask to update ticket in Jira |
| No domain label | Warn user, suggest adding appropriate label from spec |
| ADF description parsing failed | Fall back to raw description text, note in verify report |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/01-ticket-intake/ticket-raw.json` — Raw Jira response
- `docs/operations/{JIRA_KEY}/01-ticket-intake/ticket.json` — Normalized ticket
- `docs/operations/{JIRA_KEY}/01-ticket-intake/verify-report.md` — Validation report
- `docs/operations/{JIRA_KEY}/01-ticket-intake/operation-log.md` — Operation log

---

*Ticket Intake Skill v2.0.0 — 2026-08-24*
*Optimized with: MCP-first dual-path, interactive checkpoints, CRITICAL rules, precise allowed-tools*
