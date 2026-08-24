# `code-reviewer` Skill — Design Spec

**Status:** Draft v1
**Owner:** gthim
**Last updated:** 2026-04-11

---

## 1. Summary

A standalone Claude Code skill that reviews a diff, commit, branch, or PR and returns a precision-ranked list of findings with evidence. Installed globally at `~/.claude/skills/code-reviewer/SKILL.md` and usable across any project.

**Design thesis:** Almost every public code-review skill optimizes for coverage — long checklists, every possible category, exhaustive rubrics. Anthropic's own production code-review systems optimize for *precision* — three-phase pipelines, confidence thresholds, out-of-scope exclusion lists, and verifier passes that claim <1% false-flag rates. The public ecosystem is not the state of the art. The best code-review skill is the one that reproduces Anthropic's precision discipline in a single SKILL.md, not the one with the longest checklist.

**One-line north star:** *A review with zero findings is a valid output. A review with ten findings and one hallucination is a failure.*

## 2. Goals

1. **Precision over coverage.** A reviewer that surfaces 3 real issues and misses 1 is strictly better than one that surfaces 10 real issues and hallucinates 2. Noise destroys reviewer credibility faster than gaps do.
2. **Zero rubber-stamping.** If the agent approves, it must show its work. "LGTM" without a verification story is forbidden.
3. **Zero hallucinated findings.** Every finding must cite a file, a line range, and a quoted snippet the agent re-read before emitting. No line numbers from memory.
4. **Project-aware without per-project config.** Auto-ingest `CLAUDE.md`, `AGENTS.md`, `.cursorrules`, and the test file for the changed code so one skill tailors itself to any repo.
5. **Parameterized aggressiveness.** Callers in CI, pre-merge, and learning contexts need different strictness. Expose it as an input, don't hardcode.
6. **Self-contained.** One SKILL.md file. No sibling persona file, no slash-command wrapper, no orchestration layer. If it can't live in one file, it's out of scope.

## 3. Non-goals

- **Not a security-only reviewer.** Security is one of five axes. For deep security review, defer to Anthropic's dedicated `/security-review` — this skill flags the obvious OWASP-shape issues and stops.
- **Not an auto-fixer.** The skill reviews and reports. It does not edit code. A separate `/fix-review-findings` skill could be layered later.
- **Not a linter replacement.** Style violations with an existing linter config are out of scope — the linter already caught them.
- **Not a multi-agent orchestrator.** Anthropic's production Code Review product dispatches parallel agents with a verifier. That's a product, not a skill. We approximate with a `find → verify` two-pass inside one agent.
- **Not a PR-size gate for generated code.** Mechanical refactors, generated files, and large renames get a carve-out (see §5.1).

## 4. User-facing surface

### 4.1 Invocation

```
/code-reviewer [target] [strictness]
```

- **`target`** (optional, default `HEAD`): what to review. Accepts:
  - `HEAD` — the last commit
  - `main..HEAD` — all commits on the current branch vs. main
  - `<sha>` — a specific commit
  - `<sha1>..<sha2>` — a commit range
  - `<PR number>` — a GitHub PR (resolved via `gh pr view`)
  - `<file1> <file2> ...` — explicit file list (reviews current working-tree state)
- **`strictness`** (optional, default `standard`):
  - `lenient` — blockers only. Confidence threshold ≥0.85. For CI gates where false positives block merges.
  - `standard` — blockers + majors + minors. Confidence threshold ≥0.7. The default.
  - `strict` — everything including nits. Confidence threshold ≥0.5. For learning-focused reviews and pre-merge deep-dives.

### 4.2 Example calls

```
/code-reviewer                          # review HEAD at standard strictness
/code-reviewer main..HEAD strict        # review whole branch, surface everything
/code-reviewer 1234 lenient             # review PR #1234, blockers only
/code-reviewer src/auth.ts src/user.ts  # review specific files in working tree
```

### 4.3 Outputs

