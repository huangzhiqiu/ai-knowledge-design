# AI-Driven Development Reference Projects

> Reference projects and products for Jira-driven AI development workflows (Ticket → SDD → Code → Test → Deploy).

---

## Open Source Projects

### 1. Forge

| Item | Detail |
|------|--------|
| **GitHub** | https://github.com/forge-sdlc/forge |
| **Language** | Python (LangGraph + FastAPI + Redis Streams + Podman) |
| **Stars** | Active (553+ commits) |
| **License** | Open source |

**Core workflow:**
```
Jira ticket → Human-gated plan → Repo-scoped implementation → GitHub PRs → CI repair → Human review → Summary + dashboards
```

**Key design patterns:**
- **Approval gates**: Pause before major planning transitions; reviewers can approve, request revisions, or ask questions
- **Controlled write boundaries**: Agents only write inside local/container workspace; Forge's integration layer performs Jira/GitHub mutations at explicit workflow steps
- **Containerized implementation**: Code work happens in ephemeral Podman containers with repo access scoped to the task
- **CI repair loop**: Analyze failing checks, apply fixes, push updates, retry until ready for review or blocked with clear reason
- **Cross-repo by design**: Plan features/bugs across services, clients, infrastructure, and documentation repos, then split into repo-scoped units
- **Skills system**: Markdown instruction files customize how Forge writes plans, implements code, reasons about CI, and follows team conventions
- **Observability**: Prometheus metrics, Langfuse traces, Grafana dashboards for workflow throughput, step latency, ticket execution cost, model usage

**Three workflow types:**
- **Feature**: Ticket → PRD → Behavioral Spec → Cross-repo Epics → Repo-scoped Tasks → Implementation → PRs → CI → Review
- **Bug**: Ticket → Triage → Root Cause Analysis → Fix Options → Plan Approval → Implementation → PRs → Post-merge Summary
- **Task**: Ticket → Triage → Implementation Plan → Plan Approval → Implementation → Qualitative Review → PRs → CI → Review

**Jira-native controls:**
- `forge:managed` label to start
- `forge:plan-approved` label to approve plan
- `!` comments for revisions
- `?` comments for Q&A
- `forge:retry` for blocked-stage recovery

**Architecture:**
```
Jira + GitHub Webhooks → FastAPI Gateway → Redis Streams Queue → LangGraph Workflow
→ Host Orchestrator Agent → Container Agent for Implementation → Jira + GitHub Updates
```

---

### 2. Jira-Flow

| Item | Detail |
|------|--------|
| **GitHub** | https://github.com/jinx911/jira-flow |
| **Language** | Claude Code CLI + MCP |
| **License** | Open source |

**Core workflow (6 Phases + 6 Gates):**
```
Jira Issue → P1:需求分析 → P2:任务规划 → P3:TDD开发 → P4:代码评审 → P5:测试验证 → P6:收尾提测
```

**Key design patterns:**
- **Hub-and-Spoke architecture**: Leader (main Claude session) never writes code or runs Bash, only coordinates/decides/routes. All member agents report to Leader, no peer-to-peer communication.
- **On-demand scaling**: Agents spawned only when needed, destroyed after work:
  - Phase 0-1: Core think tank (Claude Opus) — requirements-analyst, architect, planner
  - Phase 2-3: Execution team (Sonnet) — backend-developer, frontend-developer
  - Phase 4-5: QA experts (Sonnet) — code-reviewer, tester
- **TDD discipline**: RED (write failing test first) → GREEN (minimal code to pass) → REFACTOR → Commit. Prohibits writing production code before a failing test.
- **Evidence discipline**: Any completion claim must be backed by `command + output summary + exit code`. No evidence = not accepted.
- **Circuit breaker**: If bug fix loop fails 3 times, Leader automatically escalates to human, never infinite loop.
- **Breakpoint recovery**: Full state persistence after each Gate. Process interrupted? Re-run command, Leader auto-loads state and resumes from breakpoint.
- **Baseline conflict check**: Auto-scans local project's `openspec/specs` historical baseline docs; if new plan conflicts, immediately blocks and reports.
- **Task granularity**: Each task in generated `tasks.md` is strictly 2-5 minutes of work.

**Three-layer config decoupling:**
| Layer | Location | Purpose |
|-------|----------|---------|
| Global index | `~/.claude/configs/projects.json` | Path mapping only |
| Flow config | `jira-flow/project-config.md` | Jira cloudId, branch naming, artifact paths |
| Project local config | `<project-root>/.claude/project-config.md` | DB connections, build commands, Playwright login scripts |

