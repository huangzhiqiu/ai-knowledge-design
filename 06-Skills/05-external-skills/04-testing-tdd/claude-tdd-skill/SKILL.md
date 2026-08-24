---
name: tdd
description: This skill should be used when the user asks to "implement with TDD", "write tests first", "test-driven development", "start TDD session", "resume TDD", "track TDD progress", or when implementing features/bugs using TDD best practices. Also auto-invokes when .tdd folder exists and user discusses feature implementation.
version: 4.0.0
---

# Test-Driven Development with Interactive Checkpoints

This skill implements a **fully interactive TDD workflow** where:
- **User is prompted at EVERY step** - explicit control over pace
- **Progress saved after each step** - resume from any point
- **All research delegated to agents** - main context stays lean
- **Human-readable progress file** - always know where you are

## Core Principles

### 1. Interactive at Every Step
```
Step → Agent Work → Save Progress → Show Summary → AskUserQuestion → Wait
```
The user ALWAYS sees results and decides whether to continue.

### 2. Research Delegation
The orchestrator NEVER explores code directly. Three dedicated research agents handle all codebase exploration:
- `tdd-codebase-researcher` - Find related code
- `tdd-pattern-researcher` - Find test patterns
- `tdd-dependency-researcher` - Find mocking needs

### 3. Progress Persistence
Every step updates `progress.md` with:
- Step number and name
- Status (pending/in_progress/completed)
- Timestamp
- Brief result summary

### 4. Lean Orchestrator
The orchestrator ONLY:
- Manages step flow
- Saves/loads progress
- Prompts user with AskUserQuestion
- Invokes sub-agents via Task tool
- Makes git commits

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│              TDD Orchestrator (Main Conversation)               │
│  - Step management                                              │
│  - Progress file updates                                        │
│  - User prompts (AskUserQuestion)                               │
│  - Sub-agent invocation                                         │
│  - Git commits                                                  │
└──────────────────────────────┬──────────────────────────────────┘
                               │ Task tool calls
       ┌───────────────────────┼───────────────────────────────────┐
       │                       │                                   │
       ▼                       ▼                                   ▼
