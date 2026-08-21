# Reference Analysis: stel1os/ai-sdd-sop

> Deep analysis of Spec-Driven Development Standard Operating Procedure — five roles, document stack, session start protocol, review rejection loop with findings documents, and spec amendment protocol.

## 1. Project Basic Info

| Field | Value |
|-------|-------|
| **Repository** | https://github.com/stel1os/ai-sdd-sop |
| **Stars** | ~500 (as of 2026-08) |
| **Language** | Markdown templates + Python scripts |
| **Tool** | Tool-agnostic (Claude Code, Cursor, any AI agent) |
| **Version** | v1.4.0 (2026-06-11) |
| **Status** | Mature, well-documented |
| **Philosophy** | Internal workflow discipline, not GxP compliance |

---

## 2. Project Background & Goals

### Problem Statement
AI coding agents are fast but untethered. Without a spec as ground truth, they build plausible-looking things that don't match what was intended. There's no standard operating procedure for AI-assisted software projects.

### Solution
A **Standard Operating Procedure (SOP)** for AI-assisted software projects. Every design decision, implementation task, and test traces back to a spec requirement. The spec is updated when requirements change — not the code.

### Core Philosophy
> "SDD borrows the mindset of change control: write what you intend to build, build exactly that, verify it, release it. No improvisation. No scope creep. No 'I thought you meant…'"

> "This is internal workflow discipline, not GxP. There is no external auditor, no signed deviation, no CAPA. The rigor comes from the process being followed, not from a regulator enforcing it."

### Key Design Decisions
1. **Spec as source of truth** — `SPEC.md` is the ground truth, never derived from code
2. **Five roles** — Planner, Test Designer, Developer, Spec Reviewer, Code Reviewer (one agent per role per task, roles never mix)
3. **Document stack** — 7 layers from Config → Code, everything committed except SPRINT.md
4. **Session start protocol** — Every session begins with reading AGENTS.md + SPRINT.md and reporting position
5. **Findings document** — Review output is structured findings with file:line + criterion citations, not free-form comments
6. **Fresh context for fixes** — Developer fixing review feedback gets a fresh context (no implementation bias)
7. **Spec amendment protocol** — Requirements changes go through a formal process, never silent reinterpretation
8. **Human gates** — SPEC, design, plan, version bump, smoke check, PR merge are human-gated

---

## 3. Architecture Deep Dive