A single markdown report (see §9 for the full template). Rendered to the conversation; the skill does not write files unless explicitly asked.

## 5. Review pipeline

The skill executes four phases in order. Each phase has a fail-fast precondition; if any precondition fails, the pipeline stops and emits an abbreviated report explaining why.

### 5.1 Phase 0 — Preconditions

**Size gate.** If the diff is >1000 lines changed (additions + deletions) and does not match any carve-out, the skill emits one finding ("too large to review meaningfully, split by [strategies]") and stops. Carve-outs:

- Mechanical refactors (renames, import reorders, formatter runs) where >90% of lines match a detectable pattern
- Generated files (lockfiles, migrations, protobuf output, vendored dependencies)
- Pure deletions

Rationale: reviewing unreviewable PRs is the root cause of rubber-stamping. A precondition is the only way to prevent it at the skill level.

**Target resolution.** If `target` can't be resolved (invalid sha, nonexistent PR, missing files), stop and report the error.

**Diff sanity.** If the resolved target contains zero code changes (only whitespace, only comments, only renames with no content change), stop and report — nothing to review.

### 5.2 Phase 1 — Context load

Before reading the diff, the agent reads (in order):

1. `CLAUDE.md`, `AGENTS.md`, `.cursorrules`, `.github/copilot-instructions.md` — whichever exist. These carry project conventions.
2. The **test file(s)** for the changed code. Tests reveal intended behavior; reading them first is the best single heuristic for inferring intent. Consensus across Google, addyosmani, wshobson, VoltAgent, CodeRabbit.
3. Up to 5 **related files** — imports, direct callers of changed functions, the module's README if present. This is Greptile-style intent inference.
4. The **PR description** or commit message, if present.

Budget: Phase 1 should consume no more than ~20% of the skill's total reading budget. If the context load balloons past 10 files, stop and flag "insufficient context to review confidently."

### 5.3 Phase 2 — Find (first pass)

The agent reviews the diff across **five axes**, in this order:

1. **Correctness.** Does the code do what the commit message / PR description / test names say it does? Off-by-one, wrong branch, misused API, unhandled error, race, wrong null semantics.
2. **Design & readability.** Is the change at the right layer? Does it leak abstractions? Can a new team member follow it without asking? Is there obvious duplication with existing code?
3. **Security.** Input validation at boundaries, parameterized SQL, no hardcoded secrets, auth/authz checks present, output encoding. OWASP-shaped core, not exhaustive.
4. **Performance.** N+1 queries, unbounded loops, missing pagination, synchronous work on hot paths. Only flag with a number or a clear scaling argument — never "could be faster."
5. **Tests & observability.** Do the tests cover the change? Is there a missing test for a branch? Are logs/metrics added where failure would be invisible otherwise?

Each finding drafted in Phase 2 gets: severity, confidence score (0.0–1.0), file + line range, quoted snippet (re-read from disk, not remembered), and a 1–3 sentence explanation.

### 5.4 Phase 3 — Verify (second pass)

The agent re-reads each drafted finding and asks three questions:

1. **Could I be wrong?** Is there a plausible reading of the code under which this isn't a problem?
2. **Is there context in another file that invalidates this?** A helper function, a type guard, a test that covers it, a config that disables the code path.
3. **Does the quoted snippet actually say what I claim it says?** Re-read the snippet; check for hallucinated line numbers or paraphrased code.

Any finding that can't survive all three questions is dropped. Findings that survive but with reduced confidence get their score lowered.

This is the single biggest lever against false positives. Anthropic's Code Review product credits a verifier sub-agent for its <1% false-flag rate; we approximate it with an in-agent second pass.

### 5.5 Phase 4 — Filter & render

- Drop findings below the strictness threshold (see §4.1).
- Group by severity.
- Collapse `nit`-tier findings under a fold by default (expanded only at `strict`).
- Emit the report (see §9).

## 6. Severity & confidence model

### 6.1 Severity tiers

