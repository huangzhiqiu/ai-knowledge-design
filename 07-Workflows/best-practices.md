# AI-Driven Development Best Practices

> Consolidated best practices for AI-driven software development, derived from industry projects, research, and the CBOL pipeline experience. Covers pipeline design, human-in-the-loop, evidence-based completion, anti-drift, knowledge injection, and tooling recommendations.

## 1. Pipeline Design

### 1.1 Stage-Based Pipeline with Approval Gates

**Principle**: Break development into discrete stages, each with a clear output and a human approval gate before proceeding.

```
✅ Good: Stage → Output → Gate (human approval) → Next Stage
❌ Bad: AI does everything end-to-end without human checkpoints
```

**Why**:
- Catches errors early (design errors are cheaper to fix than code errors)
- Provides human oversight at critical decision points
- Allows resumption from any stage (state persistence)
- Makes progress visible and measurable

**CBOL Implementation**: 7 stages, 6 approval gates (see `ticket-to-deploy-workflow.md`)

**Reference**: ai-coding-workflow (pull-based pipeline), genkovich/sdd (12 skills with handoffs)

### 1.2 Design Before Code

**Principle**: Never write production code before a design document is approved.

```
✅ Good: Requirements → SDD (approved) → TDD Implementation
❌ Bad: Jump straight to coding from ticket description
```

**Why**:
- Design is cheaper to change than code
- Ensures all stakeholders agree on approach before implementation
- Prevents AI from "solutioning" without understanding the problem
- Creates documentation for future maintenance

**CBOL Implementation**: Stage 2 (SDD) must pass Gate 2 before Stage 3 (TDD) begins

**Reference**: genkovich/sdd (spec-first), stel1os/ai-sdd-sop (SDD pipeline)

### 1.3 Test-Driven Development (TDD)

**Principle**: Write a failing test first, then write minimal code to pass, then refactor.

```
RED → GREEN → REFACTOR → Commit
(write failing test) → (make it pass) → (clean up) → (commit)
```

**Why**:
- Ensures code is testable by design
- Provides immediate feedback on implementation
- Creates regression test suite
- Prevents over-engineering (write minimal code to pass test)
- AI benefits from clear test definitions (objective success criteria)

**CBOL Implementation**: Stage 3 implements per TDD cycle, max 50 cycles per ticket

**Reference**: genkovich/sdd (implement skill is TDD engine), jira-flow (TDD with evidence)

### 1.4 Evidence-Based Completion

**Principle**: Every completion claim must be backed by command + output + exit code.

```
✅ Good: "Tests pass" + `mvn test` output + "BUILD SUCCESS" + exit code 0
❌ Bad: "Tests pass" (no evidence)
```

**Why**:
- AI can hallucinate completion — evidence verifies actual state
- Creates audit trail for debugging
- Enables objective gate evaluation
- Prevents "it works on my machine" syndrome

**CBOL Implementation**: Every stage writes operation log with Evidence section (command + output + exit code)

**Reference**: jira-flow (evidence requirement), ai-coding-workflow (operation logs)

### 1.5 3-Strike Escalation

**Principle**: Auto-retry up to 3 times, then stop and ask human. Never infinite loop.

```
Attempt 1 → Fail → Retry
Attempt 2 → Fail → Retry
Attempt 3 → Fail → ESCALATE (stop, post to Jira, wait for human)
```

**Why**:
- Prevents infinite loops and resource exhaustion
- Some failures need human judgment (not more retries)
- Creates clear escalation path
- Limits wasted compute on unfixable issues

**CBOL Implementation**: Max 3 revision loops at every gate, max 3 test retries, max 3 CI repair loops

**Reference**: ai-coding-workflow (max 3 test retries), stel1os/ai-sdd-sop (max 3 rejection loops)

---

## 2. Human-in-the-Loop

### 2.1 Human Approval at Critical Gates