### 3.1 Document Stack (7 Layers)

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: Config — AGENTS.md (committed)                        │
│  Machine-readable project config — roles, technical notes, git  │
│  Read by every agent at spawn. Stable across a sprint.           │
├─────────────────────────────────────────────────────────────────┤
│  Layer 2: Spec — SPEC.md (committed)                            │
│  Source of truth — WHAT to build. Numbered FRs (FR-001...).    │
│  Never derived from code. User writes or approves every line.    │
├─────────────────────────────────────────────────────────────────┤
│  Layer 3: Design — specs/YYYY-MM-DD-<topic>-design.md (committed)│
│  HOW to build a specific feature. Per sprint, user-approved.     │
├─────────────────────────────────────────────────────────────────┤
│  Layer 4: Plan — plans/YYYY-MM-DD-<topic>.md (committed)        │
│  Step-by-step task breakdown with complexity classification.      │
├─────────────────────────────────────────────────────────────────┤
│  Layer 5: State — SPRINT.md (gitignored)                         │
│  Live sprint tracker. Updated at every gate. Single source of    │
│  truth for "where are we." Ephemeral, reset at sprint end.       │
├─────────────────────────────────────────────────────────────────┤
│  Layer 6: Tests — test/ (committed)                              │
│  Verify spec requirements. Written from FRs before implementation.│
│  Traceable to an FR or invariant.                                 │
├─────────────────────────────────────────────────────────────────┤
│  Layer 7: Code — src/ (committed)                                │
│  Implementation. Must match plan → design → spec.                │
└─────────────────────────────────────────────────────────────────┘
```

**Key rule**: Everything is committed except `SPRINT.md`. The spec gets the same change control as the code it governs: history, diffable amendments, recoverability.

### 3.2 Five Roles

| # | Role | Phase | Tier | Does | Does NOT |
|---|------|-------|------|------|----------|
| 1 | **Planner** | Sprint start; spec amendments | standard | Reads spec + open issues. Proposes build order. Flags spec conflicts. Drafts spec amendments when escalated. | Write code or tests. Approve amendments (that's the user). |
| 2 | **Test Designer** | Before implementation | frontier | Reads the FR or issue. Writes failing test assertions. Commits the test file before Developer starts. | Write implementation code. Decide whether the test correctly captures the FR (that's the Spec Reviewer's pre-review). |
| 3 | **Developer** | Implementation | per task | Receives plan + pre-reviewed tests. Implements until tests pass. Opens PR. Escalates spec ambiguity instead of guessing. | Merge. Interpret requirements. Modify tests to make them pass — escalate inconsistency instead. |
| 4 | **Spec Reviewer** | (a) before Developer starts, (b) after each task | per task | **Pre-review:** checks tests against the FR; approves or returns findings. **Post-review:** reads actual code line-by-line against spec — nothing missing, nothing extra, no misinterpretation. Every finding cites file:line. | Comment on code style/structure/naming (that's Code Reviewer). Decide what spec should say. |
| 5 | **Code Reviewer** | After Spec Reviewer approves | per task | Reviews code quality, architecture, test coverage. Returns Critical/Important/Minor findings. | Second-guess spec requirements. Approve code the Spec Reviewer rejected. Run in parallel with Spec Reviewer. |

**Independence rules**:
- Developer never sees reviewer reasoning — fresh context per role, findings documents only
- Spec Reviewer always runs before Code Reviewer, never in parallel
- Limit: independence reduces context bleed, not pattern-matching bias. Two same-tier agents share training and will make correlated errors. Mitigations: use different tiers across roles when budget allows; treat human gates as the real backstop.

### 3.3 Model Tiers

Roles use capability tiers, not model names: **fast / standard / frontier**.

| Task type | Developer | Reviewers |
|-----------|-----------|-----------|
| Mechanical (1–2 files, clear spec) | fast | fast |
| Standard (multi-file, some judgment) | standard | standard |
| Architectural (design decisions, broad impact) | frontier | standard |

- **Test Designer is always frontier** — least constrained reasoning task, mis-specified tests are worse than no tests
- Skip Test Designer for `chore:`, `docs:`, `ci:` commits — applies only where behavior changes
- Tier mapping (as of 2026-06): fast = Haiku, standard = Sonnet, frontier = Opus/Fable
- Update mapping as models evolve; tier semantics don't change

### 3.4 Sprint Lifecycle

```
User: "I want X" (bug fix: issue opened — symptom, root cause, repro case, fix suggestion)
    │
    ▼
Planner → reads spec + issues, proposes build order
    │
    ▼
Design phase → design doc → user approves (bug fix: skip — issue is the design)
    │
    ▼
Planning phase → plan doc, tasks classified by complexity → user approves
    │
    ▼
Test Designer → writes failing tests from FRs (bug fix: test reproducing bug) → commits
    │
    ▼
Spec Reviewer (pre) → tests match the FR/issue? ✅ or findings → Test Designer
    │
    ▼
Developer → implements until tests pass, task by task
    │
    ▼
Per task: Spec Reviewer (post) ✅ → Code Reviewer ✅ (rejection → Review Rejection Loop)
    │
    ▼
(bug fix: regenerate snapshots → review diff → commit)
    │
    ▼
