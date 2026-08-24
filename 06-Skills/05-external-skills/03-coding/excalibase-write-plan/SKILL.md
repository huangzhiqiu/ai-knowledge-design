---
name: write-plan
description: Read all sources, build gap matrix, verify assumptions, then output a complete implementation plan. Use before starting any feature, fix, or audit task.
argument-hint: What to plan
---

# Plan

Task to plan: $ARGUMENTS

## Phase 1 — Read everything relevant first

STOP. Do not write a single line of plan until you have read:
1. All files directly involved (schema, impl, tests, SQL init scripts)
2. Any existing similar implementation to understand patterns
3. The MEMORY.md for this project (prior gotchas)

List every file you read and what you learned from it.

## Phase 2 — Build a gap matrix

| What exists | What is missing | Risk (L/M/H) | Assumption to verify |
|-------------|-----------------|---------------|----------------------|
| ...         | ...             | ...           | ...                  |

## Phase 3 — State assumptions explicitly

List every assumption you are making. For each one: "I assume X because I read Y in file Z at line N."

If you cannot point to a source for an assumption → mark it UNVERIFIED and flag it prominently.

## Phase 4 — Output phased plan

Break work into phases. Each phase must specify:
- Exact files to change
- What to read before changing them
- Dependencies between steps
- Risk per step (Low/Medium/High)
- How to verify the change is correct (test or introspection query)

For changes touching ≤3 files, use a compact single-phase format.

## Phase 5 — Testing & success criteria

- List what needs unit tests, integration tests, or manual verification
- Define concrete success criteria (checkboxes)
- If a DB migration is involved, state the rollback strategy

## Phase 6 — Self-review the plan

Run `/self-review` on this plan. Check every file path exists, every assumption is sourced, no capability claims are unverified.

Output: **PLAN READY FOR REVIEW** and wait for user confirmation before executing anything.
