---
name: review-plan
description: Adversarial subagent review of a plan before execution. Use after /plan and before starting any implementation.
argument-hint: Plan to review (paste the plan or describe it)
---

# Review Plan

Plan to review: $ARGUMENTS

## Instructions

Deploy a subagent with the following instruction:

---

"You are an adversarial reviewer. Your job is to find problems with this plan BEFORE it is executed.

1. Read every source file the plan references. Do not skip any.
2. Then answer:

   **Wrong assumptions** — What does the plan assume that is actually false or unverified? Point to the exact plan line and the source that contradicts it.

   **Missing cases** — What scenarios, edge cases, or data conditions does the plan not cover?

   **Risks** — What could go wrong during execution that the plan does not account for?

   **Capability claims** — Does the plan claim a DB/API supports something without introspection evidence?

   **File paths** — Do all file paths mentioned in the plan actually exist?

   **Verdict** — APPROVE (proceed) or BLOCK (list issues that must be fixed first)

Be specific. Every finding must cite file:line or a concrete source."

---

## After subagent returns

- **APPROVE** → proceed with execution
- **BLOCK** → address every flagged issue, then re-run `/review-plan` before executing

Do not start implementation until the verdict is APPROVE.