Version bump → build → PR → human smoke check ✅ → merge → release tag → close issues
```

### 3.5 Review Rejection Loop

When any reviewer rejects:

1. **Reviewer produces a findings document** — each finding cites `file:line` and the FR or quality criterion violated. The findings, not the reviewer's reasoning, are the only artifact passed back.

2. **Fresh Developer context** receives plan + tests + findings and fixes. (Pre-review rejections go back to a fresh Test Designer the same way.)

3. **Same reviewer role re-reviews** — fresh instance, full re-review of the task.

4. **Two failed cycles on the same task → stop and escalate to the user.** Repeated rejection usually signals a spec or plan defect, not a coding defect.

**Why findings-only**:
- Prevents developer from being biased by reviewer reasoning
- Focuses on objective criteria (file:line + FR/criterion)
- Makes review actionable (developer knows exactly what to fix)
- Enables fresh context (no need to pass reasoning)

### 3.6 Spec Amendment Protocol

Requirements crystallize through implementation. When Developer or reviewer finds the spec ambiguous, incomplete, or contradictory:

1. **Developer pauses** — Implementation halts at the current task. No guessing.

2. **Open a spec issue** — Title: `spec: clarify FR-xxx — <one-line summary>`. Body: the ambiguity, candidate interpretations, why it matters now.

3. **Planner drafts the amendment** — Proposes a revised FR or a new one. References the original.

4. **User approves the diff** — Spec amendments are user-gated. AI does not approve spec changes.

5. **`SPEC.md` updated in a commit** — `docs(spec): revise FR-xxx — see issue #N`. The affected FR carries a `Revised YYYY-MM-DD — see issue #N` line. The commit diff is the change record.

6. **Tests revisited** — Test Designer re-evaluates the test file; Spec Reviewer pre-reviews again.

7. **Developer resumes.**

**Key principle**: Amendments are normal, not exceptional. Silent reinterpretation is the failure mode this protocol prevents.

### 3.7 Human Gates

Agents stop at these gates, mark them `⏳ awaiting user` in `SPRINT.md`, and do not continue until the user responds.

| Gate | Who approves |
|------|-------------|
| `SPEC.md` content & amendments | Human (always) |
| Design doc | Human |
| Plan doc | Human |
| Spec Reviewer / Code Reviewer ✅ | AI |
| Version bump type | Human |
| Release smoke check | Human |
| PR merge | Human |

### 3.8 Session Start Protocol

**Every session begins here — before any other action.**

1. Read `AGENTS.md` for the project.
2. Read `SPRINT.md` in the project root.
3. Report current position in one sentence: *"We are at [gate], [what is done], next step is [X]."*
4. Continue from the first incomplete gate. Do not restart completed steps.

Gate states: `⬜` open · `✅` complete · `⏳` awaiting user.

- If `SPRINT.md` shows **Idle**, ask the user what to work on.
- If a sprint is **in progress** with no open blockers, continue without asking — the checklist is the instruction.
- If a gate is `⏳ awaiting user`, surface what is needed and stop.

`SPRINT.md` is the single source of truth for project position. Mark each gate in `SPRINT.md` before moving to the next. Never batch updates.

---

## 4. Core Features Deep Dive

### 4.1 Findings Document Format

```markdown
# Review Findings — {task_id}

**Reviewer**: Spec Reviewer (post-implementation)
**Date**: 2026-06-11
**Verdict**: CHANGES REQUESTED

## Critical (must fix)

### Finding 1
- **File**: `src/services/discount.py:42`
- **FR violated**: FR-003 (discount calculation must use percentage, not fixed amount)
- **Issue**: Code uses fixed amount `10.00` instead of percentage `discount_pct / 100 * subtotal`
- **Expected**: `discount = subtotal * (discount_pct / 100)`

### Finding 2
- **File**: `src/models/discount.py:15`
- **FR violated**: FR-001 (discount must have valid_from and valid_until dates)
- **Issue**: `valid_until` field missing from model
- **Expected**: Add `valid_until: datetime` field

## Important (should fix)

### Finding 3
- **File**: `src/services/discount.py:58`
- **Quality criterion**: Error handling — no validation for negative discount_pct
- **Issue**: Negative percentage not rejected
- **Expected**: Raise ValueError if discount_pct < 0 or > 100

## Minor (nice to have)

### Finding 4
- **File**: `src/services/discount.py:1`
- **Quality criterion**: Code style — missing module docstring
- **Issue**: No docstring at module level
- **Expected**: Add module docstring describing discount service

## Summary
- Critical: 2
- Important: 1
- Minor: 1
- Total: 4
```

