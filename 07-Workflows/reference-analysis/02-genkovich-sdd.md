# Reference Analysis: genkovich/sdd

> Deep analysis of Spec-Driven Development for Claude Code — 19 atomic skills, TDD implementation engine, Socratic method, agent-team mode, and living roadmap.

## 1. Project Basic Info

| Field | Value |
|-------|-------|
| **Repository** | https://github.com/genkovich/sdd |
| **Stars** | ~800 (as of 2026-08) |
| **Language** | Markdown skills + Python scripts + JavaScript plugin |
| **Tool** | Claude Code plugin (also Codex CLI, Cursor via installers) |
| **Last Update** | August 2026 (actively maintained) |
| **Status** | Mature, production-ready |
| **Plugin formats** | `.claude-plugin/`, `.codex-plugin/`, `.cursor-plugin/` |
| **MCP Server** | `sdd-dashboard` MCP server declared in `.mcp.json` |

---

## 2. Project Background & Goals

### Problem Statement
AI coding agents jump straight to implementation without understanding the spec. They build plausible-looking things that don't match requirements. There's no structured path from idea to shipped code.

### Solution
A self-contained Claude Code plugin that carries a feature from a one-line idea to **reviewed, verified, shipped** code through **19 atomic, stack-agnostic skills** and a **TDD implementation engine** — with a living roadmap above the per-feature flow.

### Core Philosophy
> "Every skill is Socratic (it walks decisions with you, it doesn't dump a wall of output), gated (a stage hard-refuses when its prerequisite artifact is missing), and stack-agnostic (no language, tracker, or test tool is hard-coded — the skills detect what your repo uses)."

### Key Design Decisions
1. **Spec-first** — spec.md is the source of truth, everything traces back to it
2. **Socratic method** — skills ask questions, don't dictate; depth-tunable (easy/medium/hard)
3. **Gated stages** — each stage hard-refuses if prerequisite artifact is missing
4. **Stack-agnostic** — detects language, test framework, tracker from repo
5. **File-based handoff** — each stage writes a file the next one reads; `/clear` between stages
6. **Independent review** — review skill runs in clean context, no implementation bias
7. **Three execution modes** — sequential single-agent, agent-team, dynamic workflow

---

## 3. Architecture Deep Dive

### 3.1 Skill Taxonomy

```
┌─────────────────────────────────────────────────────────────────┐
│                        Three Kinds of Skills                      │
├─────────────────────────┬─────────────────────────┬───────────────┤
│      BACKBONE           │       UTILITIES         │  CLOSE LOOP   │
│   (run in order)        │    (call anytime)       │  (after code) │
├─────────────────────────┼─────────────────────────┼───────────────┤
│ 0. survey (once/repo)   │ interview (optional)    │ 10. review    │
│ 1. specify               │ classify-size           │ 11. ship      │
│ 2. clarify               │ glossary                │               │
│ 3. design                │ decide-adr              │               │
│ 4. sequences             │ fix                     │               │
│ 5. data-model            │                         │               │
│ 6. api                   │                         │               │
│ 7. tasks                 │                         │               │
│ 8. plan-tests            │                         │               │
│ 9. implement (TDD engine)│                        │               │
└─────────────────────────┴─────────────────────────┴───────────────┘
```

### 3.2 Repository Structure