| Tier | Meaning | Author action |
|---|---|---|
| `blocker` | The change is wrong, unsafe, or broken. Merging it would cause a bug, outage, or vulnerability. | Must fix before merge. |
| `major` | A significant issue that will cause problems if shipped. Not immediately broken, but a real liability. | Must address or explicitly defer with justification. |
| `minor` | A real improvement that's worth making but not worth blocking on. | Fix before merge if cheap; otherwise optional. |
| `nit` | Style, taste, or micro-preference with no material impact. | Ignorable by default. Folded in output. |

Four tiers, not five or six. The line between `blocker` and `major` is "can this ship?"; the line between `major` and `minor` is "is this a liability?"; the line between `minor` and `nit` is "does this matter to anyone other than the reviewer?"

### 6.2 Confidence score

Every finding carries a confidence score `0.0–1.0`. The agent assigns it honestly:

- `0.9–1.0` — I re-read the code, I can point to the exact line, and there's no plausible context that makes this OK.
- `0.7–0.9` — I'm confident but there's some chance another file changes the picture.
- `0.5–0.7` — I suspect this is an issue but I can't fully verify from what I've read.
- `<0.5` — I'd only surface this at `strict` strictness; it's a hunch.

Strictness determines the cutoff: `lenient` = 0.85, `standard` = 0.7, `strict` = 0.5. Findings below cutoff are dropped silently (not listed as "low confidence findings").

### 6.3 The anti-noise rule

**If the agent finds zero blockers and zero majors, it must say so explicitly in the summary.** It is forbidden to pad the report with minors and nits to appear thorough. A clean review is a valid output.

## 7. Out-of-scope list

The skill explicitly does **not** flag:

- Theoretical race conditions without a reproduction path
- DoS / rate-limiting concerns on non-public endpoints
- Style preferences when no project style guide exists
- Suggestions to rewrite working code in a different paradigm (functional vs. OO, etc.)
- "This could be faster" without a number or a clear scaling argument
- Third-party library CVEs (defer to Dependabot / `npm audit`)
- Missing JSDoc/docstrings on internal functions
- Test file naming conventions when the project has none
- "Consider extracting this into a helper" on code used exactly once
- Memory-safety concerns in memory-safe languages (Go, Rust, JS/TS, Python)

This list is quoted verbatim in the SKILL.md. When the agent declines to flag something borderline, it can cite a specific line from this list. Borrowed from Anthropic's `claude-code-security-review` action and `/security-review` slash command — the single most underused idea outside Anthropic's own tooling.

## 8. Anti-patterns the skill must guard against

| Anti-pattern | Mitigation |
|---|---|
| **Rubber-stamping** | Mandatory "Verification Story" section in output (see §9). Agent must list what it read, what it ran, what it did NOT check. |
| **Sycophancy** | Explicit "Honesty Clause" in the skill body: don't soften real issues, don't hedge on clear bugs, quantify problems when possible. |
| **Hallucinated line numbers** | Every finding requires a quoted snippet re-read from disk in Phase 2/3. No line numbers from memory. |
| **Nit padding** | Anti-noise rule (§6.3) + nits collapsed by default in output. |
| **Pure negativity bias** | Mandatory "Done Well" section — cheap insurance, noted in every serious reviewer skill. |
| **Reviewing without reading tests** | Phase 1 precondition: test files read before diff. |
| **Bikeshedding on taste** | Out-of-scope list (§7) with "style preferences without a project style guide" explicitly called out. |

## 9. Output template