### 4.2 Testing Contract

Tests must be **traceable**. Every assertion points to one of:
- A **spec requirement** (FR-xxx): `assert firstLump === n_months * budget - sum_paid  # FR-043`
- A **structural invariant**: balance is always non-negative, loan always terminates
- A **golden snapshot**: captures correct output; fails when output drifts unexpectedly

**Tests are never updated silently.** Snapshot updates require:
1. A deliberate command
2. A diff review
3. A commit

The snapshot diff is the verification record. After each bug fix: regenerate snapshots, read the diff, confirm the delta matches the expected fix, commit.

### 4.3 Git Workflow

- `master` — always stable and releasable
- Feature/fix branches: `feat/<issue-number>-<short-description>`
- Every master merge requires a version bump + release tag (no exceptions)
- Commit prefixes: `fix(#N):` / `feat(#N):` / `chore:` / `docs:` / `test:` / `ci:`
- Never edit build artifacts directly — always edit source then rebuild

### 4.4 Release Checklist

- [ ] All tests pass (`npm test` or equivalent)
- [ ] Version bumped (semver: patch for bugs, minor for features, major for breaking)
- [ ] PR opened, CI passes
- [ ] **Human smoke check** — open the built artifact, walk the sprint's user-facing flows from a checklist, confirm pass/fail per item
- [ ] Merged to master/main
- [ ] Release tagged (`gh release create vX.Y.Z`)
- [ ] User-facing changelog updated
- [ ] Issues closed
- [ ] `SPRINT.md` reset to Idle

### 4.5 The Eight Rules

1. **Spec first.** No implementation without an approved design doc (features) or issue (bugs).
2. **No code outside sprint mode.** Discoveries go to issues. Nothing is "just a quick fix."
3. **Tests precede code.** The Test Designer commits the test file before the Developer starts.
4. **Pre-review the tests.** The Spec Reviewer checks the test file against the FR before implementation begins. Wrong tests lock the Developer to a wrong target.
5. **Two-gate review per task.** Spec compliance, then code quality. Both must pass before the next task.
6. **Tests trace to spec.** If you can't name the FR or invariant a test covers, the test is wrong.
7. **Version bump on every master merge.** Including hotfixes and docs-only changes that affect the build artifact.
8. **Source is authoritative.** Build artifacts are generated from `src/`, never edited. Working documents (SPEC, design, plan, AGENTS) are committed — governed intent with change history. Only `SPRINT.md` is gitignored: ephemeral state, not a deliverable.

### 4.6 Working Rules (Adapted from Karpathy)

1. **Think before coding.** State assumptions explicitly. When a request is ambiguous, present multiple interpretations and ask — never guess.
2. **Simplest solution first.** Implement the simplest thing that could work. Do not add abstractions, flexibility, or error handling that weren't explicitly requested.
3. **Surgical changes only.** Modify only what the task requires. Adjacent improvements need explicit authorization, even when the code looks wrong.
4. **Plan, then verify.** For any multi-step task, state a brief plan with verification checkpoints up front. Define success criteria so progress is measurable.

---

## 5. Configuration & Usage

### 5.1 Adopting the SOP

1. **Read first, act later.** Read Document Stack, Five Roles, Session Start Protocol, Lifecycle, Eight Rules, Working Rules.
2. **Wait for explicit user signal** before scaffolding, writing SPEC.md, or invoking any role.
3. **Download templates** into project root:
   - `templates/AGENTS.md` → `AGENTS.md` (required, committed)
   - `templates/SPRINT.md` → `SPRINT.md` (required, gitignored)
   - `templates/CLAUDE.md` → `CLAUDE.md` (optional, recommended)
4. **Fill in every placeholder** in `AGENTS.md`. Never commit `[bracketed]` text.
5. **Write SPEC.md** — product vision, versioning strategy, numbered FRs. User writes or approves every line.
6. **Set up the repo**: `src/`, `test/`, build and test scripts. Create initial regression suite (even if empty) before any feature code.
7. **Point AI tool at AGENTS.md** — every agent reads it at spawn.
8. **Every session after setup** begins with Session Start Protocol.

