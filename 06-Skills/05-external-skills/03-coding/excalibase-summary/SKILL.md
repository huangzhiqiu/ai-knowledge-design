---
name: summary
description: End-of-session summary. Use at the end of any work session to capture what was done, what is open, and update project memory.
---

# Summary

## What was completed

List every change made with file paths and a one-line description of what changed.

## Test status

- Tests passing: X / Y
- Any new failures introduced?
- Any tests skipped or marked pending?

## Open items

List everything NOT completed, with enough context to resume next session without re-reading the whole conversation:
- What was the last state?
- What is the next concrete action?
- What file/line to start from?

## Gotchas discovered this session

New patterns, bugs, or surprises encountered that are not yet in MEMORY.md.
Examples: "LEFT JOIN in orders_summary means order_count can be 0", "TINYINT(1) returns Int not Boolean when tinyInt1isBit=false"

## Update MEMORY.md

Write any new stable patterns or gotchas to the project MEMORY.md now, before the session ends.

Only write things that are:
- Verified (not speculative)
- Stable (not session-specific)
- Not already in MEMORY.md