**Principle**: Humans must approve critical decisions — design, requirements, test results, PR merge.

```
Critical gates requiring human approval:
  - Gate 1: Requirements (is this what we want?)
  - Gate 2: SDD (is this the right approach?)
  - Gate 4: Test results (is quality acceptable?)
  - Gate 5: PR merge (is code ready for production?)
```

**Why**:
- AI lacks business context and judgment
- Humans understand organizational constraints
- Accountability — humans own production decisions
- Prevents AI from making irreversible changes without oversight

**CBOL Implementation**: 6 approval gates, all require human approval except Gate 3 (auto-review)

**Reference**: GitHub Copilot (human reviews and merges), all reference projects

### 2.2 No Self-Approval

**Principle**: The person (or agent) who created the work cannot approve it.

```
✅ Good: AI implements → Human reviews → Human approves
❌ Bad: AI implements → AI reviews → AI approves (no human in loop)
```

**Why**:
- Independent review catches blind spots
- Prevents confirmation bias
- Ensures accountability
- AI reviewing its own code may miss issues

**CBOL Implementation**: `enforce_no_self_approval: true` in config, ticket creator cannot approve PR

**Reference**: genkovich/sdd (independent review skill in clean context), stel1os/ai-sdd-sop (fresh developer context for fixes)

### 2.3 Independent Review Context

**Principle**: Review should be done in a fresh context, without the implementation's reasoning bias.

```
✅ Good: Reviewer gets only spec + code (not implementation reasoning)
❌ Bad: Reviewer gets spec + code + implementation reasoning + justifications
```

**Why**:
- Prevents reviewer from being biased by implementation reasoning
- Focuses review on objective criteria (spec, quality, security)
- Catches issues that implementer missed
- Findings (not reasoning) are passed back for fixes

**CBOL Implementation**: Gate 3 auto-review checks against objective criteria (tests pass, coverage, Sonar issues). Gate 5 peer review is human.

**Reference**: stel1os/ai-sdd-sop (findings-only handoff, fresh developer context), genkovich/sdd (review skill in clean context)

### 2.4 Clear Escalation Path

**Principle**: When AI gets stuck, it must stop and escalate to human with clear context.

```
Escalation message must include:
  - What stage failed
  - What was tried (3 attempts)
  - Root cause (best guess)
  - Recommended next steps
  - State file location
  - Logs location
```

**Why**:
- Humans need context to help
- Prevents AI from silently failing or looping
- Creates clear handoff point
- Enables efficient human intervention

**CBOL Implementation**: Escalation template posts to Jira with all required context

**Reference**: All mature pipelines have escalation paths

---

## 3. Anti-Drift & State Management

### 3.1 State Persistence

**Principle**: After every gate, write a state file. Resume from breakpoint, never from start.

```
State file: .ai-workflow/state/{JIRA-KEY}.json
  - current_stage
  - stages: { status, version, evidence }
  - gates: { status, approved_at, revision_count }
  - retry_counts
  - created_at, updated_at
```

**Why**:
- Pipeline may be interrupted (network, timeout, human pause)
- Avoids redoing completed work
- Provides visibility into progress
- Enables debugging (what stage, what version, how many retries)

**CBOL Implementation**: JSON state file, written after every gate

**Reference**: ai-coding-workflow (state management), jira-flow (circuit breaker state)

### 3.2 Pre-Stage Anti-Drift Checks

**Principle**: Before starting any stage, verify previous stages are complete and approved.

```
Checks before every stage:
  - Previous stage status = completed
  - Previous stage evidence exists
  - Previous gate status = passed
  - Current ticket matches state file
  - Retry count < 3
  - SDD is approved version (if Stage 3+)
  - No out-of-scope changes in working tree
```

**Why**:
- Prevents building on incomplete/unapproved foundation
- Catches state corruption early
- Ensures scope discipline (no out-of-scope changes)
- Prevents version drift (using outdated SDD)