```
sdd/
├── .claude-plugin/          # Claude Code plugin manifest + marketplace
├── .codex-plugin/           # Codex CLI plugin manifest
├── .cursor-plugin/          # Cursor plugin manifest
├── .mcp.json                # sdd-dashboard MCP server declaration
├── install.sh               # Codex CLI / Cursor installer
├── agents/                   # Specialized agents (10+)
│   ├── explorer             # Codebase exploration
│   ├── test-author          # Writes tests
│   ├── implementer          # Implements code
│   ├── reviewer             # Independent review
│   ├── critic               # Critical analysis
│   ├── devils-advocate      # Challenges assumptions
│   ├── researcher           # Research
│   ├── strategist           # Strategy
│   └── analyst              # Analysis
├── scripts/
│   └── validate_plugin.py   # CI gate: manifests + frontmatter + invariants
├── skills/
│   ├── _shared/              # Canonical shared modules
│   │   ├── socratic-loop.md # Socratic questioning loop
│   │   ├── critic.md        # Critic pattern
│   │   ├── size-matrix.md   # Size classification matrix
│   │   ├── interview-depth.md # Depth dial (easy/medium/hard)
│   │   ├── handoff.md       # Handoff block template
│   │   └── tool-adapters/   # Tool adapters
│   ├── survey/               # Once per repo
│   ├── specify/              # Write spec from interview
│   ├── clarify/              # Sweep spec for ambiguities
│   ├── design/               # Arc42 SAD + C4 + ADRs
│   ├── sequences/            # Mermaid sequence diagrams
│   ├── data-model/           # Schema + migrations (staged)
│   ├── api/                  # OpenAPI contract
│   ├── tasks/                # Atomic tasks + tasks.json DAG
│   ├── plan-tests/           # Test plan (AC → test mapping)
│   ├── implement/            # TDD engine
│   ├── review/               # Independent clean-context review
│   ├── ship/                 # Verify runs + changelog + PR
│   ├── interview/            # Optional idea stress-test
│   ├── classify-size/        # XS/S/M/L/XL sizing
│   ├── glossary/             # Domain term capture
│   ├── decide-adr/           # Standalone ADR
│   └── fix/                  # Bugfix entry point
└── docs/
```

### 3.3 Backbone Flow (Detailed)

```
/sdd:survey (once per repo)
    │
    ▼
/sdd:specify <slug> --depth=easy|medium|hard
    │ (interviews user, writes spec.md + acceptance criteria)
    ▼
/sdd:clarify <slug>
    │ (devil's-advocate pass, closes or defers ambiguities)
    ▼
/sdd:design <slug>
    │ (matches feature to existing architecture, Arc42 SAD + C4 + ADRs)
    ▼
/sdd:sequences <slug>
    │ (Mermaid sequence diagrams for runtime flows)
    ▼
/sdd:data-model <slug>
    │ (schema + staged forward/rollback migrations)
    ▼
/sdd:api <slug>
    │ (OpenAPI contract derived from data model + sequences)
    ▼
/sdd:tasks <slug>
    │ (atomic ≤1-day tasks + tasks.json dependency DAG)
    ▼
/sdd:plan-tests <slug>
    │ (map every AC to ≥1 test)
    ▼
/sdd:implement <slug>
    │ (TDD engine: per task SELECT→RED→GREEN→REFACTOR→GATE→COMMIT)
    ▼
/sdd:review <slug>
    │ (independent clean-context review of whole change)
    ▼
/sdd:ship <slug>
    │ (verify runs, changelog, open PR — never auto-merges)
    ▼
shipped: PR + changelog
```

**Key**: `/clear` between stages. Each stage is gated, re-reads its inputs from disk, and ends by printing the next `/sdd:…` command to copy (the handoff block).

### 3.4 Artifact Flow (Reads → Produces)

| Stage | Reads | Produces |
|-------|-------|----------|
| survey | the repo | `docs/architecture-map.md` (+ scaffold `tasks.json` on greenfield) |
| specify | your idea, `architecture-map.md` | `spec.md` (product spec + AC) |
| clarify | `spec.md` | tightened `spec.md` |
| design | `spec.md`, `CONTEXT.md` | `sad.md`, `adr/*` |
| sequences | `sad.md` | `sad.md §6` (sequence diagrams) |
| data-model | `spec.md`, `sad.md`, sequences | `data-model.md`, staged `migrations/*.up/down.sql` |
| api | `data-model.md`, sequences, `spec.md` | `contracts/openapi.yaml` |
| tasks | all of the above | `tasks/*`, `tasks.json` (dependency DAG) |
| plan-tests | `spec.md`, `data-model.md` | `test-plan.md` (M+) or inline in spec (XS/S) |
| implement | `tasks.json` + all artifacts | code + tests + promoted migrations, committed |
| review | the diff + `spec.md` | review record, `PASS` / `CHANGES REQUESTED` |
| ship | the reviewed change | changelog + PR (never auto-merges) |

### 3.5 TDD Implementation Engine