### 5.2 Three-Layer Config

| File | Defines | Who edits | Updated |
|------|---------|-----------|---------|
| `CLAUDE.md` (optional) | **How** you work — behavioral rules | User | Rarely |
| `AGENTS.md` | **What** this project is — roles, build, invariants | AI + User | Stable across a sprint |
| `SPRINT.md` | **Where** the project is right now — gates | AI | At every gate |

### 5.3 SPRINT.md Template

```markdown
# Sprint Tracker

**Sprint**: {sprint_name}
**Start date**: {YYYY-MM-DD}
**Status**: In progress / Idle

## Current Position
We are at [gate], [what is done], next step is [X].

## Gates
- [ ] SPEC.md approved ⬜
- [ ] Design doc approved ⬜
- [ ] Plan doc approved ⬜
- [ ] Tests written (Test Designer) ⬜
- [ ] Tests pre-reviewed (Spec Reviewer) ⬜
- [ ] Implementation complete (Developer) ⬜
- [ ] Spec review passed ⬜
- [ ] Code review passed ⬜
- [ ] Version bump decided ⏳ awaiting user
- [ ] PR opened ⬜
- [ ] Smoke check passed ⏳ awaiting user
- [ ] PR merged ⏳ awaiting user
- [ ] Release tagged ⬜
- [ ] Issues closed ⬜

## Current Task
- Task ID: {T001}
- Role: {Developer}
- Status: {In progress}

## Blockers
- {None / list blockers}
```

---

## 6. Pros & Cons Analysis

### 6.1 Strengths

| Strength | Description | Impact |
|----------|-------------|--------|
| **Spec as source of truth** | SPEC.md is ground truth, everything traces to it | Prevents drift, ensures requirements met |
| **Five roles with separation** | Planner/Test Designer/Developer/Spec Reviewer/Code Reviewer never mix | Independence, specialization, quality |
| **Findings document** | Review output is structured: file:line + FR/criterion | Objective, actionable, no bias |
| **Fresh context for fixes** | Developer fixing review gets fresh context | No implementation bias, better fixes |
| **Spec amendment protocol** | Formal process for requirement changes | No silent reinterpretation, traceable changes |
| **Session start protocol** | Every session reads state, reports position | No lost context, no restarting completed work |
| **Human gates** | Critical decisions (spec, design, plan, merge) are human-gated | Human in control, accountability |
| **Test-first with pre-review** | Tests written before code, pre-reviewed for correctness | Wrong tests caught before implementation |
| **Two-gate review per task** | Spec compliance + code quality, both must pass | Comprehensive quality assurance |
| **Traceable tests** | Every assertion traces to FR or invariant | No orphan tests, coverage is meaningful |
| **Version bump on every merge** | Every master merge has version + release tag | Traceability, reproducibility |
| **Tool-agnostic** | Works with any AI agent (Claude, Cursor, etc.) | No vendor lock-in |
| **Well-documented** | v1.4.0, comprehensive docs, templates | Easy to adopt, clear procedures |
| **Model tier selection** | Different tiers for different roles/tasks | Cost optimization |
| **Scaling guidance** | Known bottlenecks with mitigations | Enterprise-ready |

### 6.2 Weaknesses

| Weakness | Description | Impact |
|----------|-------------|--------|
| **Heavy process** | 5 roles, 7 document layers, 8 rules, formal protocols | High overhead for small projects/solo devs |
| **No automation** | All orchestration manual (human invokes roles) | Labor-intensive, no pipeline automation |
| **No Jira/tracker integration** | No ticket fetching, status updates | Manual ticket management |
| **No deployment automation** | Smoke check is manual, no CI/CD integration | Manual deployment |
| **No operation logs** | No structured audit trail per stage | Harder to debug/audit |
| **No 3-strike escalation** | Two failed cycles → escalate, but no structured escalation | May not handle stuck tasks well |
| **SPRINT.md gitignored** | State is ephemeral, lost if file deleted | Risk of losing sprint state |
| **No knowledge base** | No structured KB injection | May not follow team conventions |
| **Spec amendment overhead** | Formal process for every spec change | Slow for iterative/exploratory work |
| **Human smoke check** | Manual until automated E2E exists | Labor-intensive |
| **Single developer focus** | Optimized for single developer + AI, not large teams | May need adaptation for teams |
| **No state persistence file** | SPRINT.md is the only state, no structured JSON | Resume may be fragile |

