---
name: checkpoint
description: Create or verify workflow checkpoints with git. Use to save progress milestones during long feature work.
argument-hint: create <name> | verify <name> | list
disable-model-invocation: true
---

# Checkpoint

Save and verify progress milestones during long work sessions.

## Usage

```
/checkpoint create <name>   — save current state
/checkpoint verify <name>   — compare current state to a checkpoint
/checkpoint list            — show all checkpoints
```

## Create Checkpoint

1. Run tests to confirm current state is clean
2. Commit or stash with checkpoint name
3. Log to `.claude/checkpoints.log`:

```bash
echo "$(date +%Y-%m-%d-%H:%M) | $NAME | $(git rev-parse --short HEAD)" >> .claude/checkpoints.log
```

4. Report: "Checkpoint '$NAME' saved at $(git rev-parse --short HEAD)"

## Verify Checkpoint

1. Read checkpoint from `.claude/checkpoints.log`
2. Compare current state:
   - Files added/modified since checkpoint
   - Test pass rate now vs then
   - Build status now vs then

```
CHECKPOINT: $NAME
================================
Files changed: X
Tests: +Y passed / -Z failed
Build: PASS / FAIL
```

## List Checkpoints

Show all entries from `.claude/checkpoints.log` with name, timestamp, git SHA.

## Typical Workflow

```
/checkpoint create "before-refactor"
  → implement changes
/checkpoint create "refactor-done"
  → run tests
/checkpoint verify "before-refactor"
  → open PR
```