---

### 3. AI Coding Workflow MCP Server

| Item | Detail |
|------|--------|
| **GitHub** | https://github.com/wenttt/ai-coding-workflow |
| **Language** | Python (MCP Server) |
| **License** | Open source |

**Core philosophy:**
> "This server provides the data plumbing and orchestration. The IDE Copilot does the LLM work. Skills do the procedural work. You and your team do the judgment."

**6-stage pipeline:**
```
Jira ticket
↓
[Stage 1: Design] mcp-design-brownfield / mcp-design-greenfield
↓ (publishes a GitHub Issue with the design markdown)
[Human review on GitHub Issue]
↓ Issue closed `completed` (or comments → mcp-design-revise → loop, max 3)
[Stage 2: Implementation] mcp-implement-{backend,frontend,db}
↓ (creates feat/{KEY}-* branch with code)
[Stage 3: Self review] mcp-self-review
↓
[Stage 4: Test] mcp-test-write → mcp-test-run (max 3 retries)
↓
[Code PR opened] (PR body has `Closes #<design-issue>`)
↓ human review (or rejected → loop, max 3)
[Stage 5: Deploy] mcp-deploy
↓
[Stage 6: Doc update] mcp-doc-update
↓
[Jira closed]
```

**Key design decisions:**
- **Design in Issues, code in PRs**: Design discussion lives in GitHub Issues (lightweight, no branches), code lives in PRs (branch isolation for parallel team development)
- **Pull-based, not webhook-driven**: Agent reads GitHub state when invoked, not the other way around
- **One step per invocation**: Human stays in the loop. No long-running daemons.
- **Operation logs are first-class**: Every step writes `docs/operations/{KEY}/{NN}-{stage}-v{N}.md` with: did / impact / could-not / engineering-decisions / next
- **Rejection loops are main flow, not error handling**
- **3-strike escalation**: Auto-retry 3 times, then escalate to human
- **Skills are bash-free**: All shell operations go through MCP tools (which run in this server's process)
- **Brownfield + Greenfield are first-class branches**: Stage 1 detects mode and dispatches
- **Skill mapping is configurable**: Each team can fork `resources/skill_mapping.yaml`

**Multi-project + cross-project support:**
- Multiple Jira projects, each backed by a different GitHub repo
- `list_my_tickets()` returns tickets across ALL Jira projects
- Cross-project tickets (frontend + backend) handled with **contract-first design**: explicit Contract section (OpenAPI / Protobuf / GraphQL), both sides implement against contract

**Project structure:**
```
ai-coding-workflow/
├── src/ai_coding_workflow/
│   ├── server.py          # MCP server entry point
│   ├── config.py          # Environment + paths
│   ├── state/             # Operation logs, retry tracking, frontmatter
│   ├── tools/             # MCP tools (Jira, GitHub, repo, git, tests, etc.)
│   └── resources/
│       ├── skill_mapping.yaml      # stage → skill routing (forkable)
│       └── templates/              # Design doc templates (brownfield + greenfield)
├── .claude/skills/        # MCP-aware custom skills (bash-free)
├── templates/.github/copilot-instructions.md  # Drop into team repo
├── docs/                  # Architecture + protocol docs
└── tests/
```

---

## Commercial Products

### 4. Devin (Cognition AI)

| Item | Detail |
|------|--------|
| **Website** | https://devin.ai |
| **Type** | Autonomous AI software engineer |
| **Jira integration** | Yes — add "Devin" label to Jira issue, Devin auto-detects, posts analysis comment + plan, waits for approval |

**Workflow:**
1. Assign Jira ticket to Devin (or add "Devin" label)
2. Devin reads ticket, investigates codebase, posts analysis comment + implementation plan + questions
3. Human approves or provides feedback
4. Devin plans → codes in cloud IDE → tests → debugs failures → opens PR
5. Watch entire process in real time through Devin's UI

**Key features:**
- **Knowledge feature**: Feed Devin proprietary documentation, coding standards, architectural guidelines so it produces code matching team conventions from first attempt
- **Devin API**: Programmatically assign tasks and retrieve results for custom automation pipelines
- **Self-healing debugging**: Analyzes test failures, fixes, retries
- **Session Insights**: Analyzes Devin sessions, breaks down what happened

---

### 5. GitHub Copilot for Jira

| Item | Detail |
|------|--------|
| **Type** | GitHub + Atlassian integration |
| **GA date** | June 2026 |

**Workflow:**
1. Agent reads ticket, linked Confluence pages, repository context
2. Opens draft pull request, streams progress back into Jira
3. Leave follow-up instructions in Jira chat panel — agent updates same PR
4. Peer reviewer (not ticket creator) approves final PR (enforced)

**Key constraint:** Ticket creator cannot be final approver. Peer review required.

---

### 6. Atlassian Rovo Dev

| Item | Detail |
|------|--------|
| **Type** | Jira-native AI coding agent |
| **Entry point** | Jira work item |

**Workflow:**
1. In Jira work item, select repo, supplement prompt or saved prompt, configure branch + environment
2. Choose whether to let Rovo Dev generate draft PR
3. Rovo Dev reads/writes selected repo, runs Bash/PowerShell in secure cloud sandbox
4. Never directly merges PR — human review required

---

## Cross-Project Design Pattern Comparison

| Pattern | Forge | Jira-Flow | ai-coding-workflow | Devin |
|---------|-------|-----------|-------------------|-------|
| **Approval gates** | ✅ Explicit gates at planning transitions | ✅ 6 Gates between 6 Phases | ✅ GitHub Issue review + PR review | ✅ Plan approval before implementation |
| **Evidence discipline** | ✅ CI repair loop with logs | ✅ Command+output+exit code required | ✅ Operation logs first-class | ✅ Session Insights |
| **3-strike escalation** | ✅ Blocked states with clear reason | ✅ Circuit breaker after 3 failures | ✅ Max 3 retries then escalate | ✅ Self-healing with limits |
| **TDD enforcement** | ❌ Not explicit | ✅ RED→GREEN→REFACTOR→Commit | ❌ Test as separate stage | ❌ Not explicit |
| **Container isolation** | ✅ Podman containers | ❌ Local worktree | ❌ Local repo | ✅ Cloud IDE sandbox |
| **Controlled writes** | ✅ Agents only write locally, integration layer does external | ✅ Leader never writes, only members | ✅ MCP tools handle all shell ops | ✅ Secure cloud sandbox |
| **Pull-based** | ❌ Webhook-driven (event-driven) | ❌ CLI invocation | ✅ Pull-based, one step per invocation | ❌ Async agent |
| **Cross-repo** | ✅ First-class | ❌ Single project | ✅ Contract-first design | ✅ Multi-repo |
| **Skills customization** | ✅ Markdown skills | ✅ Config + skills | ✅ skill_mapping.yaml forkable | ✅ Knowledge feature |
| **Observability** | ✅ Prometheus+Langfuse+Grafana | ✅ State persistence | ✅ Operation logs | ✅ Session Insights |

---

## Recommended Best Practices (Synthesized)

Based on analysis of all projects, these are the non-negotiable design patterns for a Jira-driven AI development workflow:

### 1. Human-in-the-loop at every critical transition
- AI proposes, human approves. Never let AI auto-merge.
- Use lightweight review mechanisms (GitHub Issues for design, PRs for code).

### 2. Evidence-based completion
- No "I think it works" — require command output + exit code.
- Every stage writes a structured operation log.

### 3. Bounded autonomy with escalation
- Auto-retry up to 3 times, then escalate to human.
- Never infinite loop. Never silent failure.

### 4. Controlled write boundaries
- AI agents work in isolated workspaces/containers.
- External mutations (Jira/GitHub/deploy) happen at explicit workflow steps, not by agents directly.

### 5. Design before code
- Generate SDD/design doc, get it reviewed, THEN implement.
- Design discussion in Issues (no branches), code in PRs (branch isolation).

### 6. Test discipline
- TDD or at minimum: write tests before declaring completion.
- Test failures trigger fix loop, not "skip and move on".

### 7. Knowledge injection
- Inject team coding standards, architecture guidelines, domain knowledge at every stage.
- Don't let AI work from generic knowledge only.

### 8. Resumability
- Persist state after each gate. Resume from breakpoint, not from start.

---

## References

- Forge: https://github.com/forge-sdlc/forge
- Jira-Flow: https://github.com/jinx911/jira-flow
- AI Coding Workflow MCP Server: https://github.com/wenttt/ai-coding-workflow
- Devin: https://devin.ai
- GitHub Copilot for Jira: https://byteiota.com/github-copilot-for-jira-is-ga-assign-a-ticket-get-a-pr/
- Atlassian Rovo Dev: https://blog.csdn.net/Loli_Wolf/article/details/161371328
- Atlassian Jira Automation for AI agents: https://www.atlassian.com/blog/development/scale-agent-impact-with-jira-automation
