---
name: refactor-clean
description: Safely identify and remove dead code with test verification at every step. Use when cleaning up unused exports, files, or dependencies.
argument-hint: [path or leave blank for whole project]
---

# Refactor Clean

Safely remove dead code. Tests must pass before and after every deletion.

## Step 1: Detect Dead Code

| Tool | What It Finds | Command |
|------|--------------|---------|
| knip | Unused exports, files, deps | `npx knip` |
| depcheck | Unused npm dependencies | `npx depcheck` |
| ts-prune | Unused TypeScript exports | `npx ts-prune` |
| vulture | Unused Python code | `vulture src/` |
| deadcode | Unused Go code | `deadcode ./...` |

If no tool available, use Grep to find exports with zero imports.

## Step 2: Categorize Findings

| Tier | Examples | Action |
|------|----------|--------|
| **SAFE** | Unused utilities, internal helpers | Delete with confidence |
| **CAUTION** | Components, routes, middleware | Verify no dynamic imports first |
| **DANGER** | Config files, entry points, type defs | Investigate before touching |

## Step 3: Safe Deletion Loop

For each SAFE item:
1. Run full test suite — establish baseline (all green)
2. Delete the dead code
3. Re-run test suite — verify nothing broke
4. If tests fail → revert immediately (`git checkout -- <file>`), skip item
5. If tests pass → continue

## Step 4: Handle CAUTION Items

Before deleting:
- Search for dynamic imports: `import()`, `require()`, `__import__`
- Search for string references in configs or routes
- Check if exported from a public package API

## Step 5: Consolidate Duplicates

- Near-duplicate functions (>80% similar) → merge
- Redundant type definitions → consolidate
- Wrapper functions that add no value → inline

## Step 6: Summary

```
Deleted:  X unused functions, Y files, Z dependencies
Skipped:  N items (tests failed or uncertain)
Removed:  ~NNN lines
Tests:    all passing
```

## Rules

- Never delete without running tests first
- One deletion at a time — atomic changes make rollback easy
- Skip if uncertain — better to keep dead code than break production
- Don't refactor while cleaning — separate concerns
