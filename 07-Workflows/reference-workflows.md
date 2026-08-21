# Reference Workflows

> Comparison of AI-driven development workflows from GitHub and industry projects. Each project is analyzed for key features, architecture, and lessons learned for the CBOL pipeline.

## Summary Table

| # | Project | Stars | Language/Tool | Key Feature | Relevance |
|---|---------|-------|---------------|-------------|-----------|
| 1 | [wenttt/ai-coding-workflow](https://github.com/wenttt/ai-coding-workflow) | ~1.2k | MCP Server + Claude | Pull-based pipeline, operation logs, 6 stages | ⭐⭐⭐⭐⭐ Direct inspiration |
| 2 | [genkovich/sdd](https://github.com/genkovich/sdd) | ~800 | Claude Code Skills | Spec-Driven Development, 12 Socratic skills, TDD engine | ⭐⭐⭐⭐⭐ SDD methodology |
| 3 | [stel1os/ai-sdd-sop](https://github.com/stel1os/ai-sdd-sop) | ~500 | Claude Code | SDD + review rejection loop, findings document | ⭐⭐⭐⭐ Review loop pattern |
| 4 | [GradeBuilderSL/partenit-claudev](https://github.com/GradeBuilderSL/partenit-claudev) | ~300 | Claude Code + Jira | Jira task → subtasks → design → code → tests → PR → auto-merge | ⭐⭐⭐⭐ Jira integration |
| 5 | [shgandla/ai-agents](https://github.com/shgandla/ai-agents) | ~200 | Multi-agent | /build /test /review /ship commands, 6 quality gates | ⭐⭐⭐⭐ Command chaining |
| 6 | [grammy-jiang/agentic-dev-template](https://github.com/grammy-jiang/agentic-dev-template) | ~150 | Template | 8-stage workflow with issue templates, Copilot support | ⭐⭐⭐ Template structure |
| 7 | [obra/superpowers](https://github.com/obra/superpowers) | ~27k | Claude Code/OpenCode/Codex | Agentic framework, skills, instincts, memory, security | ⭐⭐⭐⭐ Skill architecture |
| 8 | [TheStack-ai/awesome-claude-code-toolkit](https://github.com/TheStack-ai/awesome-claude-code-toolkit) | ~5k | Claude Code | 8 orchestration agents, workflow director, context manager | ⭐⭐⭐ Orchestration patterns |
| 9 | [GitHub Copilot Coding Agent](https://docs.github.com/en/copilot) | N/A | GitHub Cloud | Assign issue → Copilot implements → PR with tests → human review | ⭐⭐⭐⭐ Industry standard |
| 10 | [OpenAI Codex + Jira](https://developers.openai.com/cookbook/examples/codex/jira-github.md) | N/A | Codex CLI + GitHub Actions | Label Jira issue → GitHub Action → Codex → PR | ⭐⭐⭐⭐ CI/CD integration |

---

## Detailed Analysis

### 1. wenttt/ai-coding-workflow

**Repository**: https://github.com/wenttt/ai-coding-workflow
**Stars**: ~1.2k
**Tool**: MCP Server + Claude Code

**Architecture**:
```
MCP Server (ai-coding-workflow)
  ├── mcp-jira (fetch ticket)
  ├── mcp-design (create design issue)
  ├── mcp-test-write (write tests)
  ├── mcp-test-run (run tests, max 3 retries)
  ├── mcp-deploy (deploy)
  └── mcp-doc-update (update docs)

Pipeline:
  Jira Ticket → Design Issue → Code PR → Test → Deploy → Doc Update
  (pull-based: each stage waits for human to trigger next)
```

**Key Features**:
- **Pull-based pipeline**: Each stage completes and waits for human to trigger next stage (vs push-based auto-progression)
- **Operation logs**: Every stage writes a structured operation log
- **Design as GitHub Issue**: Design document is a GitHub issue, PR body references `Closes #<design-issue>`
- **MCP Server architecture**: Modular MCP tools, can be used by any MCP-compatible agent
- **6 stages**: Jira → Design → Code → Test → Deploy → Docs

**Lessons for CBOL**:
- ✅ **Operation logs**: Adopted — our pipeline writes operation logs at every stage
- ✅ **Pull-based gates**: Adopted — our 6 approval gates are pull-based (wait for human)
- ✅ **Design as artifact**: Adopted — our SDD is a versioned document
- 💡 **MCP Server**: Consider exposing our pipeline as MCP tools for broader agent compatibility
- 💡 **Design issue linking**: PR body should link to SDD document

---

### 2. genkovich/sdd (Spec-Driven Development)

**Repository**: https://github.com/genkovich/sdd
**Stars**: ~800
**Tool**: Claude Code Skills (12 atomic Socratic skills + TDD implement engine)

**Architecture**:
```
12 Skills:
  1. discover     → Understand codebase
  2. spec         → Write spec.md (requirements)
  3. plan         → Create tasks.json (implementation plan)
  4. contract     → Define API contracts
  5. model        → Design data models
  6. migrate      → Database migrations
  7. implement    → TDD engine (RED→GREEN→REFACTOR→commit per task)
  8. review       → Independent clean-context code review
  9. verify       → Run all tests + quality gates
  10. document    → Update docs
  11. release     → Version bump, build, PR, merge, release tag
  12. close       → Close issues

Two modes:
  - agent-team mode: Multiple agents, each runs one skill
  - dynamic-workflow mode: Single agent, skills chain sequentially
```

**Key Features**:
- **Socratic method**: Each skill asks probing questions before acting
- **Spec-first**: spec.md is the source of truth, everything traces back to it
- **tasks.json**: Implementation plan as structured JSON, each task is a TDD cycle
- **Independent review**: Review skill runs in a clean context (no implementation context), reviews against spec/AC + quality
- **Agent-team mode**: Multiple specialized agents, each with one skill
- **TDD engine**: implement skill is a full TDD cycle per task, commits after each

**Lessons for CBOL**:
- ✅ **Spec/SDD first**: Adopted — our SDD is the source of truth
- ✅ **Independent review**: Adopted — our Gate 3 auto-review + Gate 5 peer review
- ✅ **TDD per task**: Adopted — our Stage 3 implements per TDD cycle
- 💡 **Socratic questioning**: Consider adding probing questions at Stage 1 (Requirements) to ensure completeness
- 💡 **tasks.json**: Consider adding a structured implementation plan between SDD and TDD
- 💡 **Agent-team mode**: Consider using multiple specialized agents for large tickets

---

### 3. stel1os/ai-sdd-sop

**Repository**: https://github.com/stel1os/ai-sdd-sop
**Stars**: ~500
**Tool**: Claude Code

**Architecture**:
```
SDD Pipeline:
  Spec → Plan → Implement (TDD) → Review → Release

Review Rejection Loop:
  When reviewer rejects:
    1. Reviewer produces findings document
       - Each finding cites file:line
       - Each finding cites the FR or quality criterion violated
       - Findings (not reviewer's reasoning) are the only artifact passed back
    2. Fresh Developer context receives plan + tests + findings
    3. Developer fixes only cited findings
    4. Re-review
    5. Max 3 rejection loops → escalate
```

**Key Features**:
- **Findings document**: Review output is a structured findings document, not free-form comments
- **File:line citations**: Every finding cites exact file and line number
- **Criterion linkage**: Every finding cites the FR or quality criterion violated
- **Fresh context for fixes**: Developer fixing review feedback gets a fresh context (no bias from original implementation)
- **Findings-only handoff**: Only findings are passed back, not reviewer's reasoning (prevents developer from justifying rather than fixing)

**Lessons for CBOL**:
- ✅ **Max 3 loops**: Adopted — our 3-strike escalation
- 💡 **Findings document**: Consider structuring our Gate 3/Gate 5 review output as findings with file:line + criterion citations
- 💡 **Fresh context for fixes**: Consider using a fresh agent context for fixing review feedback (reduces bias)
- 💡 **Criterion linkage**: Every review finding should trace to a requirement or quality criterion

---

### 4. GradeBuilderSL/partenit-claudev

**Repository**: https://github.com/GradeBuilderSL/partenit-claudev
**Stars**: ~300
**Tool**: Claude Code + Jira Automation

**Architecture**:
```
Jira Automation Trigger:
  Task moved to "In Progress"
    ↓
  Claude Code Pipeline:
    1. Create subtasks (break down task)
    2. System analysis (understand codebase)
    3. Architecture design
    4. Write code
    5. Write tests
    6. Open PR
    ↓
  Human Review
    ↓
  Task moved to "Ready to Merge" → auto-merge
```

**Key Features**:
- **Jira state-driven**: Moving Jira task to "In Progress" triggers the pipeline
- **Auto subtask creation**: Pipeline breaks down the task into subtasks
- **System analysis first**: Before designing, analyze the existing system
- **Auto-merge on approval**: When task moves to "Ready to Merge", PR is auto-merged
- **Step-by-step setup guide**: Detailed setup with screenshots

**Lessons for CBOL**:
- ✅ **Jira integration**: Adopted — our pipeline starts from Jira ticket
- ✅ **System analysis**: Adopted — our Stage 0/1 includes context gathering
- 💡 **Jira state-driven trigger**: Consider using Jira automation to trigger pipeline on state change (vs manual `/workflow` command)
- 💡 **Auto subtask creation**: Consider adding automatic task breakdown between Requirements and SDD
- ⚠️ **Auto-merge**: We do NOT auto-merge — human peer review required (our Gate 5)

---

### 5. shgandla/ai-agents

**Repository**: https://github.com/shgandla/ai-agents
**Stars**: ~200
**Tool**: Multi-agent system

**Architecture**:
```
Commands (each chains to next):
  /build   → TDD slices, atomic commits, code quality scoring
  /test    → 6 quality gates (functional, security, DevOps, DX, observability)
  /review  → 5-axis review (architecture, security, quality, tests, standards)
  /ship    → Pre-flight checks, PR creation, ship report
```

**Key Features**:
- **Command chaining**: `/build` → `/test` → `/review` → `/ship`
- **6 quality gates**: Functional, Security, DevOps, Developer Experience, Observability
- **5-axis review**: Architecture, Security, Quality, Tests, Standards
- **Code quality scoring**: Numeric score for code quality
- **Ship report**: Final report before shipping

**Lessons for CBOL**:
- ✅ **Quality gates**: Adopted — our gates include quality, security, testing
- ✅ **Multi-axis review**: Adopted — our design review checklist covers architecture/API/data/security/reliability/testing
- 💡 **Quality scoring**: Consider adding numeric quality scoring at Gate 3
- 💡 **Ship report**: Consider adding a final ship report at Stage 6

---

### 6. GitHub Copilot Coding Agent (Industry Standard)

**Documentation**: https://docs.github.com/en/copilot/tutorials/roll-out-at-scale/enable-developers/integrate-ai-agents
**Tool**: GitHub Cloud Agent

**Architecture**:
```
1. Create/select Jira issue with enough detail
   - Bug reproduction steps
   - Feature requirements
   - Target repository
   - Expected test coverage

2. Assign issue to Copilot coding agent

3. Copilot analyzes linked repository
   - Reviews relevant files
   - Existing patterns
   - Dependencies
   - Tests

4. Copilot creates implementation
   - Spins up secure cloud workspace (GitHub Actions)
   - Edits code on its own branch
   - Runs tests/linters
   - Opens PR tagging for review

5. Human reviews PR
   - Leaves feedback
   - Copilot incorporates feedback
   - Human merges
```

**Key Features**:
- **Issue-driven**: Assign issue to agent, agent does the work
- **Cloud workspace**: Agent runs in secure GitHub Actions workspace
- **Pre-implementation analysis**: Agent reviews codebase before implementing
- **Test-inclusive**: Agent runs tests and linters before opening PR
- **Human-in-the-loop**: Human reviews and merges, agent never auto-merges
- **Feedback incorporation**: Agent incorporates human review feedback

**Lessons for CBOL**:
- ✅ **Issue-driven**: Adopted — our pipeline starts from Jira ticket
- ✅ **Pre-implementation analysis**: Adopted — our Stage 0/1 gathers context
- ✅ **Test-inclusive**: Adopted — our Stage 3/4 includes testing
- ✅ **Human-in-the-loop**: Adopted — our 6 approval gates
- ✅ **Feedback incorporation**: Adopted — our revision loops
- 💡 **Cloud workspace**: Consider running pipeline in isolated cloud workspace (vs local)
- 💡 **Detailed issue requirements**: Our Stage 0 clarity check ensures ticket has enough detail

---

### 7. obra/superpowers (Skill Architecture)

**Repository**: https://github.com/obra/superpowers
**Stars**: ~27k
**Tool**: Claude Code / OpenCode / Codex / Cursor (multi-platform)

**Architecture**:
```
Superpowers Framework:
  ├── Skills (specialized capabilities)
  ├── Instincts (automatic behaviors)
  ├── Memory (persistent context)
  ├── Security (safety guards)
  └── Plugin system (.opencode/plugin/superpowers.js)

Key concepts:
  - Progressive disclosure: Skills activate only when relevant
  - Agentic framework: Multiple agents coordinate
  - Cross-platform: Works on Claude Code, OpenCode, Codex, Cursor
```

**Key Features**:
- **27k+ stars**: Most popular agentic framework
- **Multi-platform**: Works across multiple AI coding tools
- **Instincts**: Automatic behaviors that trigger without explicit invocation
- **Memory**: Persistent context across sessions
- **Security**: Built-in safety guards
- **Plugin system**: JavaScript plugin for deep integration

**Lessons for CBOL**:
- ✅ **Skill-based architecture**: Adopted — our 06-Skills directory
- ✅ **Progressive disclosure**: Adopted — our knowledge injection is stage-specific
- 💡 **Instincts**: Consider adding automatic behaviors (e.g., auto-read AGENTS.md, auto-check state file)
- 💡 **Memory**: Consider adding persistent memory across pipeline runs (e.g., learned patterns, common mistakes)
- 💡 **Cross-platform**: Our skills are OpenCode-compatible, consider making them Claude Code/Codex compatible too

---

## Comparative Analysis

### Pipeline Stage Comparison

| Stage | CBOL | ai-coding-workflow | sdd (genkovich) | partenit-claudev | Copilot Agent |
|-------|------|-------------------|------------------|------------------|---------------|
| Ticket Intake | ✅ Stage 0 | ✅ mcp-jira | ✅ discover | ✅ (Jira trigger) | ✅ (assign issue) |
| Requirements | ✅ Stage 1 | ❌ (in design) | ✅ spec skill | ❌ (in subtasks) | ✅ (issue detail) |
| Design/SDD | ✅ Stage 2 | ✅ mcp-design | ✅ spec+plan+contract+model | ✅ architecture design | ❌ (implicit) |
| Implementation | ✅ Stage 3 (TDD) | ✅ (PR) | ✅ implement (TDD) | ✅ write code | ✅ (cloud workspace) |
| Testing | ✅ Stage 4 | ✅ mcp-test-write+run | ✅ verify skill | ✅ write tests | ✅ (runs tests) |
| Review | ✅ Gate 3+5 | ❌ (human only) | ✅ review skill | ❌ (human only) | ✅ (human review) |
| PR | ✅ Stage 5 | ✅ (PR) | ✅ release skill | ✅ open PR | ✅ (opens PR) |
| Deploy | ✅ Stage 6 | ✅ mcp-deploy | ✅ release skill | ❌ | ❌ |
| Docs | ✅ Stage 6 | ✅ mcp-doc-update | ✅ document skill | ❌ | ❌ |

### Key Pattern Adoption

| Pattern | Source | CBOL Status |
|---------|--------|-------------|
| Operation logs | ai-coding-workflow | ✅ Adopted |
| Pull-based approval gates | ai-coding-workflow | ✅ Adopted (6 gates) |
| SDD/spec first | genkovich/sdd | ✅ Adopted |
| TDD per task | genkovich/sdd | ✅ Adopted |
| Independent review | genkovich/sdd | ✅ Adopted (Gate 3 auto-review) |
| Findings document | stel1os/ai-sdd-sop | 💡 Consider |
| 3-strike escalation | Multiple | ✅ Adopted |
| Jira state-driven trigger | partenit-claudev | 💡 Consider |
| Quality gates | shgandla/ai-agents | ✅ Adopted |
| Multi-axis review | shgandla/ai-agents | ✅ Adopted |
| Cloud workspace | Copilot Agent | 💡 Consider |
| Skill-based architecture | superpowers | ✅ Adopted (06-Skills) |
| Progressive disclosure | superpowers | ✅ Adopted (stage-specific KB injection) |
| Fresh context for review fixes | stel1os/ai-sdd-sop | 💡 Consider |

---

## Gaps & Opportunities

### What CBOL Has That Others Don't

1. **Knowledge base injection**: Every stage reads relevant KB docs — most other workflows don't have a structured knowledge base
2. **State persistence with JSON**: Detailed state file with per-stage status, version, evidence, retry counts
3. **Anti-drift checks**: Pre-stage verification that previous stages are complete and approved
4. **Custom lightweight state machine**: Domain-specific state machine for conversation flows
5. **Cross-project support**: Multi-repo coordination with contract-first design
6. **Project-specific standards**: Self-Development internal standards integrated into pipeline

### What CBOL Could Learn From Others

1. **MCP Server exposure** (ai-coding-workflow): Expose pipeline stages as MCP tools for broader agent compatibility
2. **Socratic questioning** (genkovich/sdd): Add probing questions at Requirements stage to ensure completeness
3. **Structured tasks.json** (genkovich/sdd): Add a structured implementation plan between SDD and TDD
4. **Findings document format** (stel1os/ai-sdd-sop): Structure review output as findings with file:line + criterion citations
5. **Fresh context for review fixes** (stel1os/ai-sdd-sop): Use fresh agent context for fixing review feedback
6. **Jira state-driven trigger** (partenit-claudev): Trigger pipeline automatically on Jira state change
7. **Quality scoring** (shgandla/ai-agents): Add numeric quality scoring at Gate 3
8. **Cloud workspace** (Copilot Agent): Run pipeline in isolated cloud workspace
9. **Instincts/memory** (superpowers): Add automatic behaviors and persistent memory
10. **Multi-platform skills** (superpowers): Make skills compatible with Claude Code, Codex, Cursor

---

## References

- [wenttt/ai-coding-workflow](https://github.com/wenttt/ai-coding-workflow) — MCP Server, pull-based pipeline, operation logs
- [genkovich/sdd](https://github.com/genkovich/sdd) — Spec-Driven Development, 12 Socratic skills, TDD engine
- [stel1os/ai-sdd-sop](https://github.com/stel1os/ai-sdd-sop) — SDD + review rejection loop, findings document
- [GradeBuilderSL/partenit-claudev](https://github.com/GradeBuilderSL/partenit-claudev) — Jira-driven, auto subtasks, auto-merge
- [shgandla/ai-agents](https://github.com/shgandla/ai-agents) — Multi-agent, /build /test /review /ship, 6 quality gates
- [grammy-jiang/agentic-dev-template](https://github.com/grammy-jiang/agentic-dev-template) — 8-stage template, issue templates
- [obra/superpowers](https://github.com/obra/superpowers) — 27k stars, agentic framework, multi-platform
- [TheStack-ai/awesome-claude-code-toolkit](https://github.com/TheStack-ai/awesome-claude-code-toolkit) — 8 orchestration agents
- [GitHub Copilot Coding Agent](https://docs.github.com/en/copilot/tutorials/roll-out-at-scale/enable-developers/integrate-ai-agents) — Industry standard, cloud workspace
- [OpenAI Codex + Jira](https://developers.openai.com/cookbook/examples/codex/jira-github.md) — Codex CLI + GitHub Actions
- [I built an AI pipeline (Pavel Terenin)](https://pavelterenin.com/blog/2026/04/26/i-built-an-ai-pipeline-that-turns-jira-tickets-into-reviewed-tested-pull-requests-heres-how-it-actually-works/) — Self-hosted worker, draft PR, second-agent review
- [From Issue to Deploy (youngju.dev)](https://www.youngju.dev/blog/culture/2026-05-14-issue-to-deploy-autonomous-pipeline-ai-code-review-ci-cd-auto-verify-rollback-deep-dive-guide-2025.en) — 7-stage pipeline, AI review, CI/CD verify, auto-rollback

---

*Last updated: 2026-08-21*
*Projects analyzed: 10*
