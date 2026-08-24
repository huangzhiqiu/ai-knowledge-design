# Session Resumption Reference

This document describes how the TDD skill handles checkpoint-based session resumption for seamless continuation across context resets or breaks.

## Design Philosophy

The TDD skill uses an **iterative checkpoint model**:
- Every significant step saves state BEFORE asking the user
- User decides whether to continue or stop at each checkpoint
- Resumption is always from a well-defined checkpoint state

## Checkpoint Types

| Checkpoint | Phase | State | Resume Action |
|------------|-------|-------|---------------|
| `SESSION_CREATED` | Init | Folders created | Continue to stack analysis |
| `STACK_ANALYZED` | Init | Stack profile saved | Continue to test design |
| `DESIGN_COMPLETE` | Init | Test plan ready | Start first cycle |
| `RED_COMPLETE` | Cycle | Failing test written | Continue to GREEN |
| `GREEN_COMPLETE` | Cycle | Test passing | Continue to REFACTOR |
| `REFACTOR_COMPLETE` | Cycle | Code clean | Commit cycle |
| `CYCLE_COMPLETE` | Cycle | Git commit made | Next cycle or complete |
| `COVERAGE_ANALYZED` | Complete | Coverage report | Session review |
| `SESSION_COMPLETE` | Complete | Session archived | Done |

## Session State Files

```
.tdd/sessions/YYYY-MM-DD_feature-name/
├── context.json      # Machine-readable state (checkpoint source of truth)
├── progress.md       # Human-readable progress
├── test-design.md    # Test plan from designer agent
└── metrics.md        # Session metrics
```

## context.json Checkpoint Structure

```json
{
  "schema_version": "3.0",
  "session_id": "2026-01-29_user-auth",

  "metadata": {
    "created_at": "2026-01-29T10:30:00Z",
    "updated_at": "2026-01-29T14:22:00Z",
    "description": "Implement user authentication"
  },

  "stack_profile": "typescript-jest",

  "test_design": {
    "total_cycles": 4,
    "cycles_summary": [
      "Cycle 1: Email validation",
      "Cycle 2: Password hashing",
      "Cycle 3: Login flow",
      "Cycle 4: Token generation"
    ]
  },

  "progress": {
    "current_cycle": 2,
    "current_phase": "green",
    "completed_cycles": 1
  },

  "cycles": [
    {
      "number": 1,
      "name": "Email validation",
      "status": "completed",
      "test_file": "src/auth/auth.spec.ts",
      "test_name": "should validate email format",
      "implementation_file": "src/auth/validators.ts",
      "git_commit": "abc123"
    },
    {
      "number": 2,
      "name": "Password hashing",
      "status": "in_progress",
      "test_file": "src/auth/auth.spec.ts",
      "test_name": "should hash password securely",
      "implementation_file": null,
      "git_commit": null
    }
  ],

  "checkpoints": [
    {
      "type": "DESIGN_COMPLETE",
      "timestamp": "2026-01-29T10:45:00Z",
      "summary": "4 cycles planned"
    },
    {
      "type": "CYCLE_COMPLETE",
      "cycle": 1,
      "timestamp": "2026-01-29T11:30:00Z",
      "git_commit": "abc123"
    },
    {
      "type": "RED_COMPLETE",
      "cycle": 2,
      "timestamp": "2026-01-29T11:45:00Z",
      "summary": "Test 'should hash password securely' failing"
    }
  ],

  "last_checkpoint": {
    "type": "RED_COMPLETE",
    "cycle": 2,
    "timestamp": "2026-01-29T11:45:00Z"
  }
}
```

## Resumption Algorithm

### Step 1: Detect Active Sessions

```bash
# Find sessions with checkpoints (not archived)
find .tdd/sessions -name "context.json" | head -1
```

If multiple sessions exist, show list and ask user which to resume.

### Step 2: Load Last Checkpoint

```javascript
function getResumePoint(context) {
  const lastCheckpoint = context.last_checkpoint;

  // Map checkpoint type to resume action
  const resumeActions = {
    'SESSION_CREATED': { action: 'invoke_stack_analyzer', description: 'Stack analysis' },
    'STACK_ANALYZED': { action: 'invoke_test_designer', description: 'Test design' },
    'DESIGN_COMPLETE': { action: 'start_cycle_1_red', description: 'Start Cycle 1 (RED)' },
    'RED_COMPLETE': { action: 'start_green', description: `Cycle ${lastCheckpoint.cycle} (GREEN)` },
    'GREEN_COMPLETE': { action: 'start_refactor', description: `Cycle ${lastCheckpoint.cycle} (REFACTOR)` },
    'REFACTOR_COMPLETE': { action: 'commit_cycle', description: `Commit Cycle ${lastCheckpoint.cycle}` },
    'CYCLE_COMPLETE': { action: 'start_next_cycle', description: `Start Cycle ${lastCheckpoint.cycle + 1}` },
    'COVERAGE_ANALYZED': { action: 'session_review', description: 'Session review' },
    'SESSION_COMPLETE': { action: 'done', description: 'Session already complete' }
  };

  return resumeActions[lastCheckpoint.type];
}
```

