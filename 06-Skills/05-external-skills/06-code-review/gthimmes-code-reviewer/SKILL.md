---
name: code-reviewer
description: Reviews a diff, commit, branch, or PR and returns a precision-ranked list of findings with evidence. Optimizes for signal over coverage — rubber-stamps nothing, hallucinates nothing, nitpicks nothing by default.
---

## When to use this skill

Use this skill when asked to review code — a commit, a branch, a PR, or a set of files. Invoke it explicitly with `/code-reviewer` or when the user asks you to "review", "look at this PR", "check this diff", or similar.

Do NOT use this skill for:
- Security-only audits (defer to `/security-review`)
- Fixing or editing code (this skill reports; it does not modify)
- Running linters or formatters

## Inputs

```
/code-reviewer [target] [strictness]
```

**target** (optional, default `HEAD`):
- `HEAD` — the last commit
- `main..HEAD` or `master..HEAD` — all commits on the current branch vs. base
- A commit SHA — a specific commit
- `<sha1>..<sha2>` — a commit range
- A PR number (e.g., `1234`) — a GitHub PR
- One or more file paths — review the working-tree state of those files

**strictness** (optional, default `standard`):
- `lenient` — blockers only. Confidence threshold >= 0.85. Use for CI gates.
- `standard` — blockers + majors + minors. Confidence threshold >= 0.7.
- `strict` — everything including nits. Confidence threshold >= 0.5. Use for learning-focused reviews and deep-dives.

## Process

Execute phases 0 through 4 in order. If any phase fails its precondition, stop and emit an abbreviated report explaining why.

---

### Phase 0 — Preconditions

**Resolve the target.** Determine the diff to review:

- For `HEAD`: run `git diff HEAD~1..HEAD`
- For branch ranges like `main..HEAD`: run `git diff main..HEAD`
- For a commit SHA: run `git diff <sha>~1..<sha>`
- For a commit range: run `git diff <sha1>..<sha2>`
- For a PR number: run `gh pr diff <number>`
- For file paths: run `git diff -- <file1> <file2> ...` and if that's empty, treat the current file contents as the review target

If the target can't be resolved (invalid sha, nonexistent PR, missing files), stop and report the error.

**Check diff size.** Count the total lines changed (additions + deletions). If >1000 lines, check for carve-outs before stopping:

- Mechanical refactors (renames, import reorders, formatter runs) where >90% of changed lines follow a detectable pattern — ALLOW
- Generated files (lockfiles, migrations, protobuf output, vendored dependencies) — EXCLUDE these files from the count and review
- Pure deletions (removing dead code, dropping files) — ALLOW

If the diff exceeds 1000 lines after excluding carve-outs, emit this single finding and stop:

> **blocker** (confidence: 1.0) — This diff is too large to review meaningfully at ~<N> lines changed. Large reviews miss bugs — error detection drops off significantly past ~400 lines. Split strategies: (1) stack by dependency order, (2) split by file/module boundary, (3) separate refactoring from behavior changes, (4) separate tests from implementation.

**Check for empty diffs.** If the resolved diff contains zero code changes (only whitespace, only comments, only renames with no content change), stop and report: nothing to review.

---

### Phase 1 — Context load

Read context BEFORE reading the diff. This shapes your understanding of project conventions and the author's intent.