### 6.3 Opportunities

| Opportunity | Description |
|-------------|-------------|
| **Add automation** | Orchestrate roles automatically (pipeline engine) |
| **Add Jira integration** | Fetch tickets, update status, link to artifacts |
| **Add deployment automation** | CI/CD integration, automated smoke checks |
| **Add operation logs** | Structured audit trail per stage/role |
| **Add knowledge base** | Team conventions, patterns, references |
| **Lightweight mode** | Simplified process for small features/solo devs |
| **Larger community** | More contributors, more examples, more tools |

### 6.4 Threats

| Threat | Description |
|--------|-------------|
| **Process fatigue** | Too much process may cause users to abandon it |
| **Competing methodologies** | Other SDD/TDD frameworks may gain more traction |
| **AI native features** | AI tools may build this in natively |

---

## 7. Lessons for CBOL

### 7.1 What CBOL Already Adopted

| Pattern | CBOL Implementation | Source |
|---------|-------------------|--------|
| Spec/SDD as source of truth | Stage 2 SDD must be approved before implementation | ai-sdd-sop SPEC.md |
| Test-first | TDD: RED→GREEN→REFACTOR, tests before code | ai-sdd-sop Test Designer → Developer |
| Two-gate review | Gate 3 (auto-review) + Gate 5 (peer review) | ai-sdd-sop Spec Reviewer + Code Reviewer |
| Human gates | 6 approval gates, all human | ai-sdd-sop human gates |
| Version bump on merge | Conventional commits, PR template | ai-sdd-sop version bump rule |
| Traceable tests | Unit testing guidelines require test naming + coverage | ai-sdd-sop testing contract |

### 7.2 What CBOL Does Better

| Feature | CBOL | ai-sdd-sop |
|---------|------|------------|
| **Automation** | Full pipeline automation (7 stages, orchestrated) | All manual (human invokes roles) |
| **Jira integration** | Native Jira ticket fetch + status | No Jira integration |
| **Structured state file** | JSON state with per-stage status/version/retry | SPRINT.md markdown only |
| **Operation logs** | Structured operation logs per stage | No operation logs |
| **3-strike escalation** | Explicit retry limit + escalation template | Two cycles then escalate (less structured) |
| **Knowledge base injection** | Stage-specific KB doc injection (100+ docs) | No structured KB |
| **Deployment stage** | Stage 6 deploy + docs | Manual smoke check only |
| **Anti-drift checks** | Pre-stage verification | Session start protocol (less rigorous) |
| **Quality gates** | Coverage >= 80%/70%, SonarQube, security | No explicit quality gates |
| **PR template** | Structured PR body with artifact links | No PR template |
| **Custom state machine** | Domain-specific state machine | N/A |

### 7.3 What CBOL Could Learn

| Pattern | Description | CBOL Action |
|---------|-------------|-------------|
| **Findings document format** | Review output: file:line + FR/criterion + expected | Structure Gate 3/Gate 5 review output as findings |
| **Fresh context for review fixes** | Developer fixing review gets fresh context (no bias) | Consider fresh agent context for fixing review feedback |
| **Spec amendment protocol** | Formal process for requirement changes (issue → planner → user approve → update) | Add formal spec/SDD amendment protocol to Stage 1/2 |
| **Pre-review tests** | Spec Reviewer checks tests against FR before implementation | Add test pre-review to Stage 3 (before implementation) |
| **Two independent reviewers** | Spec Reviewer + Code Reviewer, sequential, different focus | Consider adding code quality review separate from spec compliance |
| **Session start protocol** | Every session reads state + reports position in one sentence | Already have state file, could add position report |
| **Model tier selection** | Different tiers for different roles (frontier for review, fast for mechanical) | Consider model tier selection for different pipeline stages |
| **Traceable tests with inline comments** | Every assertion has `# FR-001` comment | Add FR traceability comments to test guidelines |
| **Working rules (Karpathy)** | Think before coding, simplest first, surgical changes, plan+verify | Add working rules to AGENTS.md |
| **SPRINT.md as single source of truth** | One file tracks all gates, updated at every gate | Already have state file, could make it more visible |

