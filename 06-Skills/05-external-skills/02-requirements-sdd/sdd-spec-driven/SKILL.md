---
name: sdd
description: This skill should be used when users want guidance on Spec-Driven Development methodology using GitHub's Spec-Kit. Guide users through executable specification workflows for both new projects (greenfield) and existing codebases (brownfield). After any SDD command generates artifacts, automatically provide structured 10-point summaries with feature status tracking, enabling natural language feature management and keeping users engaged throughout the process.
version: 2.1.0
triggers:
  - spec-driven development
  - spec kit
  - speckit
  - sdd
  - specify cli
  - specification driven
  - github spec-kit
  - executable specifications
  - intent-driven development
  - brownfield
  - existing codebase
  - legacy code
  - reverse engineer
  - codebase analysis
  - feature status
  - track features
author: Based on GitHub Spec-Kit by Den Delimarsky and John Lam
license: MIT
tags:
  - development-methodology
  - ai-native-development
  - spec-driven
  - github
  - project-management
  - workflow
  - requirements
  - planning
---
# Spec-Driven Development (SDD) Skill
Guide users through GitHub's Spec-Kit for Spec-Driven Development - a methodology that flips traditional software development by making specifications executable and directly generating working implementations.

## Core Philosophy
Spec-Driven Development emphasizes:
- **Intent-driven development**: Define the "what" before the "how"
- **Rich specification creation**: Use guardrails and organizational principles
- **Multi-step refinement**: Not one-shot code generation
- **AI-native**: Heavy reliance on advanced AI capabilities

Specifications aren't just documentation - they're executable artifacts that directly drive implementation.

## Quick Decision Tree
### Is this a new project (greenfield)?
→ See Greenfield Workflow for the complete 6-step process
### Is this an existing codebase (brownfield)?
→ See Brownfield Workflow for reverse-engineering and integration guidance

## Installation Quick Start
**Recommended (Persistent):**
```bash
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git
```

**One-time Usage:**
```bash
uvx --from git+https://github.com/github/spec-kit.git specify init <PROJECT_NAME>
```

**Verify:**
```bash
specify check
```

## Supported AI Agents
Works with: Claude Code, GitHub Copilot, Gemini CLI, Cursor, Qwen Code, opencode, Windsurf, Codex CLI, and more.

## Artifact Summarization and Feedback Loop
**CRITICAL WORKFLOW**: After any SDD command generates or modifies artifacts, automatically follow this feedback loop:

### After Each Command Completes
1. **Detect Artifact Changes** — Identify which artifacts were created or modified:
   - `constitution.md` (project principles)
   - `spec.md` (requirements specification)
   - `plan.md` (technical implementation plan)
   - `tasks.md` (actionable task breakdown)
2. **Read and Summarize** — Extract key information from artifacts
3. **Present Structured Summary** — Use 10-Point Template (see below)
4. **Include Feature Status** — Brief status line in every summary
5. **Offer Feedback Options**:
   - **A**: "Looks good, proceed to next step"
   - **B**: "I'd like to modify [specific section]"
   - **C**: "Regenerate with these changes: [user input]"
   - **D**: "Explain why [specific decision] was made"

### 10-Point Summary Template
```
## [Command Name] Completed - Here's What Just Happened
### Key Decisions Made (Top 3)
1. [Decision] - Rationale: [Why this was chosen]
2. [Decision] - Rationale: [Why this was chosen]
3. [Decision] - Rationale: [Why this was chosen]
### What Was Generated
- [Artifact 1]: [Brief description]
- [Artifact 2]: [Brief description]
### Important Items to Review (Top 3)
1. [Critical item to check and why it matters]
2. [Important detail to verify and potential impact]
3. [Edge case to consider]
### Watch Out For (Top 2)
- [Potential issue] - How to avoid: [Guidance]
- [Common mistake] - How to avoid: [Guidance]
### What This Enables Next (2 Options)
- Option 1: [Next step] - Best if: [Condition]
- Option 2: [Alternative step] - Best if: [Condition]
Feature Status: [Current Feature Name] ([Stage]) -> Next: [Next Feature]
   Progress: [X]% | Completed: [N] of [Total] features
Your options: [A] Proceed [B] Modify [C] Explain more [D] Show full status
```

## Feature Status Tracking
### Hybrid Approach
After every SDD command, include a brief feature status line in the summary. Provide detailed status on demand.

### Brief Status Line Format
```
Feature Status: [Current Feature Name] ([Stage] - [X]% complete) -> Next: [Next Feature Name]
   Progress: [completed/total] | Dependencies: [status]
```

**Stage values:**
- `Specifying` (20% complete)
- `Planning` (40% complete)
- `Tasking` (60% complete)
- `In Progress` (80% complete)
- `Complete` (100% complete)

### Natural Language Feature Management
Automatically detect and handle natural language feature management requests:
- "Move feature XYZ before ABC" → Reorder
- "Add a feature for email notifications" → New feature
- "Let's do profile-management first" → Move to top priority
- "Skip [feature] for now" → Mark as deferred
- "We finished [feature]" → Update status to complete
- "Show feature status" → Display full dashboard

### Progress Calculation
| Stage | Progress | Indicators |
|-------|----------|------------|
| **Specified** | 20% | `specify.md` exists |
| **Planned** | 40% | `plan.md` exists |
| **Tasked** | 60% | `tasks.md` exists |
| **In Progress** | 80% | Implementation started |
| **Complete** | 100% | Implementation complete, tests pass |

## How to Use This Skill

### When User Asks About SDD
1. Explain core philosophy: Executable specifications, intent-driven, AI-native
2. Verify prerequisites: `uv`, Python 3.11+, Git, AI agent
3. Determine project type: New (greenfield) vs existing (brownfield)
4. Guide to appropriate workflow

### When User Wants to Start a New Project
1. Guide installation
2. Initialize project: `specify init my-project --ai claude`
3. Follow greenfield workflow
4. After each step: Summarize artifacts and get user feedback

### When User Has an Existing Codebase
1. Check for `.speckit/` directory
2. If missing → Guide through brownfield workflow:
   - Analyze existing code
   - Generate constitution from existing patterns
   - Add new features with SDD
3. After each step: Summarize artifacts and get user feedback

### When User Wants to Add a Feature
- Greenfield → Follow steps 3-6 (specify → plan → tasks → implement)
- Brownfield → Follow brownfield workflow steps
- Summarize each artifact before proceeding

---
*Source: https://github.com/SpillwaveSolutions/sdd-skill*
*Note: Full skill includes references/ directory with greenfield.md, brownfield.md, sdd_install.md, feature_management.md.*