### Step 3: Verify State Integrity

Before resuming, verify:

1. **Files exist**: Check test and implementation files referenced in context
2. **Git state**: Check for uncommitted changes that might conflict
3. **Stack file**: Verify .tdd/stack.md exists

```javascript
function verifyState(context) {
  const issues = [];

  // Check files
  for (const cycle of context.cycles) {
    if (cycle.test_file && !fileExists(cycle.test_file)) {
      issues.push(`Missing test file: ${cycle.test_file}`);
    }
  }

  // Check git
  const gitStatus = exec('git status --porcelain');
  if (gitStatus.includes(context.cycles[context.progress.current_cycle - 1]?.test_file)) {
    issues.push('Uncommitted changes in test file');
  }

  return issues;
}
```

### Step 4: Show Resume Prompt

```markdown
## Resuming TDD Session

**Session**: user-auth
**Last checkpoint**: RED_COMPLETE (Cycle 2)
**Time since checkpoint**: 2 hours ago

### Progress
- Cycle 1: Email validation ✅
- Cycle 2: Password hashing (in progress - test written, needs implementation)
- Cycle 3: Login flow (pending)
- Cycle 4: Token generation (pending)

### Last Action
Wrote failing test: "should hash password securely"
The test expects bcrypt hashing but implementation not yet written.

### Resume Options
1. **Continue** - Proceed to GREEN phase (write implementation)
2. **View test** - See the failing test before continuing
3. **Start over** - Abandon this session and start fresh
```

## User Interaction Pattern

### Resume Prompt (AskUserQuestion)

```json
{
  "questions": [{
    "question": "Found active session 'user-auth' at Cycle 2 (RED complete). Continue to GREEN phase?",
    "header": "Resume TDD",
    "options": [
      {"label": "Continue", "description": "Proceed to write implementation"},
      {"label": "View details", "description": "See what was done before continuing"},
      {"label": "Start fresh", "description": "Archive this session and start new"}
    ],
    "multiSelect": false
  }]
}
```

### On "View details"

Show:
- Last checkpoint details
- Files modified
- Test names written
- Git commits made

Then ask again whether to continue.

### On "Start fresh"

1. Archive current session: `mv .tdd/sessions/[session] .tdd/archive/`
2. Start new session flow

## Handling Issues

### Issue: Missing Files

```markdown
## Warning: File Missing

The file `src/auth/validators.ts` referenced in the session no longer exists.

Options:
1. **Continue anyway** - Will recreate the file
2. **Rollback** - Go back to last git checkpoint
3. **Abort** - Stop and investigate manually
```

### Issue: Uncommitted Changes

```markdown
## Warning: Uncommitted Changes

There are uncommitted changes in files tracked by this session:
- `src/auth/auth.spec.ts` (modified)

Options:
1. **Stash changes** - Save changes aside and resume from clean state
2. **Include changes** - Continue with current state
3. **Review changes** - Show diff before deciding
```

### Issue: Git History Changed

```markdown
## Warning: Git History Mismatch

The last checkpoint commit (abc123) is no longer in git history.
This may happen if commits were amended, rebased, or reset.

Options:
1. **Continue anyway** - Resume from current state
2. **Abort** - Stop and investigate manually
```

## Checkpoint Save Pattern

The orchestrator must save checkpoint BEFORE asking user:

```javascript
// Pattern for each step
async function executeStep(stepName, action) {
  // 1. Do the work (via sub-agent)
  const result = await Task(subagent_type, prompt);

  // 2. Save checkpoint FIRST
  await saveCheckpoint({
    type: stepName,
    timestamp: new Date().toISOString(),
    summary: result.summary
  });

  // 3. THEN ask user
  const userChoice = await AskUserQuestion({
    question: `${result.summary}. Continue to next step?`,
    options: ['Continue', 'Stop here', 'Show details']
  });

  // 4. Handle user choice
  if (userChoice === 'Stop here') {
    showResumeInstructions();
    return 'stopped';
  }

  return 'continue';
}
```

## Best Practices

1. **Save before ask**: Always persist checkpoint before prompting user
2. **Atomic checkpoints**: Each checkpoint represents a complete, resumable state
3. **Clear summaries**: Checkpoint summaries should explain what to do next
4. **Verify on resume**: Always verify file and git state when resuming
5. **Graceful degradation**: If verification fails, offer options rather than aborting