**CBOL Implementation**: Anti-drift checks run before every stage

**Reference**: CBOL-specific (derived from pipeline experience)

### 3.3 Versioned Artifacts

**Principle**: Every stage output is versioned. Revisions create new versions, don't overwrite.

```
Operation logs:
  01-requirements-v1.md (first draft)
  01-requirements-v2.md (after revision)
  01-requirements-v3.md (after second revision)

SDD:
  CBOL-123-sdd-v1.md
  CBOL-123-sdd-v2.md
```

**Why**:
- Audit trail of how design evolved
- Can compare versions to see what changed
- Prevents loss of previous reasoning
- Enables rollback if new version is worse

**CBOL Implementation**: Operation logs and SDDs are versioned

**Reference**: ai-coding-workflow (versioned operation logs)

### 3.4 Scope Discipline

**Principle**: Only implement what's in the approved SDD. New requirements → stop and escalate.

```
✅ Good: Implement exactly what SDD specifies
❌ Bad: AI adds "nice to have" features not in SDD
❌ Bad: AI changes approach mid-implementation without updating SDD
```

**Why**:
- Prevents scope creep
- Ensures implementation matches approved design
- New requirements may need different design
- Keeps PR focused and reviewable

**CBOL Implementation**: Anti-drift check verifies no out-of-scope changes. If new requirement emerges, stop and escalate.

**Reference**: All mature pipelines enforce scope discipline

---

## 4. Knowledge Injection

### 4.1 Stage-Specific Knowledge

**Principle**: Each stage reads only the relevant knowledge base documents, not the entire KB.

```
Stage 0 (Ticket): Read 01-CBOL-Domain-Knowledge/README.md
Stage 1 (Requirements): Read 01-CBOL-Domain-Knowledge/
Stage 2 (SDD): Read 02-Chat-Domain-Knowledge/ + 03-Design-Guidelines/
Stage 3 (TDD): Read 04-Coding-Guidelines/ + 02/java-implementation/ + code-templates/
Stage 4 (Test): Read 04/code-quality.md + 04/sonar-rules.md + 04/unit-testing-guidelines.md
Stage 5 (PR): Read 03/api-design-guidelines.md
Stage 6 (Deploy): Read 01/configuration/ + 01/deployment-architecture/
```

**Why**:
- Context window is limited — don't waste it on irrelevant docs
- Relevant knowledge improves output quality
- Reduces hallucination (AI has correct reference material)
- Stage-specific injection ensures consistency with project standards

**CBOL Implementation**: `knowledge_base.stage_injection` config maps stages to KB directories

**Reference**: CBOL-specific (structured KB enables this)

### 4.2 Progressive Disclosure

**Principle**: Start with high-level docs, drill down into specifics as needed.

```
Stage 2 (SDD):
  1. Read 03-Design-Guidelines/README.md (overview)
  2. Read relevant category (e.g., 03/02-api-design/)
  3. Read specific doc (e.g., rest-api-design.md)
  4. Search 02-Chat-Domain-Knowledge/ for relevant patterns
```

**Why**:
- Avoids context overload
- AI can request more detail if needed
- High-level docs provide framework, specific docs provide details
- Mirrors how human engineers work (start broad, drill down)

**CBOL Implementation**: Knowledge injection starts with READMEs, then specific docs

**Reference**: obra/superpowers (progressive disclosure pattern)

### 4.3 Reference Open Source Projects

**Principle**: When designing, reference proven open source implementations.

```
For IM system design:
  - Turms (Java/Netty, high concurrency, read diffusion)
  - Mattermost (Go, layered architecture, enterprise)
  - Rocket.Chat (Node.js, real-time, NATS microservices)
  - Matrix/Synapse (Python, federation, Event DAG)
```

**Why**:
- Don't reinvent the wheel — learn from proven designs
- Open source code provides concrete implementation reference
- Industry-standard patterns are more likely to be correct
- Reduces risk (patterns have been battle-tested)

