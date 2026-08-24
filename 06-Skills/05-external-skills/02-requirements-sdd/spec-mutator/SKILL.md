---
name: spec
description: |
  Sole semantic author of SPEC.md at repo root — create, amend, fold designs,
  or backprop bugs. Invoke when user asks to write spec, start new spec, distill spec from
  code, add invariants, amend a section, or record a bug. Common phrasings:
  "write the spec for...", "new spec", "distill spec from code",
  "spec this idea", "import existing repo", "pull invariants out of code",
  "this bug keeps biting", "post-mortem on Y".
license: MIT
compatibility: opencode
---
# spec — spec mutator

## DISPATCH

**Step 0 (precondition):** `git status --porcelain SPEC.md` empty → continue; else bail with "SPEC.md has uncommitted changes; commit or stash first".

**Step 1 (fold-in shortcut):** `$ARGUMENTS` matches `designs/*.md`, file exists, SPEC.md exists @ repo root → FOLD-IN. Else → gate.

Engage the socratic skill gate with `$ARGUMENTS` as intent. Single-question loop until convergence matches one mode:
- **NEW** — goal + first-principle-asked + (≥ 1 invariant or ≥ 1 task)
- **DISTILL** — explicit "build from code" intent
- **BACKPROP** — symptom + surface + recurrence-class
- **AMEND** — §-target + delta

SPEC.md presence is the only branch:
1. no SPEC.md @ repo root → gate restricted to {NEW, DISTILL}.
2. SPEC.md exists → gate ranges over {BACKPROP, AMEND, NEW}.

## NEW — idea → spec
Input: user idea.
1. Goal (1 line) → §G.
2. Constraints stated or implied → §C.
3. External surfaces named → §I.
4. Initial invariants → §V (numbered V<n>).
5. Goal → ordered tasks → §T pipe table, all status `.`, ids T<n>.
6. §B header row only (`id|date|cause|fix`).
→ APPLY.

## DISTILL — code → spec
Walk repo. Produce §G (infer from README/package.json/main entry), §C (infer from stack), §I (enumerate public APIs/CLIs/configs), §V (derive from tests + assertions), §T (one task per known TODO or missing test), §B (empty). Flag uncertain items with `?` so user can confirm.
→ APPLY.

## BACKPROP — bug → §B + §V
Input: gate triple (symptom + surface + recurrence-class).
1. Parse bug.
2. Find root cause (read code).
3. New invariant would catch recurrence? yes → draft `V<next>`.
4. Append §B row `B<next>|<date>|<cause>|<fix>` — fix cell `V<N>` when step 3 drafted, else `-`.
5. Drafted → append invariant to §V.
6. Fix changes behavior → add/patch §T rows.
→ APPLY.

Rule: every bug → §B entry. Invariant optional but preferred.

## AMEND — targeted edit
Input: gate §-target + delta.
Read target §. Ask user what changes.
→ APPLY.
Never silently rewrite §s user did not name.

## FOLD-IN — design draft → §V or §T amend
Input: `designs/<slug>.md` (converged per design skill).
No socratic gate — design skill enforced convergence pre-persist.
Multi-target: one design may propose new §V row(s), §T row(s), §I edit(s), §B row(s) in one apply.
1. Read draft; parse proposed amendments.
2. Draft each in target §s + delta text.
→ APPLY.

## APPLY (all modes, post-delta)

**Step 0 — write-time prune**: Clean up inlined-history residue in delta.
- §V-row delta: strip amendment counters, dated-retirement clauses, supersession narration.
- §B cause trim: auto-trim to one-line bug-class description.

**Step 0.5 — empty-delta check**: pruned delta matches live SPEC.md exactly → print "Spec already matches current state; no changes needed." and exit.

**Step 1 — audit table**:
| audit | fires when | on fail |
|-------|-----------|---------|
| sweep-scope | contains sweep-§T row | bail → SWEEP-§T SCOPE AUDIT |
| pinned-cite | touches PUBLISHED or SPEC.md narrative | bail → PINNED-CITE AUDIT |
| fold-first | adds §V row to pre-existing §V | AskUserQuestion → FOLD-FIRST AUDIT |

**Step 2 — render-split**: §V + §B content rows → steno format for user review; all else → telegraph.

**Step 3 — show-user**: render diff preview; await user OK.

**Step 4 — write + commit**: on OK → write SPEC.md + auto-commit path-scoped:
```bash
git commit -m "<subject>" [-m "<body>"] -- SPEC.md
```

Msg per mode:
```
NEW      → init SPEC.md (V<1>..V<n>, T<1>..T<m>)
DISTILL  → init SPEC.md from code
BACKPROP → backprop §B.<n>(+) + §V.<N>(+): <one-line cause>
AMEND    → amend §<S>.<n>(+): <one-line>
FOLD-IN  → fold-in §V.<n>(+) and §T.<n>(+): <slug>
```

## SWEEP-§T SCOPE AUDIT
Every sweep-§T row (remediating §V-class violation) in delta must declare scope as grep pattern or vocab table. No pattern → bail; user supplies pattern, retry.

## PINNED-CITE AUDIT
**Sub-recipe (a) — PUBLISHED-scope ban**: No `§[VTB]\.[0-9]+` citations in PUBLISHED scope. Use placeholder form (`§V.<n>`) or inline rule embedding.
**Sub-recipe (b) — SPEC.md-narrative §V resolution**: All `§V\.[0-9]+` in SPEC.md narrative must resolve against current §V row set. Unresolved → bail; rewrite or backtick-wrap historical quotes.

## FOLD-FIRST AUDIT
Per fold-first authoring invariant. Each proposed new §V row:
1. Find closest existing §V row by topic.
2. AskUserQuestion:
   - **question**: "New §V row proposed: <delta>. Closest existing row §V.<m>: <summary>. Fold into existing or split as new row?"
   - **options**:
     - `Fold into §V.<m>` → reroute as amend
     - `New row (cite §B recurrence-class)` → proceed with §B cite
     - `New row (orthogonal concept)` → proceed with declaration

## WRITE-TIME PRUNE
Per freshness-contract invariant (SPEC.md is clean current design; history in commit log + archive).

**§V-row delta prune**: Strip inlined-history residue:
- amendment-counter `(∆)` markers → drop
- dated-retirement `retired YYYY-MM-DD` clause → drop
- supersession-narration (`pre-amend …`, `prior … retired/dropped/superseded`) → drop
- `Closes §B.<x>` standalone narration → fold to `(closes §B.<x>)` suffix

**§B cause trim**: Auto-trim to one-line bug-class description; multi-line forensics → commit-msg body.

## POST-APPLY
Every mode post-commit → surface invoking the check skill as Next-block item #1. Operator dispatches next turn → cascade scan over just-applied delta.

## OUTPUT RULES
Read `SPEC-FORMAT.md` — single source of truth for row shape, section catalog + order, citation forms, header conventions.

**Monotonic ID Generation**: Compute any new `V<next>`, `T<next>`, or `B<next>` IDs by finding the absolute maximum ID across both `SPEC.md` and `SPEC.archive.md` (if present) and incrementing it.

## OUTPUT — "Next" block
Heading `## Next`; list follow-up actions for the operator.

---
*Source: https://github.com/kborovik/opencode-skills (skills/spec/)*
*Note: Full skill includes SPEC-FORMAT.md and is part of a larger skill pack with build, check, design, and other skills.*
