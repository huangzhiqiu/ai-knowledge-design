---
name: self-check
description: Quality gate checklist for tests and assertions. TRIGGER when: about to complete any task that involves writing tests, assertions, or claims about what a database or API supports.
---

# Self-Check

Before marking any task complete, answer every question below. If any answer is NO — fix it before finishing.

## Tests and assertions

- [ ] For every `expect(row.field).toX()` — did I read the DDL/source for that table or view?
- [ ] For every view assertion — did I check whether the view uses LEFT JOIN or INNER JOIN?
- [ ] For every `forEach` assertion — can ANY row in this dataset legally fail this assertion?
- [ ] For every NULL assertion — is the column actually nullable in the schema?
- [ ] For every count/length assertion — could mutations in earlier tests have changed the row count?
- [ ] For every aggregate assertion (sum, avg, count) — did I confirm the data used to compute it?

## Code placement

- [ ] For every block of code inserted mid-file — did I read the surrounding lines to confirm the enclosing class/function/describe block is correct?
- [ ] For every new test — is it in the right `describe` block for what it actually tests?

## Capability claims

- [ ] Did I make any claim about what a DB or API "supports" or "only has"? If yes — what is my evidence and where did I read it?
- [ ] Did I run an introspection query to verify actual available fields/operators before writing tests for them?

## Completion gate

If any checkbox is unchecked → do NOT declare the task done. Fix the gap first.