`implement` reads `tasks.json`, builds a dependency DAG, and runs a **TDD cycle per task**:

```
SELECT → RED → GREEN → REFACTOR → GATE → COMMIT
  │       │      │        │         │       │
  │       │      │        │         │       └─ Commit with SDD-Task/SDD-AC trailers
  │       │      │        │         └─ Unit + integration + lint + vet
  │       │      │        └─ Clean up code while keeping tests green
  │       │      └─ Write minimal code to pass test
  │       └─ Write failing test, prove failure is for the right reason
  └─ Pick next task from DAG (respecting dependencies)
```

**Three execution modes** (chosen automatically from settings + DAG shape):

| Mode | Description | When |
|------|-------------|------|
| **Sequential single-agent TDD** | Default, one agent does everything | Always available (floor) |
| **Agent team** (`team_mode: true`) | `test-author` → `implementer` → `reviewer` over DAG, one git worktree per agent | When team mode enabled |
| **Dynamic workflow** (`workflow_mode: auto`) | Generated `Workflow` pipeline fans out independent tasks up to parallelism cap | When DAG has independent branches |

### 3.6 Size Classification & Routes

Features are classified XS/S/M/L/XL, which determines the **route** (`quick` / `standard` / `full`):

| Size | Route | Behavior |
|------|-------|----------|
| XS/S | `quick` | Optional stages auto-skip if work doesn't exist (e.g., "auto-skipped clarify: zero open questions") |
| M | `standard` | Handoff offers skip as `↳ or …`, user picks |
| L/XL | `full` | Every optional stage runs, no skip alternatives |

**Skip conditions** (N/A conditions, not size defaults):
- `clarify` — zero open questions
- `sequences` — no multi-step flow
- `data-model` — no schema change
- `api` — no contract change
- `plan-tests` — inline in spec (XS/S)

An XS feature *with* a migration still runs `data-model`, on every route. Route steers handoffs only, never locks a door.

### 3.7 Gated Stage Refusals

Stages are gated: each one **hard-refuses when the artifact it consumes is missing** and names the stage to run first.

| Refusal | Meaning | Action |
|---------|---------|--------|
| `design`: «run `specify` first» | No `spec.md` for this slug | Run `/sdd:specify <slug>` |
| `api`: «run `data-model` first» | Feature changes schema but has no `data-model.md` | Run `/sdd:data-model <slug>` |
| `tasks`: «no Accepted ADR» | `design` spawned no ADR (rare) | Run `/sdd:decide-adr <slug>` or re-run design |

---

## 4. Core Features Deep Dive

### 4.1 Socratic Method

**What it is**: Skills don't dump output — they walk decisions with the user through probing questions.

**How it works**:
- Q&A skills (`specify`, `clarify`, `design`) open by setting a **depth dial**
- One `AskUserQuestion` per run tunes how much the skill decides vs. interrogates
- Changes *how many* questions, never *what gets covered*

**Depth levels**:
- `easy` — Skill decides more, fewer questions (faster, less user input)
- `medium` — Balanced (default)
- `hard` — Skill interrogates more, more trade-off discussion (slower, more thorough)

**Why it matters**: AI shouldn't dictate design decisions — it should surface trade-offs and let the human decide. The Socratic method ensures the human is involved in key decisions.

### 4.2 Independent Clean-Context Review

**What it is**: The `review` skill runs in a **fresh, clean context** with no memory of the implementation. It only sees the diff + spec.md.

