---
name: self-review
description: Force a structured review of any response before returning it. Use after generating code, plans, SQL, schema, or API contracts to catch hallucinations, wrong assumptions, and low-confidence answers.
argument-hint: The response or output to review
---

# Self-Review

Response to review: $ARGUMENTS

## Step 1 — Re-read sources

Before reviewing the output, re-read every source file or doc the response is based on.

If you cannot point to a source for a claim → it is unverified.

## Step 2 — Line-by-line confidence check

For each substantive claim or code block:

| Claim / Code | Source that confirms it | Confidence (HIGH/MED/LOW) | Issue? |
|--------------|------------------------|--------------------------|--------|
| ...          | file:line              | ...                      | ...    |

Flag every LOW confidence item.

## Step 3 — Output-type specific checks

### Code
- Do all method and field names actually exist? (confirm from source you read, not inferred)
- Does it compile? (trace types and imports mentally)
- Does it handle null, empty, and error cases?
- Does it match existing code style and patterns?
- Would this break any existing tests?

### SQL / queries
- Does every table and column name exist? (confirm from schema you actually read)
- Are JOIN conditions correct? (check FK direction)
- Will this return empty results in edge cases where caller expects data?
- LEFT JOIN vs INNER JOIN — did you check which is used?

### Plan
- Does every file path mentioned actually exist?
- Is every assumption explicitly sourced?
- Are any phrases like "probably", "should be", or "likely" present? → those need verification

### API contract / schema
- Does it match what the resolver/REST layer actually expects?
- Are all types correct, especially nullability?
- Are any breaking changes introduced silently?

### Config / YAML / K8s
- Are all required fields present?
- Are resource names, namespaces, and labels consistent with the rest of the project?
- Are secrets handled correctly (not hardcoded)?

## Step 4 — Verdict

- **APPROVED** — No issues. Confidence is HIGH across all claims. Return as-is.
- **APPROVED WITH CAVEATS** — Minor issues. Return but explicitly flag caveats to user.
- **REVISE** — Significant issues. Revise now. Do NOT return the original. Run self-review again on the revised version.

Never return a response with LOW confidence items without flagging them explicitly.
