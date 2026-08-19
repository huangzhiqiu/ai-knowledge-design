---
name: workflow
description: Start or resume the Jira ticket-to-deploy AI development pipeline
arguments:
  - name: jira_key
    description: Jira ticket key (e.g., CBOL-123)
    required: true
  - name: start_from
    description: Force start from specific stage (stage0-6)
    required: false
  - name: status
    description: Show current state without executing
    required: false
---

# Workflow: Ticket to Deploy

Load and execute the `workflow-ticket-to-deploy` skill with the provided arguments.

## Arguments

- `jira_key`: Jira ticket key (e.g., CBOL-123)
- `start_from` (optional): Force start from specific stage
- `status` (optional): Show current state without executing

## Usage

```
/workflow jira_key=CBOL-123
/workflow jira_key=CBOL-123 start_from=stage3
/workflow jira_key=CBOL-123 status
```

## Pipeline Stages

0. Ticket Intake → 1. Requirements → 2. SDD → 3. TDD Implement → 4. Test → 5. PR → 6. Deploy

## Rules

- Design before code: SDD must be approved before implementation
- TDD: RED → GREEN → REFACTOR → Commit
- Evidence: Every completion needs command + output + exit code
- 3-strike: Auto-retry 3 times, then escalate to human
- State persistence: Resume from breakpoint, never from start