**CBOL Implementation**: 02-Chat-Domain-Knowledge/open-source-deep-dive/ contains 6+ deep analyses

**Reference**: Turms, Mattermost, Rocket.Chat, Matrix/Synapse

---

## 5. Tooling & Infrastructure

### 5.1 Isolated Workspace

**Principle**: Run AI pipeline in an isolated workspace (container, cloud VM, or separate git branch).

```
✅ Good: Pipeline runs on feature branch, isolated from main
✅ Good: Pipeline runs in container/cloud workspace
❌ Bad: Pipeline runs directly on main branch
❌ Bad: Pipeline runs in shared environment with other work
```

**Why**:
- Prevents AI from accidentally breaking main branch
- Enables easy cleanup (delete branch/container)
- Isolates AI's experiments from human work
- Enables parallel pipelines (multiple tickets)

**CBOL Implementation**: Pipeline creates feature branch `feat/CBOL-XXX-{desc}`

**Reference**: GitHub Copilot (secure cloud workspace via GitHub Actions), Pavel Terenin's pipeline (self-hosted worker)

### 5.2 Structured Configuration

**Principle**: All pipeline configuration in version-controlled YAML, not hardcoded.

```
Config sections:
  - jira: connection, custom fields
  - github: repo, token
  - knowledge_base: stage injection mapping
  - limits: max retries, max cycles
  - branch: naming pattern
  - commit: convention
  - pr: title, body template, labels
  - tdd: framework, coverage thresholds
  - build: commands
  - deployment: tool, environments
  - observability: logging
```

**Why**:
- Configuration as code (version controlled, reviewable)
- Easy to modify without changing pipeline code
- Enables different configs for different projects
- Documents all pipeline parameters

**CBOL Implementation**: `.ai-workflow/config.example.yaml` + `project_mapping.yaml`

**Reference**: ai-coding-workflow (MCP config), all mature pipelines

### 5.3 Operation Logs

**Principle**: Every stage writes a structured operation log.

```
Operation log schema:
  # {Stage Name} — {JIRA-KEY} (v{N})

  ## Did
  {What was done}

  ## Impact
  {Files changed, components, risk}

  ## Could Not
  {What was NOT done and why}

  ## Engineering Decisions
  {Key decisions + trade-offs}

  ## Evidence
  - Command: `{command}`
  - Output: {summary}
  - Exit code: {0/1}

  ## Next
  {Handoff to next stage}
```

**Why**:
- Creates audit trail of entire pipeline
- Enables debugging (what was done, what failed)
- Provides context for next stage
- Human can review what AI did without reading all code

**CBOL Implementation**: `docs/operations/{KEY}/{NN}-{stage}-v{N}.md`

**Reference**: ai-coding-workflow (operation logs), jira-flow (evidence logs)

### 5.4 PR Template

**Principle**: Use a structured PR template that links to all pipeline artifacts.

```
PR Body:
  ## Summary
  {One-paragraph summary}

  ## Changes
  - Files changed
  - Modules affected
  - API changes

  ## SDD
  [Link to approved SDD]

  ## Test Results
  - Unit tests: {passed}/{total}
  - Integration tests: {passed}/{total}
  - Coverage: line {x}%, branch {y}%

  ## Checklist
  - [ ] SDD approved
  - [ ] All tests pass
  - [ ] Coverage >= threshold
  - [ ] No Sonar critical issues
  - [ ] Docs updated
  - [ ] No secrets committed

  ## Jira
  Closes CBOL-XXX
```

**Why**:
- Standardizes PR format
- Ensures all artifacts are linked
- Provides reviewer with complete context
- Checklist ensures nothing is missed

**CBOL Implementation**: `.ai-workflow/templates/pr-body.md`

**Reference**: ai-coding-workflow (PR body references design issue), all mature pipelines

---

## 6. Quality & Security