**Step 1: Project conventions.** Read whichever of these exist at the repo root (silently skip any that don't exist):
- `CLAUDE.md`
- `AGENTS.md`
- `.cursorrules`
- `.github/copilot-instructions.md`

These files carry project-specific conventions, coding standards, and review expectations. Treat their rules as additional review criteria.

**Step 2: Test files.** For each file in the diff, look for its test file (common patterns: `*.test.ts`, `*.spec.ts`, `*_test.go`, `test_*.py`, `*Test.java`, `__tests__/*`). Read the test file if it exists. Tests reveal intended behavior — this is the single best heuristic for inferring the author's intent.

**Step 3: Related context.** Read up to 5 closely related files — direct imports of the changed files, direct callers of changed functions, the parent module's index/barrel file. Stop at 5 files. If more than 5 seem relevant, prioritize the ones closest to the changed code.

**Step 4: PR description / commit message.** Read the commit message (`git log --format=%B -n1 <sha>`) or PR body (`gh pr view <number> --json body`). This is the author's stated intent.

Budget: Phase 1 should not exceed ~15 file reads total. If context is sparse, that's fine — note what you couldn't find in the Verification Story.

---

### Phase 2 — Find (first pass)

Now read the diff. Review it across five axes in this order:

#### Axis 1: Correctness

Does the code do what the commit message / PR description / test names say it does?

Look for:
- Logic errors, off-by-one, wrong branch, wrong boolean logic
- Misused APIs (wrong argument order, wrong return type assumption, deprecated usage)
- Unhandled errors (missing catch, unchecked null, ignored return value that indicates failure)
- Race conditions with a plausible reproduction path
- State mutations that don't account for all callers
- Edge cases visible from the types and the code (empty collections, boundary values, Unicode, negative numbers)

#### Axis 2: Design & readability

Is the change at the right abstraction level? Can someone new follow it?

Look for:
- Wrong layer (business logic in a controller, UI logic in a data model, infrastructure in domain code)
- Leaky abstractions (callers need to know implementation details)
- Obvious duplication with existing code the author may not know about (only flag if you found the duplicate in Phase 1 context loading)
- Unnecessary complexity — code that could be simpler without losing correctness
- Dead code introduced by the change (unused variables, unreachable branches, commented-out code)
- New dependencies — check if they're justified. For any new package/import not previously in the project, note: bundle size impact, maintenance status (last commit date), whether a simpler alternative exists in the existing dependency tree

#### Axis 3: Security

Look for OWASP-shaped issues at system boundaries:
- Missing input validation / sanitization on user-facing inputs
- Non-parameterized SQL or shell commands (injection risk)
- Hardcoded secrets, API keys, tokens, passwords
- Missing or incorrect auth/authz checks
- Missing output encoding (XSS vectors in HTML/template contexts)
- Sensitive data in logs, error messages, or client-facing responses
- Insecure cryptographic choices (MD5/SHA1 for security purposes, ECB mode, fixed IVs)

Do NOT flag theoretical security issues without a plausible attack path. If you can't describe how an attacker exploits it, don't flag it.

#### Axis 4: Performance

Only flag issues where you can articulate a number or a clear scaling argument. "Could be faster" is not a finding.

Look for:
- N+1 queries (a query inside a loop that iterates over a result set)
- Unbounded loops or recursive calls on user-controlled input
- Missing pagination on database queries or API responses
- Synchronous blocking operations on hot paths (e.g., sync file I/O in a request handler)
- Unnecessary re-computation inside loops (invariant code that belongs outside the loop)
- Quadratic or worse algorithms on collections that could realistically be large

#### Axis 5: Tests & observability

Look for:
- Changed behavior with no corresponding test change (the most common oversight)
- Missing test for an obvious error/edge case visible from the code
- Tests that test implementation details instead of behavior (brittle to refactoring)
- Missing error logging or metrics on failure paths that would be invisible in production
- Flaky test patterns (time-dependent, order-dependent, network-dependent without mocking)

**For each finding, draft:**
- Severity: `blocker`, `major`, `minor`, or `nit`
- Confidence: `0.0` to `1.0`
- Location: `file:line-line`
- Quoted snippet: the exact code you're referencing (you MUST re-read the file with the Read tool to get the exact lines — never quote from memory or from the diff alone)
- Explanation: 1-3 sentences. What's wrong, why it matters, and a suggested fix if obvious.

---

### Phase 3 — Verify (second pass)

This is the precision pass. For EVERY finding you drafted in Phase 2, ask these three questions:

1. **Could I be wrong?** Is there a plausible reading of the code under which this isn't a problem? A type guard upstream, a framework guarantee, a convention this project follows?

2. **Is there invalidating context?** Did I read the caller? The config? The middleware? Is there a test that explicitly covers this case? If you're not sure, go read the file now.

3. **Does my evidence actually say what I claim?** Re-read the quoted snippet. Is the line number correct? Does the snippet contain what I said it contains? Am I paraphrasing or quoting?

**Actions after verification:**
- Finding survives all three questions unchanged → keep it
- Finding survives but you're less sure → lower the confidence score
- Finding fails any question → drop it entirely
- You realize the finding is about something out of scope (see §Out of Scope) → drop it

Do not skip this phase. Do not abbreviate it. This is the most important phase in the entire pipeline. Every hallucinated finding that ships past this phase is a failure of the skill.

---

### Phase 4 — Filter & render

1. Drop all findings with confidence below the strictness threshold (`lenient`: 0.85, `standard`: 0.7, `strict`: 0.5).
2. Group remaining findings by severity tier.
3. Count findings per tier.
4. At `lenient` and `standard` strictness, collapse `nit` findings inside a `<details>` fold.
5. Render the report using the output template below.

---

## Severity model

| Tier | Meaning | Author action |
|---|---|---|
| `blocker` | The change is wrong, unsafe, or broken. Merging causes a bug, outage, or vulnerability. | Must fix before merge. |
| `major` | A significant issue that will cause problems. Not immediately broken, but a real liability. | Must address or explicitly defer with justification. |
| `minor` | A real improvement worth making but not worth blocking on. | Fix before merge if cheap; otherwise optional. |
| `nit` | Style, taste, or micro-preference with no material impact. | Ignorable. Collapsed in output by default. |

The line between `blocker` and `major` is: "can this ship safely?"
The line between `major` and `minor` is: "is this a liability if it ships?"
The line between `minor` and `nit` is: "does this matter to anyone other than the reviewer?"

## Confidence scores

Assign honestly:
- `0.9–1.0` — I re-read the code, I can point to the exact line, and there's no plausible context that makes this OK.
- `0.7–0.9` — I'm confident but there's some chance another file changes the picture.
- `0.5–0.7` — I suspect this is an issue but I can't fully verify from what I've read.
- `<0.5` — A hunch. Only surfaces at `strict`.

## Out of scope

The following are explicitly NOT findings. Do not flag them. If you notice something borderline and decide not to flag it because of this list, you may mention it in the "Out-of-Scope Notes" section of the output.

1. Theoretical race conditions without a plausible reproduction path
2. DoS or rate-limiting concerns on non-public endpoints
3. Style preferences when no project style guide or linter config exists
4. Suggestions to rewrite working code in a different paradigm (functional vs. OO, etc.)
5. "This could be faster" without a number or a clear scaling argument
6. Third-party library CVEs (defer to Dependabot, `npm audit`, etc.)
7. Missing JSDoc/docstrings on internal, non-exported functions
8. Test file naming conventions when the project has no documented convention
9. "Consider extracting this into a helper" when the code is used exactly once
10. Memory-safety concerns in memory-safe languages (Go, Rust, JS/TS, Python, Java)
11. Log-format preferences or log-level debates
12. Binary files in the diff (list them in "What I did NOT check" and move on)

## Anti-patterns

Guard against these failure modes throughout every phase:

**Rubber-stamping.** Never emit "LGTM" or "looks good" without the full Verification Story. If you found zero blockers and zero majors, say so explicitly — do not pad the report with nits to appear thorough. A clean review is a valid output.

**Sycophancy.** Do not soften real issues. Do not hedge on clear bugs ("this might possibly be a minor concern" when it's a null dereference). Quantify problems when possible ("this N+1 query adds a round-trip per item — at 100 items that's ~2s of latency"). Push back on clear problems even if the code is written by a senior author.

**Hallucinated evidence.** Every finding MUST include a quoted snippet obtained by re-reading the file with the Read tool during Phase 2 or Phase 3. Never cite line numbers from memory. Never paraphrase code and present it as a quote. If you cannot re-read the file for any reason, do not emit the finding.

**Nit padding.** If you have zero blockers, zero majors, and zero minors, do not manufacture nits to fill the report. State that the code is clean. Your job is to find real issues, not to demonstrate thoroughness.

**Pure negativity.** The "Done Well" section exists. Use it honestly — if the change genuinely does something well (good test coverage, clean error handling, smart use of an existing utility), say so. Do not fill it with generic praise ("good variable names!") just to have something there. If nothing stands out, write "No standout positives to note — the change is straightforward and adequate" and move on.

**Reviewing without context.** Never skip Phase 1. If you review the diff without reading tests, project conventions, or related files, you will miss context and produce worse findings. The cost of Phase 1 is ~10 file reads; the cost of a false positive is reviewer credibility.

## Rationalizations

When you catch yourself thinking any of these, stop — they are the skill failing:

| Rationalization | Why it's wrong |
|---|---|
| "I'll skip the verify pass — my findings look solid" | Every false positive you've ever emitted looked solid in the first pass too. Phase 3 is mandatory. |
| "I'll flag this style issue since I'm already here" | Unless it's in the project's style guide or linter config, it's out of scope. You're burning credibility on taste. |
| "The diff is large but I'll do my best" | You can't review 2000 lines well. Trigger the size gate. A partial review is worse than no review — it creates false confidence. |
| "I didn't find much, I should look harder for issues" | A clean diff is a clean diff. Padding findings is rubber-stamping in reverse. |
| "I'm pretty sure line 42 says X, I don't need to re-read it" | You do. Re-read it. The Phase 3 verify pass exists because models hallucinate line contents. |
| "This is probably fine but I'll flag it just in case" | Assign a confidence score. If it's below the threshold, it gets dropped. Don't flag low-confidence findings as high-confidence to sneak them through. |
| "I'll skip the test files — the implementation tells me enough" | Tests are the author's specification. Skipping them means you're guessing at intent. |

## Output template

Use this exact structure for every review. Do not reorder sections, skip sections, or add sections.

```markdown
# Code Review: <short description of what changed>

**Strictness:** <lenient | standard | strict>
**Files reviewed:** <count of files in the diff>
**Verdict:** <clean | minor issues | major issues | blocked>

## Summary

<2-4 sentences. What changed, what the reviewer thinks of it overall.
If zero blockers and zero majors, say so here explicitly.
Do not pad this section.>

## Findings

### Blockers (<count>)

> **blocker** | confidence: <0.X> | `<file>:<line-line>`
> ```<language>
> <quoted snippet — exact code from the file>
> ```
> <1-3 sentence explanation. What's wrong, why it matters, suggested fix if obvious.>

<Repeat for each blocker. If zero: "None.">

### Majors (<count>)

<Same format. If zero: "None.">

### Minors (<count>)

<Same format. If zero: "None." Shown at standard and strict only.>

<details>
<summary>Nits (<count>) — click to expand</summary>

<Same format. Collapsed by default. Shown at strict only; at other levels this section is collapsed and may be empty.>

</details>

## Done Well

<2-5 bullets on what the change gets right. Be specific and genuine.
If nothing stands out: "No standout positives — the change is straightforward and adequate.">

## Verification Story

**What I read:**
- <file path> — <1-line reason: "test file for auth module", "project conventions", "direct caller of changed function">
- ...

**What I ran:**
- <any commands executed, test suites triggered, etc. "None" is valid.>

**What I did NOT check:**
- <explicit list: binary files skipped, files excluded by carve-outs, areas outside your context window, things you'd want a human to verify>

## Out-of-Scope Notes

<Optional. If you noticed something borderline and declined to flag it per the out-of-scope rules, mention it in one line here. If nothing: omit this section entirely.>
```

## Verification requirements

A review is only complete when:

1. Every finding has a severity, confidence score, file:line reference, and a quoted snippet obtained by re-reading the source file
2. Every finding survived the Phase 3 verify pass
3. All findings below the confidence threshold have been dropped
4. The Verification Story lists what you read, what you ran, and what you did NOT check
5. The Summary honestly reflects the findings — if nothing is wrong, it says so
6. The Done Well section is present and either contains genuine observations or an explicit "nothing stands out"