┌─────────────────┐   ┌─────────────────────┐   ┌─────────────────┐
│  RESEARCH       │   │  TDD CYCLE          │   │  COMPLETION     │
│  Agents         │   │  Agents             │   │  Agents         │
│ (INIT Phase)    │   │  (CYCLE Phase)      │   │  (FINAL Phase)  │
├─────────────────┤   ├─────────────────────┤   ├─────────────────┤
│ tdd-stack-      │   │ tdd-test-writer     │   │ tdd-coverage-   │
│   analyzer      │   │ tdd-implementer     │   │   checker       │
│ tdd-codebase-   │   │ tdd-failure-        │   │ tdd-session-    │
│   researcher    │   │   analyzer          │   │   reviewer      │
│ tdd-pattern-    │   │ tdd-refactorer      │   └─────────────────┘
│   researcher    │   └─────────────────────┘
│ tdd-dependency- │
│   researcher    │
│ tdd-test-       │
│   designer      │
└─────────────────┘
```

## Step-by-Step Workflow

### PHASE 0: Session Detection

**Step 0.1: Check for Existing Sessions**

```bash
# Check for active sessions
ls .tdd/sessions/*/progress.md 2>/dev/null
```

**If session found with incomplete status:**
1. Read `progress.md` to find last completed step
2. Show resume summary
3. **Prompt user:**
```
AskUserQuestion:
  "Found session '[name]' at step [N]: [step name]. Resume or start new?"
  Options: "Resume session", "Start new session", "View progress details"
```

**If no session or user chooses new:**
- Proceed to Phase 1

---

### PHASE 1: Initialize Session (Steps 1.1 - 1.8)

#### Step 1.1: Create Session

**Action:** Create session folder structure

**Cross-platform:** Use the appropriate script for the user's OS, or create directories directly with Claude Code tools.

```bash
# Linux/macOS/WSL
bash ~/.claude/skills/tdd/scripts/init-tdd-folder.sh

# Windows PowerShell
. $env:USERPROFILE\.claude\skills\tdd\scripts\init-tdd-folder.ps1
```

Then create session directory:
```bash
SESSION_ID="$(date +%Y-%m-%d)_[feature-name]"  # Linux/macOS
# or
$SESSION_ID = "$(Get-Date -Format 'yyyy-MM-dd')_[feature-name]"  # Windows

mkdir -p ".tdd/sessions/$SESSION_ID/research"  # Linux/macOS
# or
New-Item -ItemType Directory -Force -Path ".tdd\sessions\$SESSION_ID\research"  # Windows
```

**Alternative:** If shell scripts fail, use Claude Code's Write tool to create files directly (platform-agnostic).

**Update progress.md:**
```markdown
# TDD Session: [feature-name]

**Session ID**: 2026-02-02_feature-name
**Started**: 2026-02-02 10:30:00
**Status**: IN PROGRESS

## Progress

| # | Step | Status | Time | Notes |
|---|------|--------|------|-------|
| 1.1 | Create session | ✅ DONE | 10:30 | Session folder created |
```

**Prompt user:**
```
AskUserQuestion:
  "Session created: [session_id]. Continue to stack analysis?"
  Options: "Continue", "Stop here"
```

---

#### Step 1.2: Stack Analysis

**Action:** Invoke stack analyzer agent

```
Task(
  subagent_type="tdd-stack-analyzer",
  prompt="Analyze project stack. Save to .tdd/stack.md.
          Return concise summary (max 10 lines)."
)
```

**Update progress.md:** Add row for step 1.2

**Prompt user:**
```
AskUserQuestion:
  "Stack: [language] / [framework] / [test runner]. Continue to codebase research?"
  Options: "Continue", "Stop here", "Show stack details"
```

---

#### Step 1.3: Codebase Research

**Action:** Invoke codebase researcher agent

```
Task(
  subagent_type="tdd-codebase-researcher",
  prompt="Research codebase for [feature].
          Save to .tdd/sessions/[id]/research/codebase.md
          Return concise summary."
)
```

**Update progress.md:** Add row for step 1.3

**Prompt user:**
```
AskUserQuestion:
  "Found [N] related files, [N] integration points. Continue to pattern research?"
  Options: "Continue", "Stop here", "Show codebase findings"
```

---

#### Step 1.4: Pattern Research

**Action:** Invoke pattern researcher agent

```
Task(
  subagent_type="tdd-pattern-researcher",
  prompt="Research test patterns for [area].
          Save to .tdd/sessions/[id]/research/patterns.md
          Return concise summary."
)
```

**Update progress.md:** Add row for step 1.4

**Prompt user:**
```
AskUserQuestion:
  "Patterns: [naming convention], [structure pattern]. Continue to dependency research?"
  Options: "Continue", "Stop here", "Show pattern details"
```

---

#### Step 1.5: Dependency Research

**Action:** Invoke dependency researcher agent

```
Task(
  subagent_type="tdd-dependency-researcher",
  prompt="Research dependencies for [feature].
          Save to .tdd/sessions/[id]/research/dependencies.md
          Return concise summary."
)
```

**Update progress.md:** Add row for step 1.5

**Prompt user:**
```
AskUserQuestion:
  "Dependencies: [N] to mock, [N] existing mock utilities. Continue to test design?"
  Options: "Continue", "Stop here", "Show dependency details"
```

---

#### Step 1.6: Test Design

**Action:** Invoke test designer agent

```
Task(
  subagent_type="tdd-test-designer",
  prompt="Design test cycles for [feature].
          Use research from .tdd/sessions/[id]/research/
          Stack from .tdd/stack.md
          Save to .tdd/sessions/[id]/test-design.md
          Return cycle summary."
)
```

**Update progress.md:** Add row for step 1.6

**Prompt user:**
```
AskUserQuestion:
  "Designed [N] test cycles. Review the plan before starting?"
  Options: "Start TDD cycles", "Review test design first", "Stop here"
```

---

#### Step 1.7: Review Test Design (if requested)

**Action:** Read and display test design summary

```
Read(.tdd/sessions/[id]/test-design.md) → Extract cycle list
```

**Display:**
```
## Test Cycles Planned

1. [Cycle 1]: [description]
2. [Cycle 2]: [description]
3. [Cycle 3]: [description]
...
```

**Prompt user:**
```
AskUserQuestion:
  "Approve this test design?"
  Options: "Approve and start", "Request changes", "Stop here"
```

---

#### Step 1.8: INIT Phase Complete

**Update progress.md:** Mark INIT phase complete

**Update context.json:** Save init completion state

**Prompt user:**
```
AskUserQuestion:
  "INIT complete. Ready to start Cycle 1: [name]?"
  Options: "Start Cycle 1", "Stop here"
```

---

### PHASE 2: TDD Cycles (Steps 2.N.1 - 2.N.4 per cycle)

For each cycle N in test design:

#### Step 2.N.1: RED Phase - Write Failing Test

**Action:** Invoke test writer agent

```
Task(
  subagent_type="tdd-test-writer",
  prompt="Write failing test for Cycle [N]: [name]
          Test design: .tdd/sessions/[id]/test-design.md
          Patterns: .tdd/sessions/[id]/research/patterns.md
          Stack: .tdd/stack.md

          Write test and run it.
          Return: test name, file:line, failure message (max 10 lines)"
)
```

**Update progress.md:** Add row for step 2.N.1

**Prompt user:**
```
AskUserQuestion:
  "RED: Test '[name]' written and failing: [failure reason]. Continue to GREEN?"
  Options: "Continue to GREEN", "Show test code", "Stop here"
```

---

#### Step 2.N.2: GREEN Phase - Make Test Pass

**Action:** Invoke implementer agent

```
Task(
  subagent_type="tdd-implementer",
  prompt="Make test pass with MINIMAL code.
          Test: [name] in [file:line]
          Failure: [message]

          Write implementation and run tests.
          Return: what was implemented, test result (max 10 lines)"
)
```

**If test passes:**

**Update progress.md:** Add row for step 2.N.2

**Prompt user:**
```
AskUserQuestion:
  "GREEN: Test passing. Continue to REFACTOR?"
  Options: "Continue to REFACTOR", "Show implementation", "Stop here"
```

**If test still fails:**

**Action:** Invoke failure analyzer

```
Task(
  subagent_type="tdd-failure-analyzer",
  prompt="Analyze why test still fails.
          Test: [name] in [file:line]
          Implementation: [file]

          Return: diagnosis and suggested fix (max 10 lines)"
)
```

**Prompt user:**
```
AskUserQuestion:
  "Test still failing. [diagnosis]. Try suggested fix?"
  Options: "Apply fix", "More analysis", "Fix manually", "Stop here"
```

---

#### Step 2.N.3: REFACTOR Phase

**Action:** Invoke refactorer agent

```
Task(
  subagent_type="tdd-refactorer",
  prompt="Refactor for quality, keep tests green.
          Test: [file]
          Implementation: [file]

          Apply ONE change at a time, run tests after each.
          Return: changes made or 'no changes needed' (max 10 lines)"
)
```

**Update progress.md:** Add row for step 2.N.3

**Prompt user:**
```
AskUserQuestion:
  "REFACTOR: [changes made or 'Code already clean']. Commit this cycle?"
  Options: "Commit cycle", "More refactoring", "Skip commit", "Stop here"
```

---

#### Step 2.N.4: Commit Cycle

**Action:** Git commit

```bash
git add [test_file] [implementation_file]
git commit -m "[TDD] Cycle [N]: [name]

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

**Update progress.md:** Add row for step 2.N.4, mark cycle complete

**Update context.json:** Save cycle completion

**If more cycles remain:**
```
AskUserQuestion:
  "Cycle [N] committed. [M] cycles remaining. Start Cycle [N+1]: [name]?"
  Options: "Start next cycle", "Stop here"
```

**If last cycle:**
```
AskUserQuestion:
  "All [N] cycles complete! Run coverage analysis?"
  Options: "Run coverage", "Skip to review", "Stop here"
```

---

### PHASE 3: Session Completion (Steps 3.1 - 3.3)

#### Step 3.1: Coverage Analysis

**Action:** Invoke coverage checker agent

```
Task(
  subagent_type="tdd-coverage-checker",
  prompt="Analyze coverage for session files.
          Session: .tdd/sessions/[id]/context.json

          Run coverage, identify gaps.
          Return: coverage %, recommendations (max 10 lines)"
)
```

**Update progress.md:** Add row for step 3.1

**Prompt user:**
```
AskUserQuestion:
  "Coverage: [N]%. [recommendations]. Continue to session review?"
  Options: "Continue", "Add more tests", "Stop here"
```

---

#### Step 3.2: Session Review

**Action:** Invoke session reviewer agent

```
Task(
  subagent_type="tdd-session-reviewer",
  prompt="Review TDD session.
          Session: .tdd/sessions/[id]/

          Extract learnings, update .tdd/learnings.json
          Return: summary and key learnings (max 10 lines)"
)
```

**Update progress.md:** Add row for step 3.2

**Prompt user:**
```
AskUserQuestion:
  "Session reviewed. [N] learnings captured. Archive session?"
  Options: "Archive", "Add notes first", "Stop here"
```

---

#### Step 3.3: Archive Session

**Action:** Move session to archive

```bash
mv .tdd/sessions/[id] .tdd/archive/[id]
```

**Update progress.md:** Mark session COMPLETE

**Final message:**
```
## TDD Session Complete

**Cycles**: [N] completed
**Tests**: [N] written
**Coverage**: [N]%
**Commits**: [N]

Session archived to .tdd/archive/[id]/
```

---

## Progress.md Format

```markdown
# TDD Session: [feature-name]

**Session ID**: 2026-02-02_feature-name
**Feature**: [description]
**Started**: 2026-02-02 10:30:00
**Status**: IN PROGRESS | PAUSED | COMPLETE

## Progress

| # | Step | Status | Time | Notes |
|---|------|--------|------|-------|
| 1.1 | Create session | ✅ DONE | 10:30 | Session initialized |
| 1.2 | Stack analysis | ✅ DONE | 10:31 | TypeScript/Jest detected |
| 1.3 | Codebase research | ✅ DONE | 10:33 | 5 related files found |
| 1.4 | Pattern research | ✅ DONE | 10:35 | AAA pattern, should_* naming |
| 1.5 | Dependency research | ✅ DONE | 10:37 | 3 deps to mock |
| 1.6 | Test design | ✅ DONE | 10:39 | 6 cycles planned |
| 1.7 | Review design | ✅ DONE | 10:40 | Approved |
| 1.8 | INIT complete | ✅ DONE | 10:40 | Ready for cycles |
| 2.1.1 | Cycle 1 RED | ✅ DONE | 10:42 | Test failing correctly |
| 2.1.2 | Cycle 1 GREEN | ✅ DONE | 10:44 | Test passing |
| 2.1.3 | Cycle 1 REFACTOR | ✅ DONE | 10:45 | No changes needed |
| 2.1.4 | Cycle 1 COMMIT | ✅ DONE | 10:45 | abc123 |
| 2.2.1 | Cycle 2 RED | ⏳ IN PROGRESS | 10:47 | Writing test... |

## Resume Point

**Last completed**: Step 2.1.4 (Cycle 1 COMMIT)
**Next step**: Step 2.2.1 (Cycle 2 RED)
**Context**: Starting email validation edge cases
```

---

## Context.json Structure

```json
{
  "schema_version": "4.0",
  "session_id": "2026-02-02_feature-name",

  "metadata": {
    "created_at": "2026-02-02T10:30:00Z",
    "updated_at": "2026-02-02T10:47:00Z",
    "description": "Feature description",
    "status": "in_progress"
  },

  "current_step": {
    "number": "2.2.1",
    "name": "Cycle 2 RED",
    "phase": "cycle",
    "started_at": "2026-02-02T10:47:00Z"
  },

  "completed_steps": [
    {"number": "1.1", "name": "Create session", "completed_at": "..."},
    {"number": "1.2", "name": "Stack analysis", "completed_at": "..."}
  ],

  "research": {
    "stack_analyzed": true,
    "codebase_researched": true,
    "patterns_researched": true,
    "dependencies_researched": true
  },

  "test_design": {
    "total_cycles": 6,
    "cycles": [
      {"number": 1, "name": "Basic validation", "status": "completed"},
      {"number": 2, "name": "Edge cases", "status": "in_progress"},
      {"number": 3, "name": "Error handling", "status": "pending"}
    ]
  },

  "cycles": [
    {
      "number": 1,
      "name": "Basic validation",
      "status": "completed",
      "test_file": "src/auth.spec.ts",
      "test_name": "should_validate_email",
      "implementation_file": "src/auth.ts",
      "git_commit": "abc123",
      "completed_at": "2026-02-02T10:45:00Z"
    }
  ]
}
```

---

## User Prompt Patterns

### Standard Continue/Stop Prompt
```json
{
  "questions": [{
    "question": "[Summary of step result]. Continue to [next step]?",
    "header": "TDD",
    "options": [
      {"label": "Continue", "description": "Proceed to [next step name]"},
      {"label": "Stop here", "description": "Save progress and stop. Resume later with /tdd"},
      {"label": "Show details", "description": "See more about this step"}
    ],
    "multiSelect": false
  }]
}
```

### On Stop Response
```markdown
## Session Paused

**Last completed**: Step [N]: [name]
**Next step**: Step [N+1]: [name]
**Resume**: Say "Resume TDD" or use `/tdd`

Progress saved to: .tdd/sessions/[id]/progress.md
```

---

## Resuming a Session

### Step R1: Detect Session
```bash
# Find most recent active session
find .tdd/sessions -name "progress.md" -exec grep -l "IN PROGRESS\|PAUSED" {} \;
```

### Step R2: Parse Progress
Read `progress.md` and find last completed step.

### Step R3: Show Resume Point
```markdown
## Resuming TDD Session

**Session**: [session_id]
**Feature**: [description]
**Status**: PAUSED at step [N]

**Last completed**: Step [N]: [step name]
**Next step**: Step [N+1]: [next step name]

Continue?
```

### Step R4: Jump to Correct Step
Resume workflow at the appropriate step based on progress.

---

## Files Reference

### Skill Components
- `SKILL.md` - This orchestration document
- `agents/` - Sub-agent definitions (11 agents)
  - Research: `tdd-codebase-researcher`, `tdd-pattern-researcher`, `tdd-dependency-researcher`
  - Init: `tdd-stack-analyzer`, `tdd-test-designer`
  - Cycle: `tdd-test-writer`, `tdd-implementer`, `tdd-failure-analyzer`, `tdd-refactorer`
  - Complete: `tdd-coverage-checker`, `tdd-session-reviewer`
- `references/` - Best practices and templates
  - `tests-md-template.md` - Template for test documentation format
- `scripts/` - Utility scripts (cross-platform)
  - `init-tdd-folder.sh` - Linux/macOS/WSL initialization
  - `init-tdd-folder.ps1` - Windows PowerShell initialization

### Session Files
- `.tdd/stack.md` - Project stack profile
- `.tdd/learnings.json` - Cross-session learnings
- `.tdd/tests.md` - Cumulative test documentation (updated after each session)
- `.tdd/sessions/[id]/`
  - `progress.md` - Human-readable progress (resume point)
  - `context.json` - Machine-readable state
  - `test-design.md` - Test cycle plan
  - `research/`
    - `codebase.md` - Related code findings
    - `patterns.md` - Test pattern findings
    - `dependencies.md` - Mocking strategy

---

## Integration with Other Skills

| Skill | Integration |
|-------|-------------|
| `spec-driven-dev` | Read acceptance criteria from spec |
| `test-runner` | Used by sub-agents to run tests |
| `clean-code` | tdd-refactorer may consult |
| `dotnet-oop` | tdd-refactorer may consult for .NET |