### 6.1 Automated Quality Gates

**Principle**: Enforce quality checks automatically before human review.

```
Automated gates:
  - All tests pass (unit + integration)
  - Coverage >= threshold (line 80%, branch 70%)
  - No SonarQube critical/blocker issues
  - No security vulnerabilities (Semgrep, dependency check)
  - Linting and formatting pass
  - Build succeeds
```

**Why**:
- Catches obvious issues before human review
- Saves human reviewer time
- Enforces consistent quality standards
- Prevents "it works on my machine" (CI-verified)

**CBOL Implementation**: Gate 3 auto-review runs all checks

**Reference**: shgandla/ai-agents (6 quality gates), GitHub Copilot (runs tests before PR)

### 6.2 Security-by-Design

**Principle**: Security is considered at every stage, not bolted on at the end.

```
Stage 1 (Requirements): Identify security requirements
Stage 2 (SDD): Threat modeling, auth/authorization design, data classification
Stage 3 (TDD): Security test cases (input validation, auth checks)
Stage 4 (Test): Security scan (SAST), dependency vulnerability scan
Stage 5 (PR): Security review checklist
```

**Why**:
- Security is cheaper to design in than to fix later
- AI may introduce security vulnerabilities without awareness
- Regulatory compliance requires security-by-design
- Prevents common AI mistakes (hardcoded secrets, SQL injection, etc.)

**CBOL Implementation**: SDD includes security section, threat modeling doc, security guidelines in 04-Coding-Guidelines

**Reference**: OWASP, all enterprise pipelines

### 6.3 No Secrets in Code

**Principle**: Never commit secrets, tokens, passwords to git. Use environment variables or secret management.

```
✅ Good: api_token: ${JIRA_API_TOKEN} (env var reference)
✅ Good: Secret stored in vault, fetched at runtime
❌ Bad: api_token: ghp_xxxxxxxxxxxx (hardcoded)
❌ Bad: Password in config file committed to git
```

**Why**:
- Secrets in git are permanently exposed (even if deleted later)
- Automated scanners can find and exploit committed secrets
- Regulatory violation (data protection laws)
- Account takeover risk

**CBOL Implementation**: Config uses `${ENV_VAR}` references, `.gitignore` excludes real config, AGENTS.md prohibits secrets

**Reference**: All security-conscious pipelines

---

## 7. AI-Specific Best Practices

### 7.1 Clear, Structured Prompts

**Principle**: Give AI clear, structured instructions with expected output format.

```
✅ Good:
  "Write a unit test for MessageService.sendMessage().
   Use JUnit 5 + Mockito.
   Test cases:
   1. Valid message → returns MessageResponse with id
   2. Empty content → throws IllegalArgumentException
   3. User not in conversation → throws AccessDeniedException
   Follow naming: should{Behavior}When{Condition}"

❌ Bad:
  "Write tests for MessageService"
```

**Why**:
- AI output quality depends on input clarity
- Structured prompts reduce ambiguity
- Expected format ensures consistency
- Specific test cases ensure coverage

**CBOL Implementation**: Skills have structured instructions, SDD provides detailed requirements, TDD skill has clear cycle definition

**Reference**: All AI engineering best practices

### 7.2 Chunking Large Tasks

**Principle**: Break large tasks into smaller, manageable chunks.

```
✅ Good: SDD → tasks.json (10-20 tasks) → TDD per task → commit per task
❌ Bad: "Implement the entire feature" (one giant task)
```

**Why**:
- AI context window is limited — large tasks lose context
- Smaller tasks are easier to review and debug
- Incremental commits enable rollback
- Progress is visible and measurable
- Prevents AI from getting lost in complexity

**CBOL Implementation**: TDD implements per task, max 50 TDD cycles per ticket, atomic commits

**Reference**: genkovich/sdd (tasks.json + implement per task), shgandla/ai-agents (TDD slices)