---

## 8. Key Code & Config Examples

### 8.1 AGENTS.md Template (conceptual)

```markdown
# AGENTS.md — AI Development Guidelines

## Project Overview
- **Name**: {project_name}
- **Language**: {language}
- **Framework**: {framework}
- **Build**: {build_tool}
- **Test**: {test_framework}

## Roles
- Planner: reads spec + issues, proposes build order
- Test Designer: writes failing tests from FRs
- Developer: implements until tests pass
- Spec Reviewer: checks code against spec (file:line + FR)
- Code Reviewer: reviews code quality, architecture, coverage

## Build Commands
- Install: {install_command}
- Test: {test_command}
- Build: {build_command}
- Lint: {lint_command}

## Invariants
- {invariant_1}
- {invariant_2}

## Git Workflow
- Branch: feat/{issue}-{desc}
- Commit: {type}(#{issue}): {subject}
- Every merge: version bump + release tag

## References
- SPEC.md: requirements
- specs/: design docs
- plans/: task breakdowns
- SPRINT.md: current sprint state (gitignored)
```

### 8.2 SPEC.md Template (conceptual)

```markdown
# SPEC — {project_name}

**Version**: 1.0.0
**Last updated**: {YYYY-MM-DD}

## Product Vision
{One paragraph describing what this product does and why}

## Versioning Strategy
- Semantic versioning: MAJOR.MINOR.PATCH
- MAJOR: breaking changes
- MINOR: new features (backward compatible)
- PATCH: bug fixes (backward compatible)

## Functional Requirements

### FR-001: {requirement title}
**Description**: {what this requirement does}
**Acceptance Criteria**:
- {AC 1}
- {AC 2}
- {AC 3}
**Priority**: Must/Should/Could

### FR-002: {requirement title}
...

## Non-Functional Requirements
- Performance: {NFR}
- Security: {NFR}
- Scalability: {NFR}

## Revision History
| Version | Date | Change | Issue |
|---------|------|--------|-------|
| 1.0.0 | {date} | Initial spec | — |
```

### 8.3 Review Findings Template

```markdown
# Review Findings — {task_id}

**Reviewer**: {Spec Reviewer / Code Reviewer}
**Date**: {YYYY-MM-DD}
**Verdict**: PASS / CHANGES REQUESTED

## Critical (must fix)
### Finding {N}
- **File**: `{path}:{line}`
- **FR/Criterion violated**: {FR-001 / quality criterion}
- **Issue**: {what's wrong}
- **Expected**: {what should be}

## Important (should fix)
...

## Minor (nice to have)
...

## Summary
- Critical: {N}
- Important: {N}
- Minor: {N}
- Total: {N}
```

---

## 9. References

- **Repository**: https://github.com/stel1os/ai-sdd-sop
- **README**: https://github.com/stel1os/ai-sdd-sop/blob/main/README.md
- **Templates**: https://github.com/stel1os/ai-sdd-sop/tree/main/templates
- **CBOL Pipeline**: `../ticket-to-deploy-workflow.md`
- **CBOL Comparison**: `../reference-workflows.md`
- **Karpathy CLAUDE.md**: https://github.com/karpathy/nanoGPT/blob/master/CLAUDE.md (inspiration for working rules)
- **spec-kit**: https://github.com/spec-kit/spec-kit (related tool, SDD extends it)
- **Change Management**: ITIL change management principles (inspiration for spec amendment protocol)

---

*Analysis date: 2026-08-21*
*Analyst: CBOL Knowledge Base AI*
*Project version at analysis: v1.4.0 (2026-06-11)*
