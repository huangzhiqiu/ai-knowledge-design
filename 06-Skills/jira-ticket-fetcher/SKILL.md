# Jira Ticket Fetcher

> Skill for Stage 0 of the ticket-to-deploy workflow: fetch a Jira ticket, structure its content, extract acceptance criteria, and check clarity.

## Purpose

Fetch a Jira ticket by key, parse its content into a structured format, extract acceptance criteria and user stories, identify missing context, and output a structured ticket summary for downstream stages.

## Input

| Parameter | Required | Description |
|-----------|----------|-------------|
| `jira_key` | Yes | Jira issue key, e.g. `CBOL-123` |

## Output

A structured markdown file at `docs/operations/{JIRA-KEY}/00-ticket.md` with the following sections:

```markdown
# Ticket: {JIRA-KEY} — {Summary}

## Basic Info
- Key: {JIRA-KEY}
- Type: {Story/Bug/Task/Epic}
- Priority: {Highest/High/Medium/Low/Lowest}
- Status: {Current status}
- Assignee: {Assignee or Unassigned}
- Reporter: {Reporter}
- Created: {Date}
- Updated: {Date}
- Labels: {labels}
- Components: {components}
- Epic: {epic link or None}
- Sprint: {sprint or None}

## Description
{Full description text, formatted}

## Acceptance Criteria
{Extracted and numbered acceptance criteria}
1. {criterion 1}
2. {criterion 2}
...

## User Stories
{Extracted user story format: As a {role}, I want {action}, so that {benefit}}

## Linked Issues
- {linked issue key} — {relationship} — {summary}

## Attachments
- {attachment name} — {URL}

## Comments (recent)
- {author} ({date}): {comment text}

## Clarity Check
### Well-defined? {Yes/No/Partial}

### Missing Context
{List of missing information that blocks implementation}
- {missing item 1}
- {missing item 2}

### Clarification Questions
{Questions to post to Jira if ticket is not well-defined}
1. {question 1}
2. {question 2}

### Domain Relevance
{How this ticket relates to CBOL domain knowledge}
- Related domain: {接回话/回话管理/回话转发/...}
- Related knowledge base docs: {paths}

## Next Stage Handoff
{Notes for Stage 1 (Requirements Analysis)}
- Key constraints: {...}
- Key stakeholders: {...}
- Potential risks: {...}
```

## Workflow

1. **Fetch ticket** from Jira REST API
   - `GET /rest/api/3/issue/{key}?fields=*all`
   - Include description, comments, attachments, linked issues, subtasks

2. **Parse and structure** the raw JSON into the output template

3. **Extract acceptance criteria**
   - Look for "Acceptance Criteria" section in description
   - Look for checkbox items (`- [ ]`)
   - Look for "Given/When/Then" format
   - If no explicit AC, infer from description and flag for human review

4. **Extract user stories**
   - Look for "As a ... I want ... so that ..." pattern
   - If none, infer role/action/benefit from description

5. **Clarity check**
   - Is the description detailed enough?
   - Are acceptance criteria testable?
   - Are there open questions in comments?
   - Is the priority/type appropriate?
   - Flag missing context and generate clarification questions

6. **Domain relevance**
   - Read `01-CBOL-Domain-Knowledge/README.md`
   - Identify which domain area the ticket relates to
   - List related knowledge base documents for downstream stages

7. **Write output** to `docs/operations/{JIRA-KEY}/00-ticket.md`

8. **If not well-defined**: post clarification questions as a comment to the Jira ticket, add label `needs-clarification`, and pause the workflow. Do NOT proceed to Stage 1.

## Jira API Reference

### Authentication
- Basic auth: email + API token
- Token obtained from: https://id.atlassian.com/manage-profile/security/api-tokens

### Key Endpoints
```
# Get issue with all fields
GET /rest/api/3/issue/{key}?fields=*all

# Get issue with specific fields
GET /rest/api/3/issue/{key}?fields=summary,description,status,priority,assignee,reporter,labels,components,issuetype,created,updated,comment,attachment,issuelinks,subtasks,epic

# Add comment
POST /rest/api/3/issue/{key}/comment
Body: { "body": { "type": "doc", "version": 1, "content": [...] } }

# Add label
PUT /rest/api/3/issue/{key}
Body: { "fields": { "labels": ["existing-label", "new-label"] } }

# Search by JQL
GET /rest/api/3/search?jql=project=CBOL AND assignee=currentUser()
```

### Atlassian Document Format (ADF)
Jira v3 API uses ADF for rich text fields (description, comments). The structure is:
```json
{
  "type": "doc",
  "version": 1,
  "content": [
    { "type": "paragraph", "content": [{ "type": "text", "text": "..." }] },
    { "type": "bulletList", "content": [...] },
    { "type": "heading", "attrs": { "level": 2 }, "content": [...] }
  ]
}
```
The skill must convert ADF to markdown.

## Configuration

Read from `.ai-workflow/config.yaml`:
```yaml
jira:
  base_url: https://your-domain.atlassian.net
  email: your-email@company.com
  api_token: ${JIRA_API_TOKEN}
```

Or from environment variables:
- `JIRA_BASE_URL`
- `JIRA_EMAIL`
- `JIRA_API_TOKEN`

## Usage

```
/jira-ticket-fetcher jira_key=CBOL-123
```

## Clarity Check Criteria

A ticket is considered **well-defined** if ALL of the following are true:
- [ ] Description is not empty and not just a title
- [ ] At least 2 acceptance criteria are present and testable
- [ ] No open "?" comments from the reporter
- [ ] Priority is set
- [ ] Issue type is appropriate (not "Task" for a feature that should be "Story")

If any fail, generate clarification questions and pause.

## Related Skills

- `workflow-ticket-to-deploy` — Orchestrator that invokes this skill at Stage 0
- `sdd-generator` — Uses the structured ticket output at Stage 2

## References

- Jira REST API docs: https://developer.atlassian.com/cloud/jira/platform/rest/v3/
- Atlassian Document Format: https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/
- Forge (reference): https://github.com/forge-sdlc/forge (Jira ticket intake pattern)