### 7.3 Objective Success Criteria

**Principle**: Define objective, measurable success criteria for each task.

```
✅ Good: "Test passes (exit code 0), coverage >= 80%, no Sonar critical issues"
❌ Bad: "Code looks good" (subjective)
```

**Why**:
- AI needs objective criteria to know when task is complete
- Prevents AI from declaring success prematurely
- Enables automated gate evaluation
- Reduces subjective disagreements

**CBOL Implementation**: Every gate has objective criteria (tests pass, coverage threshold, no critical issues)

**Reference**: jira-flow (evidence-based), all mature pipelines

### 7.4 Human Review of AI Output

**Principle**: Always have human review AI-generated code before merging.

```
AI can:
  - Write code based on clear specs
  - Write tests
  - Refactor
  - Fix bugs (with clear reproduction steps)
  - Generate documentation

AI struggles with:
  - Business judgment
  - Edge cases not in spec
  - Security implications
  - Performance trade-offs
  - Organizational context
  - "Is this the right thing to build?"
```

**Why**:
- AI hallucinates — may produce plausible but incorrect code
- AI lacks business context and judgment
- AI may introduce subtle bugs or security issues
- Human accountability for production code
- Review is also a learning opportunity (understand what AI built)

**CBOL Implementation**: Gate 5 peer review required before merge, no auto-merge

**Reference**: GitHub Copilot (human reviews and merges), all enterprise pipelines

---

## 8. Anti-Patterns to Avoid

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| **End-to-end without gates** | AI makes mistakes early, compounds them | Stage-based pipeline with approval gates |
| **No design before code** | AI solutions wrong problem, rework needed | SDD must be approved before implementation |
| **No evidence** | AI claims success but didn't actually do it | Command + output + exit code for every completion |
| **Infinite retry** | AI loops forever on unfixable issue | 3-strike escalation |
| **No state persistence** | Interruption means starting over | State file, resume from breakpoint |
| **No scope discipline** | AI adds features not in spec, PR too large | Only implement approved SDD, escalate new requirements |
| **Hardcoded secrets** | Security breach, account takeover | Environment variables, secret management |
| **AI self-approval** | No independent review, misses issues | Human approval at gates, no self-approval |
| **Giant tasks** | AI loses context, output quality drops | Chunk into small tasks, TDD per task |
| **No knowledge injection** | AI reinvents wheel, inconsistent with standards | Stage-specific KB injection |
| **Subjective success criteria** | AI declares success prematurely | Objective, measurable criteria |
| **No operation logs** | Can't debug what AI did, no audit trail | Structured operation logs at every stage |
| **Running on main branch** | AI can break production code | Feature branch, isolated workspace |
| **No security consideration** | AI introduces vulnerabilities | Security-by-design, threat modeling, SAST |
| **No human review** | Bugs and security issues reach production | Mandatory peer review before merge |
| **Vague prompts** | AI output inconsistent, low quality | Clear, structured prompts with expected format |
| **No config versioning** | Pipeline behavior changes unpredictably | Config as code (YAML), version controlled |
| **Ignoring open source** | Reinvent proven patterns | Reference open source implementations |
| **No rollback plan** | Bad deployment can't be undone | Rollback strategy, feature flags, versioned releases |

---

## 9. Maturity Model

### Level 1: Ad-Hoc
- AI used sporadically for code generation
- No pipeline, no gates
- No evidence, no state
- Human reviews everything manually

### Level 2: Structured
- Stage-based pipeline with gates
- TDD enforced
- Evidence-based completion
- State persistence
- Knowledge injection

### Level 3: Automated
- Automated quality gates (tests, coverage, Sonar)
- PR template with artifact links
- Operation logs
- 3-strike escalation
- Anti-drift checks

### Level 4: Integrated
- Jira integration (ticket-driven)
- CI/CD integration (auto-deploy)
- Cross-project support
- MCP server exposure
- Cloud workspace