```markdown
# Code Review: <target description>

**Strictness:** <lenient | standard | strict>
**Files reviewed:** <count>
**Verdict:** <clean | minor issues | major issues | blocked>

## Summary

<2–4 sentences. What changed, what the reviewer thinks of it overall,
whether there are blockers. If zero blockers and zero majors, say so
explicitly here — do not pad.>

## Findings

### Blockers (<count>)

<For each: file:line, severity, confidence, quoted snippet, explanation,
suggested fix if obvious.>

### Majors (<count>)

<Same shape.>

### Minors (<count>)

<Same shape. Shown at standard and strict.>

<details>
<summary>Nits (<count>) — click to expand</summary>

<Same shape. Collapsed by default; only shown at strict.>

</details>

## Done Well

<2–5 bullets on what the change gets right. Not pro forma —
only include if there's something genuine to note.>

## Verification Story

**What I read:**
- <file list with 1-line "why">

**What I ran:**
- <tests executed, if any; "none" is a valid answer>

**What I did NOT check:**
- <explicit list of things out of scope for this review — e.g.,
  "performance under load", "the new database migration",
  "browser compatibility">

## Out-of-Scope Notes

<Optional. If the agent noticed something borderline and declined
to flag it because of §7, it can mention it here in 1 line — e.g.,
"Noted a theoretical TOCTOU in file.ts:42 but no repro path, skipped
per out-of-scope rules.">
```

## 10. Success criteria

How we'll know this skill is good:

1. **False-positive rate <5%** on a test set of 20 real PRs. Measured by asking a human to mark each finding as "real / false / taste." Target: ≤1 false per 20 findings across all severities.
2. **Zero hallucinated line numbers.** Every finding's cited line range, when checked against the actual file, exists and contains the quoted snippet.
3. **At least one clean review in the test set.** If the skill finds blockers on every PR, it's broken — some PRs are actually fine.
4. **The verification story is non-empty and accurate.** The "did NOT check" section exists on every output.
5. **Nits don't dominate `standard` output.** At `standard` strictness, nits are folded and the visible findings are blockers + majors + minors only.

## 11. Open questions & future work

- **Q1: How does the skill invoke `git` and `gh`?** The SKILL.md will instruct the agent to use Bash with specific commands, but we should document the exact commands expected (e.g., `git diff <target>`, `gh pr diff <number>`). Resolve during SKILL.md drafting.
- **Q2: Does the skill need a retry / feedback loop if Phase 1 context load fails?** E.g., if `CLAUDE.md` doesn't exist, does it proceed silently or note the absence? Lean: proceed silently, note in "what I read."
- **Q3: What happens on a binary file in the diff?** Lean: list it in "what I did NOT check" and move on.
- **Q4: Should the size gate be configurable?** 1000 lines is a defensible default but some teams' "normal PR" is genuinely larger. Lean: no — configurability adds surface area and the carve-outs already handle the legitimate cases.
- **Future: `code-reviewer-react`, `code-reviewer-python` domain packs.** Layer narrow language-specific rules on top of this base skill. Out of scope for v1.
- **Future: `fix-review-findings` companion skill.** Takes a review report and applies the fixes. Out of scope for v1.

## 12. Why this beats the public alternatives

A tight defense of the design against what's out there.

- **vs. addyosmani/code-review-and-quality:** Same 5-axis framework and test-first ordering, but addyosmani has no confidence scores, no verifier pass, no out-of-scope list, and a 5-tier severity model that fragments near-synonyms. addyosmani optimizes for coverage; we optimize for precision.
- **vs. wshobson/code-review-excellence:** wshobson's 6-emoji label system is theater and its "ask questions instead of stating problems" idea inflates output. We state findings; we don't pose riddles.
- **vs. VoltAgent/code-reviewer:** VoltAgent hardcodes numeric thresholds (coverage >80%, complexity <10) that don't generalize. We use qualitative rules and let project context (CLAUDE.md) tune strictness.
- **vs. Anthropic's `/security-review`:** `/security-review` is a specialist — deep on security, silent on everything else. This skill is a generalist that borrows its precision discipline (confidence thresholds, out-of-scope list, verifier pass) and applies it to all five axes.
- **vs. CodeRabbit / Greptile / Sourcery:** These are products with orchestration layers, codebase indexing, and multi-agent pipelines we can't reproduce in a SKILL.md. But we can reproduce their most valuable single idea (auto-ingest project context files) and their output shape (summary + inline findings + verdict).

---

**Next step:** draft `SKILL.md` implementing this spec.
