---
name: sdd
description: Generate a Software Design Document (SDD) based on requirements and knowledge base
arguments:
  - name: jira_key
    description: Jira ticket key for context
    required: true
---

# SDD Generator

Load and execute the `sdd-generator` skill to create a comprehensive Software Design Document.

## What It Generates

- Context and problem statement
- Requirements (functional + non-functional)
- Architecture overview and diagrams
- API design (endpoints, request/response, error codes)
- Data model (entities, relationships, schema)
- Business logic and state machines
- Security considerations
- Testing strategy
- Implementation plan and task breakdown

## Knowledge Injection

The SDD generator automatically reads relevant knowledge base documents:
- `02-Chat-Domain-Knowledge/` (IM patterns, Java implementation)
- `03-Design-Guidelines/` (architecture, API design)
- `04-Coding-Guidelines/` (technology-specific standards)

## Usage

```
/sdd jira_key=CBOL-123
```

## Output

SDD saved to `design/sdd/{JIRA-KEY}-sdd.md`