**Why**:
- Prevents reviewer from being biased by implementation reasoning
- Mimics how a human reviewer would approach a PR (they didn't write the code)
- Catches issues the implementer missed (they're too close to the code)
- Findings (not reasoning) are passed back if changes requested

**What it checks**:
- Spec compliance (every FR implemented, nothing extra)
- Acceptance criteria met
- Code quality
- Test coverage
- Architecture consistency

**Output**: `PASS` or `CHANGES REQUESTED` with structured findings.

### 4.3 Staged Migrations

**What it is**: `data-model` writes migrations under a feature folder (staged), not the live tree. `implement` promotes each staged migration into the live `migrations/` as it builds.

**Why**:
- Migrations aren't applied until the feature is implemented
- Allows review of migrations before they hit the live tree
- Enables rollback (just don't promote, or promote down migration)
- Keeps feature work isolated

### 4.4 Living Roadmap (survey)

**What it is**: `survey` runs once per repo. On an existing codebase, it maps the current architecture to `docs/architecture-map.md`. On an empty repo, it runs a foundation session and scaffolds the skeleton.

**Why**:
- Every later stage reads the architecture map for constraints
- Provides context for design decisions (fit existing architecture)
- Onboards new contributors (understand codebase quickly)
- Living document — updated as architecture evolves

### 4.5 Bugfix Entry Point (fix skill)

**What it is**: `fix` is a dedicated bugfix entry point that:
1. Reproduces the bug
2. Traces symptom to spec's acceptance criteria (regression / ambiguous AC / uncovered gap)
3. Pins it with a failing test
4. Applies minimal fix through the same gate `implement` runs
5. Patches the spec and writes a fix record under `_fixes/`

**Why**: Bugfixes have different flow than features. They don't need full spec/design — they need reproduction, root cause, and regression test. `fix` works even on repos with no specs (code-first, recommends `survey`).

### 4.6 Judgment Agents with Model Tier Selection

**What it is**: Judgment agents (reviewer, critic, devils-advocate, strategist, analyst) default to `opus`. If no Opus access, set `judgment_model: sonnet` in `.claude/sdd.local.md`. A model tier that turns out unavailable **degrades, never blocks**.

**Why**: Judgment tasks need higher reasoning capability. Implementation tasks can use cheaper/faster models. Tier selection optimizes cost vs. quality.

### 4.7 Plugin Validation (CI Gate)

`scripts/validate_plugin.py` is a CI gate that validates:
- Plugin manifests
- Skill/agent frontmatter
- Consistency invariants (links resolve, `/sdd:` form, handoff block, single-source taxonomy, no `_shared` orphans)

**Why**: Ensures the plugin remains consistent and all skills are properly formatted as the project grows.

---

## 5. Configuration & Usage

### 5.1 Installation

```bash
# Claude Code (plugin marketplace)
# Install from .claude-plugin/ marketplace

# Codex CLI / Cursor
./install.sh

# Or manually: copy subtree, prefix skill names, generate functional agents
```

### 5.2 Usage (Typical Flow)

```bash
# First time on a repo
/sdd:survey

# Start a feature
/sdd:specify checkout-discounts --depth=easy

# Walk the backbone (use /clear between stages)
/sdd:clarify checkout-discounts
/sdd:design checkout-discounts
/sdd:sequences checkout-discounts
/sdd:data-model checkout-discounts
/sdd:api checkout-discounts
/sdd:tasks checkout-discounts
/sdd:plan-tests checkout-discounts
/sdd:implement checkout-discounts
/sdd:review checkout-discounts
/sdd:ship checkout-discounts
```

### 5.3 Local Configuration

```yaml
# .claude/sdd.local.md
judgment_model: sonnet  # or opus, fable
team_mode: false         # true for agent-team mode
workflow_mode: auto      # auto, sequential, team
parallelism_cap: 4       # max parallel tasks in dynamic workflow mode
```

### 5.4 Artifact Locations

```
docs/
├── architecture-map.md          # survey output (once per repo)
└── features/
    └── <slug>/
        ├── .size                 # XS/S/M/L/XL
        ├── .route                # quick/standard/full
        ├── spec.md               # specify output
        ├── sad.md                # design output
        ├── data-model.md         # data-model output
        ├── test-plan.md          # plan-tests output (M+)
        ├── tasks.json            # tasks output (dependency DAG)
        ├── adr/                  # ADRs from design
        ├── migrations/           # Staged migrations (promoted by implement)
        └── _fixes/               # Bugfix records
```

---

## 6. Pros & Cons Analysis

### 6.1 Strengths

| Strength | Description | Impact |
|----------|-------------|--------|
| **19 atomic skills** | Fine-grained, composable, each does one thing well | Flexibility, reusability, easy to understand |
| **Socratic method** | Skills ask questions, don't dictate; depth-tunable | Human involved in key decisions, better design |
| **Gated stages** | Hard-refuse if prerequisite missing | Can't skip ahead, prevents errors |
| **Stack-agnostic** | Detects language/test/tracker from repo | Works on any project |
| **TDD engine** | Full TDD cycle per task, 3 execution modes | Enforces TDD, supports parallelism |
| **Independent review** | Clean-context review, no implementation bias | Catches more issues, mimics human review |
| **Staged migrations** | Migrations staged until implementation | Safe, reviewable, rollback-able |
| **Size classification** | XS/S/M/L/XL determines route (quick/standard/full) | Appropriate rigor for feature size |
| **Bugfix entry point** | Dedicated `fix` skill for bugfixes | Different flow for bugs vs features |
| **Living roadmap** | `survey` maps architecture once per repo | Context for all later stages |
| **Multi-platform** | Claude Code, Codex CLI, Cursor plugins | Broad compatibility |
| **MCP server** | `sdd-dashboard` MCP server | Dashboard/monitoring |
| **CI validation** | `validate_plugin.py` checks consistency | Maintains quality as project grows |
| **Judgment model tier** | Reviewers use stronger model, implementers use cheaper | Cost optimization |

### 6.2 Weaknesses

| Weakness | Description | Impact |
|----------|-------------|--------|
| **Many stages** | 11 backbone stages + utilities = lots of steps | Slow for small features, friction |
| **/clear between stages** | Must clear context between each stage | Manual intervention, loses context |
| **No Jira integration** | No ticket tracking integration | Manual ticket management |
| **No deployment automation** | `ship` opens PR, no deploy | Manual deployment |
| **No operation logs** | No structured audit trail per stage | Harder to debug/audit |
| **No 3-strike escalation** | No explicit retry limit/escalation | Potential infinite loops |
| **Claude-centric** | Designed for Claude Code, others via installers | Best experience on Claude |
| **Complex setup** | Plugin, agents, scripts, MCP server | Steep learning curve |
| **No knowledge base** | No structured KB injection | May not follow team conventions |
| **Artifacts in docs/features/** | Not standard location for all teams | May conflict with existing docs structure |
| **No state persistence** | No state file, relies on artifacts existing | Resume may be fragile |

### 6.3 Opportunities

| Opportunity | Description |
|-------------|-------------|
| **Add Jira integration** | Fetch tickets, update status, link to artifacts |
| **Add deployment stage** | CI/CD integration, automated deploy |
| **Add operation logs** | Structured audit trail per stage |
| **Add 3-strike escalation** | Prevent infinite loops |
| **Add knowledge base injection** | Team conventions, patterns, references |
| **Simplify for small features** | Faster path for XS features (already has quick route) |
| **Larger community** | More contributors, more examples |

### 6.4 Threats

| Threat | Description |
|--------|-------------|
| **Claude Code native features** | Anthropic may build this into Claude Code natively |
| **Project complexity** | Too many skills may overwhelm users |
| **Maintenance burden** | 19 skills + agents + scripts = lots to maintain |

---

## 7. Lessons for CBOL

### 7.1 What CBOL Already Adopted

| Pattern | CBOL Implementation | Source |
|---------|-------------------|--------|
| Spec/SDD first | Stage 2 (SDD) before Stage 3 (implementation) | sdd specify → design → implement |
| TDD enforcement | RED→GREEN→REFACTOR→COMMIT per task | sdd implement TDD engine |
| Independent review | Gate 3 auto-review + Gate 5 peer review | sdd review (clean context) |
| Task breakdown | SDD includes implementation plan | sdd tasks (tasks.json DAG) |
| Size-appropriate rigor | Quick route for small features | sdd classify-size + routes |
| Bugfix flow | Different handling for bugs vs features | sdd fix skill |

### 7.2 What CBOL Does Better

| Feature | CBOL | sdd |
|---------|------|-----|
| **Jira integration** | Native Jira ticket fetch + status update | No Jira integration |
| **Structured state file** | JSON state with per-stage status/version/retry | No state file, relies on artifacts |
| **Operation logs** | Structured operation logs per stage | No operation logs |
| **3-strike escalation** | Explicit retry limit + escalation template | No explicit escalation |
| **Knowledge base injection** | Stage-specific KB doc injection (100+ docs) | No structured KB |
| **Deployment stage** | Stage 6 deploy + docs | `ship` opens PR only, no deploy |
| **Anti-drift checks** | Pre-stage verification | Gated stages but no explicit anti-drift |
| **Quality gates** | Coverage >= 80%/70%, SonarQube, security | No explicit quality gates |
| **PR template** | Structured PR body with artifact links | No PR template |
| **Custom state machine** | Domain-specific state machine | N/A |

### 7.3 What CBOL Could Learn

| Pattern | Description | CBOL Action |
|---------|-------------|-------------|
| **Socratic method** | Skills ask questions, don't dictate; depth-tunable | Add Socratic questioning to Stage 1 (Requirements) |
| **Size classification** | XS/S/M/L/XL determines route (quick/standard/full) | Add feature sizing to determine pipeline rigor |
| **Staged migrations** | Migrations staged until implementation | Consider staging DB migrations in SDD |
| **Independent clean-context review** | Review in fresh context, no implementation bias | Consider fresh agent context for Gate 3 review |
| **Bugfix entry point** | Dedicated flow for bugs (reproduce → pin → fix → regression) | Add bugfix-specific pipeline path |
| **Living roadmap** | `survey` maps architecture once per repo | Add codebase survey skill (already have architecture-analyzer) |
| **Judgment model tier** | Reviewers use stronger model than implementers | Consider model tier selection for different stages |
| **Artifact-based handoff** | Each stage writes a file the next reads; `/clear` between | Already have operation logs, could formalize artifact handoff |
| **Plugin validation CI** | Validate skill consistency in CI | Add validation for CBOL skills |

---

## 8. Key Code & Config Examples

### 8.1 tasks.json (Dependency DAG)

```json
{
  "feature": "checkout-discounts",
  "tasks": [
    {
      "id": "T001",
      "title": "Add Discount model and migration",
      "estimate": "0.5d",
      "depends_on": [],
      "acceptance_criteria": ["FR-001", "FR-002"]
    },
    {
      "id": "T002",
      "title": "Implement discount calculation service",
      "estimate": "1d",
      "depends_on": ["T001"],
      "acceptance_criteria": ["FR-003", "FR-004"]
    },
    {
      "id": "T003",
      "title": "Add discount API endpoints",
      "estimate": "0.5d",
      "depends_on": ["T002"],
      "acceptance_criteria": ["FR-005"]
    }
  ]
}
```

### 8.2 Handoff Block Template

```markdown
## What I did
- Wrote spec.md with 5 FRs and acceptance criteria
- Classified as M size, standard route

## Review before continuing
- [spec.md](docs/features/checkout-discounts/spec.md)
- [.size](docs/features/checkout-discounts/.size) → M
- [.route](docs/features/checkout-discounts/.route) → standard

## Run next
/clear
/sdd:clarify checkout-discounts
```

### 8.3 Skill Frontmatter (conceptual)

```yaml
---
name: sdd:implement
description: TDD implementation engine — per task SELECT→RED→GREEN→REFACTOR→GATE→COMMIT
model: inherit
requires:
  - docs/features/{slug}/tasks.json
  - docs/features/{slug}/spec.md
produces:
  - src/ (implementation code)
  - test/ (test code)
  - migrations/ (promoted migrations)
arguments:
  - name: slug
    description: Feature slug (e.g., checkout-discounts)
    required: true
---
```

---

## 9. References

- **Repository**: https://github.com/genkovich/sdd
- **README**: https://github.com/genkovich/sdd/blob/main/README.md
- **CBOL Pipeline**: `../ticket-to-deploy-workflow.md`
- **CBOL Comparison**: `../reference-workflows.md`
- **Arc42**: https://arc42.org/ (architecture documentation template)
- **C4 Model**: https://c4model.com/ (architecture diagrams)
- **TDD**: https://martinfowler.com/bliki/TestDrivenDevelopment.html

---

*Analysis date: 2026-08-21*
*Analyst: CBOL Knowledge Base AI*