### Level 5: Optimized
- Quality scoring
- Socratic questioning
- Independent review context
- Findings document format
- Memory/instincts
- Chaos testing of pipeline
- Continuous improvement based on metrics

**CBOL Current Level**: Level 3 (Structured + Automated)
**CBOL Target Level**: Level 4 (Integrated) by Q4 2026

---

## 10. Metrics to Track

| Metric | Description | Target |
|--------|-------------|--------|
| Pipeline success rate | % of tickets that complete all stages | > 80% |
| Stage completion time | Average time per stage | Track, optimize |
| Escalation rate | % of stages that hit 3-strike | < 15% |
| Revision loops per gate | Average revisions before approval | < 1.5 |
| PR review time | Time from PR open to first review | < 24h |
| PR merge rate | % of AI PRs that merge (vs close) | > 70% |
| Bug rate post-merge | Bugs found in AI-generated code after merge | Track, reduce |
| Test coverage | Line/branch coverage of AI code | >= 80% / 70% |
| Knowledge injection usage | % of stages that actually read KB | 100% |
| Human intervention points | Average number of human touches per ticket | Track, optimize |

---

## References

### Books
- *Accelerate* (Forsgren, Humble, Kim) — DevOps metrics and practices
- *The Pragmatic Programmer* (Hunt, Thomas) — Software craftsmanship
- *Clean Architecture* (Robert C. Martin) — Architecture principles
- *Designing Data-Intensive Applications* (Martin Kleppmann) — Distributed systems

### Articles
- [I built an AI pipeline that turns Jira tickets into reviewed, tested PRs](https://pavelterenin.com/blog/2026/04/26/i-built-an-ai-pipeline-that-turns-jira-tickets-into-reviewed-tested-pull-requests-heres-how-it-actually-works/) — Pavel Terenin
- [From Issue to Deploy — Autonomous Pipeline](https://www.youngju.dev/blog/culture/2026-05-14-issue-to-deploy-autonomous-pipeline-ai-code-review-ci-cd-auto-verify-rollback-deep-dive-guide-2025.en) — youngju.dev
- [Ticket-Driven Development with Codex CLI](https://codex.danielvaughan.com/2026/04/20/codex-cli-jira-ticket-driven-development-atlassian-mcp-automation/) — Daniel Vaughan
- [Ticket-to-PR: Turning Jira Issues Into Pull Requests Automatically](https://www.augmentcode.com/guides/jira-ticket-to-pull-request-automation) — Augment Code

### GitHub Projects
- [wenttt/ai-coding-workflow](https://github.com/wenttt/ai-coding-workflow) — MCP Server, pull-based pipeline
- [genkovich/sdd](https://github.com/genkovich/sdd) — Spec-Driven Development, 12 skills
- [stel1os/ai-sdd-sop](https://github.com/stel1os/ai-sdd-sop) — SDD + review rejection loop
- [GradeBuilderSL/partenit-claudev](https://github.com/GradeBuilderSL/partenit-claudev) — Jira-driven pipeline
- [shgandla/ai-agents](https://github.com/shgandla/ai-agents) — Multi-agent, 6 quality gates
- [obra/superpowers](https://github.com/obra/superpowers) — Agentic framework, 27k stars

### Documentation
- [GitHub Copilot Coding Agent](https://docs.github.com/en/copilot/tutorials/roll-out-at-scale/enable-developers/integrate-ai-agents) — Industry standard
- [OpenAI Codex + Jira](https://developers.openai.com/cookbook/examples/codex/jira-github.md) — CI/CD integration
- [Integrating agentic AI into SDLC](https://docs.github.com/en/copilot/rolling-out-github-copilot-at-scale/enabling-developers/integrating-agentic-ai) — GitHub enterprise guide

---

*Last updated: 2026-08-21*
*Version: 1.0*
*Based on: 10+ reference projects, industry research, CBOL pipeline experience*
