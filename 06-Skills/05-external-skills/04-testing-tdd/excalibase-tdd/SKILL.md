---
name: tdd
description: Test-Driven Development cycle. Use when implementing any new feature or fix.
argument-hint: Feature or fix to implement
---

# TDD

Task: $ARGUMENTS

## Step 1 — Introspect before writing the test

If this involves a database or API:
- Run `/db-researcher` or query the live schema directly before writing assertions
- For GraphQL: run `{ __type(name: "TableWhereInput") { inputFields { name } } }` to see actual operators
- For SQL views: run `SHOW CREATE VIEW name` or `SELECT pg_get_viewdef(...)` to check JOIN type before asserting aggregates
- Read the DDL for every table/view you will assert against — confirm column types, nullability, FK directions

Do not write a single assertion based on assumption.

## Step 2 — Write the failing test

Write the test FIRST. Ground every assertion in what you actually read in Step 1.

Rules:
- Every `expect(row.field).toX()` must be traceable to a column you read in the schema
- Every `forEach` assertion must be valid for ALL rows, including edge cases (NULLs, 0-count aggregates, empty FKs)
- Every length/count assertion must account for data inserted by earlier tests in the same suite

## Step 3 — Confirm it fails for the right reason

Run the test. It must fail because the feature is missing — NOT because:
- The table/field name is wrong
- The assertion logic itself is broken
- Test data does not exist

Fix test logic issues before proceeding to implementation.

## Step 4 — Implement minimal code

Write the minimum code to make the test pass. No extra features, no speculative abstractions.

Before writing implementation code:
- Read the existing impl file for the same module to match patterns
- Check MEMORY.md for known gotchas for this DB/framework

## Step 5 — Verify green

Run the test. It must pass. If it does not → debug the implementation, not the test.

Also run the full test suite to confirm no regressions.

## Step 6 — Self-check and self-review

Run `/self-check` on all new assertions.
Run `/self-review` on all new implementation code.

Only declare done after both pass.
