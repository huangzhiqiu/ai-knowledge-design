# External Skills Collection

> Downloaded, ready-to-use OpenCode/Claude Code skills from GitHub. Organized by category for the CBOL (AI Messaging Hub) project.

## Overview

This directory contains **91 executable skills** (265 files) downloaded from 11 GitHub repositories. Each skill has a `SKILL.md` file with YAML frontmatter and can be directly used in OpenCode/Claude Code.

**Collection date**: 2026-08-24
**Total skills**: 91
**Total files**: 265
**Source repositories**: 11

## Source Repositories

| # | Repository | Skills | Description |
|---|-----------|--------|-------------|
| 1 | [illarion/claude-jira-skill](https://github.com/illarion/claude-jira-skill) | 1 | Full-featured Jira skill with MCP/REST, ADF handling, multi-instance |
| 2 | [Lumyk/jira-planner-skill](https://github.com/Lumyk/jira-planner-skill) | 1 | Jira Planner — single entry point for Jira-Cloud ops, MCP-default, config-driven, tenant-data hygiene |
| 3 | [or-ituran/claude-tdd-skill](https://github.com/or-ituran/claude-tdd-skill) | 1 + 10 agents | Interactive TDD with sub-agents, progress persistence, checkpoints |
| 4 | [gthimmes/code-reviewer](https://github.com/gthimmes/code-reviewer) | 1 | 5-axis code review with find-then-verify, confidence scoring |
| 5 | [Kevinweisl/claude-skills-cicd](https://github.com/Kevinweisl/claude-skills-cicd) | 4 | build-and-release, dependency-audit, lint-and-test, security-scan |
| 6 | [genkovich/sdd](https://github.com/genkovich/sdd) | 22 | Atomic SDD skills: specify, clarify, design, data-model, implement, etc. |
| 7 | [excalibase/claude-toolkiit](https://github.com/excalibase/claude-toolkiit) | 44 | Java/Spring Boot patterns, TDD, coding standards, security, deployment, etc. |
| 8 | [adamcaviness/agentic-toolkit](https://github.com/adamcaviness/agentic-toolkit) | 13 | create-ticket, next-ticket, code-review, pr, ship, triage-* skills |
| 9 | [Synafast/prd-taskmaster](https://github.com/Synafast/prd-taskmaster) | 1 | Smart PRD generator with TaskMaster integration, 13 validation checks, autonomous execution |
| 10 | [SpillwaveSolutions/sdd-skill](https://github.com/SpillwaveSolutions/sdd-skill) | 1 | Spec-Driven Development (SDD) v2.1.0 — GitHub Spec-Kit methodology, greenfield/brownfield, feature status tracking |
| 11 | [kborovik/opencode-skills](https://github.com/kborovik/opencode-skills) | 1 | spec mutator — sole semantic author of SPEC.md, NEW/DISTILL/BACKPROP/AMEND/FOLD-IN modes |
| 12 | [jamesrochabrun/skills](https://github.com/jamesrochabrun/skills) | 1 | PRD Generator — comprehensive PRDs with user stories, metrics frameworks, 4 usage patterns |
| 13 | [fancybread-com/sdlc-workflow-skills](https://github.com/fancybread-com/sdlc-workflow-skills) | 1 | complete-task — commit, push, PR creation, Constitutional Review gate, issue transition |

## Categories

### 01-jira (2 skills)

| Skill | Source | Description |
|-------|--------|-------------|
| `claude-jira-skill/` | illarion | Fetch/search/create Jira tickets, MCP+REST dual-path, ADF formatting, transitions, assignments, linking, team digests |
| `jira-planner/` | Lumyk | Single entry point for Jira-Cloud ops, MCP-default/acli-opportunistic, config-driven, tenant-data hygiene, new-task flow with 13 steps |

### 02-requirements-sdd (26 skills)

**genkovich/sdd (22 atomic skills)**:
`specify`, `clarify`, `classify-size`, `data-model`, `decide-adr`, `design`, `design-system`, `fix`, `glossary`, `implement`, `interview`, `plan-tests`, `review`, `roadmap`, `scaffold`, `screens`, `sequences`, `ship`, `survey`, `tasks`, `ux-flows`, `_shared`

**Additional PRD/SDD skills (4)**:

| Skill | Source | Description |
|-------|--------|-------------|
| `prd-taskmaster/` | Synafast | Smart PRD generator with TaskMaster integration, 12-step workflow, 13 validation checks, 4 autonomous execution modes |
| `sdd-spec-driven/` | SpillwaveSolutions | Spec-Driven Development v2.1.0 — GitHub Spec-Kit, executable specs, greenfield/brownfield, 10-point summaries, feature status tracking |
| `spec-mutator/` | kborovik | Sole semantic author of SPEC.md — NEW/DISTILL/BACKPROP/AMEND/FOLD-IN modes, audit gates, write-time prune, monotonic IDs |
| `prd-generator/` | jamesrochabrun | Comprehensive PRD generator — 13-section template, user stories with acceptance criteria, AARRR/HEART/OKR metrics, 4 usage patterns, self-review checklist |

### 03-coding (22 skills)

**excalibase/claude-toolkiit**:
`java-coding-standards`, `springboot-patterns`, `springboot-security`, `springboot-verification`, `jpa-patterns`, `backend-patterns`, `coding-standards`, `refactor-clean`, `build-fix`, `db-researcher`, `deep-research`, `search-first`, `write-plan`, `self-check`, `self-review`, `review-plan`, `project-review`, `codebase-onboarding`, `continuous-learning`, `strategic-compact`, `summary`, `checkpoint`

### 04-testing-tdd (6 skills)

| Skill | Source | Description |
|-------|--------|-------------|
| `claude-tdd-skill/` | or-ituran | Full interactive TDD with 10 sub-agents, progress persistence, checkpoints |
| `excalibase-tdd/` | excalibase | TDD workflow skill |
| `excalibase-springboot-tdd/` | excalibase | Spring Boot TDD patterns |
| `excalibase-integration-testing/` | excalibase | API/server E2E testing |
| `excalibase-ui-testing/` | excalibase | Playwright browser E2E testing |

### 05-devops-cicd (8 skills)

**Kevinweisl/claude-skills-cicd**:
`build-and-release` (dry-run default), `dependency-audit` (CVE scan), `lint-and-test`, `security-scan`

**excalibase/claude-toolkiit**:
`deployment-patterns`, `docker-patterns`, `database-migrations`

**fancybread-com/sdlc-workflow-skills**:
`complete-task` — commit, push, PR creation with Constitutional Review gate, issue transition to Code Review

### 06-code-review (5 skills)

| Skill | Source | Description |
|-------|--------|-------------|
| `gthimmes-code-reviewer/` | gthimmes | 5-axis review (correctness/design/security/performance/tests), find-then-verify, confidence scoring |
| `excalibase-code-review/` | excalibase | Code review skill |
| `excalibase-security-review/` | excalibase | Security review |
| `excalibase-architecture-review/` | excalibase | Architecture review |
| `excalibase-quality-gate/` | excalibase | Lint, type-check, format checks |

### 07-productivity (24 skills)

**adamcaviness/agentic-toolkit (13)**:
`create-ticket`, `next-ticket`, `code-review`, `pr`, `ship`, `triage-architecture`, `triage-bugs`, `triage-product`, `apply-review`, `get-it-right`, `update-deps`, `compress-markdown`, `convert-worktree`

**excalibase/claude-toolkiit (11)**:
`api-design`, `architecture-decision-records`, `frontend-patterns`, `react-best-practices`, `golang-patterns`, `mongodb-patterns`, `mysql-patterns`, `postgres-patterns`, `golang-testing`, `skill-create`

## How to Use

### Option 1: Symlink to OpenCode skills directory

```bash
# Linux/macOS
ln -s /path/to/06-Skills/05-external-skills/01-jira/claude-jira-skill ~/.config/opencode/skills/claude-jira-skill

# Windows (PowerShell, admin)
New-Item -ItemType SymbolicLink -Path "$env:USERPROFILE\.config\opencode\skills\claude-jira-skill" -Target "C:\path\to\06-Skills\05-external-skills\01-jira\claude-jira-skill"
```

### Option 2: Copy to project skills directory

```bash
# Copy specific skill to project .opencode/skills/
cp -r 06-Skills/05-external-skills/04-testing-tdd/claude-tdd-skill .opencode/skills/
```

### Option 3: Reference in AGENTS.md

Add to project `AGENTS.md`:
```markdown
## External Skills
- Jira: `06-Skills/05-external-skills/01-jira/claude-jira-skill/SKILL.md`
- TDD: `06-Skills/05-external-skills/04-testing-tdd/claude-tdd-skill/SKILL.md`
- Code Review: `06-Skills/05-external-skills/06-code-review/gthimmes-code-reviewer/SKILL.md`
```

## CBOL Project Mapping

| CBOL Workflow Stage | Recommended Skills |
|---------------------|-------------------|
| Ticket intake | `claude-jira-skill`, `jira-planner`, `agentic-toolkit/create-ticket` |
| Requirements | `genkovich-sdd/specify`, `genkovich-sdd/clarify`, `genkovich-sdd/interview`, `prd-taskmaster`, `prd-generator` |
| SDD/Design | `genkovich-sdd/design`, `genkovich-sdd/data-model`, `genkovich-sdd/sequences`, `genkovich-sdd/decide-adr`, `sdd-spec-driven`, `spec-mutator`, `excalibase-api-design` |
| Test cases (TDD RED) | `claude-tdd-skill`, `excalibase-tdd`, `excalibase-springboot-tdd`, `genkovich-sdd/plan-tests` |
| Code generation (TDD GREEN) | `genkovich-sdd/implement`, `excalibase-java-coding-standards`, `excalibase-springboot-patterns`, `excalibase-jpa-patterns` |
| Code review | `gthimmes-code-reviewer`, `excalibase-code-review`, `excalibase-security-review`, `excalibase-architecture-review`, `agentic-toolkit/code-review` |
| Quality gate | `excalibase-quality-gate`, `claude-skills-cicd/lint-and-test`, `claude-skills-cicd/security-scan` |
| PR creation | `agentic-toolkit/pr`, `agentic-toolkit/apply-review`, `complete-task` |
| Deployment | `claude-skills-cicd/build-and-release`, `excalibase-deployment-patterns`, `excalibase-docker-patterns`, `agentic-toolkit/ship`, `complete-task` |
| Dependency management | `claude-skills-cicd/dependency-audit`, `agentic-toolkit/update-deps` |
| Triage/Bug fixing | `agentic-toolkit/triage-bugs`, `agentic-toolkit/triage-architecture`, `genkovich-sdd/fix`, `excalibase-build-fix`, `spec-mutator` (BACKPROP mode) |
| Knowledge/Research | `excalibase-deep-research`, `excalibase-search-first`, `excalibase-db-researcher`, `excalibase-codebase-onboarding` |

## Notes

- All skills are original works from their respective repositories. See each skill's `SKILL.md` for license information.
- Some skills may require additional setup (MCP servers, API tokens, etc.) — see individual skill documentation.
- The `claude-tdd-skill` includes 10 sub-agent definition files in its `agents/` directory.
- The `genkovich-sdd` skills share a `_shared` directory with common templates and references.

---

*External Skills Collection v1.1.0 — 2026-08-24*
*91 skills from 13 GitHub repositories, organized for CBOL (AI Messaging Hub) project*
*New in v1.1.0: +6 skills (jira-planner, prd-taskmaster, sdd-spec-driven, spec-mutator, prd-generator, complete-task) from 6 new repos*
