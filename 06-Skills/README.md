# Skills

> Automation skills for AI-driven development, code analysis, and knowledge collection. All skills are OpenCode-compatible (standard `SKILL.md` with frontmatter) unless noted as concept skills.

---

## Category 1: AI Development Pipeline (`01-ai-development-pipeline/`)

> End-to-end Jira-driven AI development workflow: Ticket → Requirements → SDD → Code(TDD) → Test → PR → Deploy. With approval gates, evidence discipline, 3-strike escalation, and operation logs.

### Orchestrator

| Skill | Directory | Purpose |
|-------|-----------|---------|
| **Workflow: Ticket to Deploy** | [workflow-ticket-to-deploy/](./01-ai-development-pipeline/workflow-ticket-to-deploy/) | Full pipeline orchestration (7 stages + 6 gates), operation log schema, gate check protocol, knowledge injection map, configuration |

### Stage Skills

| Stage | Skill | Directory | Purpose |
|-------|-------|-----------|---------|
| **Stage 0** | Jira Ticket Fetcher | [jira-ticket-fetcher/](./01-ai-development-pipeline/jira-ticket-fetcher/) | Fetch Jira ticket, structure content, extract acceptance criteria, clarity check, post clarification questions |
| **Stage 1** | (inline in orchestrator) | — | Requirements analysis: extract AC, propose 2-3 approaches with trade-offs |
| **Stage 2** | SDD Generator | [sdd-generator/](./01-ai-development-pipeline/sdd-generator/) | Generate comprehensive SDD (12 sections) with knowledge base injection |
| **Stage 3** | TDD Implementer | [tdd-implementer/](./01-ai-development-pipeline/tdd-implementer/) | Implement code using strict TDD (RED→GREEN→REFACTOR→Commit), inject coding guidelines + Java implementation references |
| **Stage 4** | Test Verifier | [test-verifier/](./01-ai-development-pipeline/test-verifier/) | Run tests, collect evidence, verify coverage, manage fix loop with 3-strike escalation |
| **Stage 5** | PR Creator | [pr-creator/](./01-ai-development-pipeline/pr-creator/) | Create branch, commit, push, create GitHub PR with structured body, manage CI repair loop |
| **Stage 6** | Deploy & Doc Updater | [deploy-doc-updater/](./01-ai-development-pipeline/deploy-doc-updater/) | Update deployment config, update knowledge base docs, update Jira status to Done, post summary |

### Pipeline Flow

```
Jira Ticket (CBOL-XXX)
    │
    ▼
[Stage 0] Ticket Intake ──→ [Gate 0] Clarity check
    │
    ▼
[Stage 1] Requirements ──→ [Gate 1] Human review
    │
    ▼
[Stage 2] SDD Generation ──→ [Gate 2] Human review
    │
    ▼
[Stage 3] TDD Implementation ──→ [Gate 3] Automated self-review
    │
    ▼
[Stage 4] Test & Verification ──→ [Gate 4] Human review
    │
    ▼
[Stage 5] PR Creation ──→ [Gate 5] Peer review (enforced)
    │
    ▼
[Stage 6] Deploy & Doc Update
    │
    ▼
[Complete]
```

### Key Design Patterns

- **Approval gates**: Pause before major transitions; human approves/revises/questions
- **Evidence discipline**: Every completion claim backed by command + output + exit code
- **3-strike escalation**: Auto-retry up to 3 times, then escalate to human
- **TDD discipline**: RED→GREEN→REFACTOR→Commit; no production code before failing test
- **Controlled writes**: Agents work locally; external mutations at explicit workflow steps
- **Operation logs**: Every stage writes structured log to `docs/operations/{KEY}/`
- **Knowledge injection**: Each stage reads relevant knowledge base documents

---

## Category 2: Code Analysis & Reverse Engineering (`02-code-analysis/`)

> Skills for analyzing existing codebases, extracting architecture, and performing security audits. Use these before starting new development to understand the existing system.

| Skill | Directory | Purpose | Output |
|-------|-----------|---------|--------|
| **Java Maven Project Analyzer** | [java-maven-project-analyzer/](./02-code-analysis/java-maven-project-analyzer/) | Analyze Java Maven multi-module projects: APIs, data models, business logic, system boundaries, dependencies | `PROJECT_ARCHITECTURE.md` |
| **Architecture Analyzer** | [architecture-analyzer-skill/](./02-code-analysis/architecture-analyzer-skill/) | Deeply analyze any codebase → single `architecture.md` optimized for LLM consumption (16 sections: tech stack, design patterns, data layer, API layer, business flows, legacy guidance, with real code snippets) | `architecture.md` |
| **Codebase Architecture Analyst** | [codebase-architecture-analyst/](./02-code-analysis/codebase-architecture-analyst/) | File-level reverse engineering → 6 output folders (source files, dependency graph, architecture docs, OWASP security audit, interactive query DB, Mermaid visuals). Supports SAST tools (SpotBugs, PMD, semgrep, grype) | 6 output folders |

### Recommended Analysis Workflow

```
1. architecture-analyzer-skill     → Quick, comprehensive architecture.md (LLM context)
2. java-maven-project-analyzer     → Java/Maven-specific deep dive (if Java project)
3. codebase-architecture-analyst   → Exhaustive reverse engineering + security audit
```

---

## Category 3: Knowledge Collection (`03-knowledge-collection/`)

> Concept skills (documentation templates) for collecting and organizing knowledge. Code analysis and doc generation are now handled by executable skills in `02-code-analysis/`.

| Skill | Directory | Purpose | Output Target |
|-------|-----------|---------|---------------|
| **CBOL Knowledge Collector** | [cbol-knowledge-collector/](./03-knowledge-collection/cbol-knowledge-collector/) | Extract CBOL domain knowledge from existing code and related systems | `01-CBOL-Domain-Knowledge/` |
| **Chat Pattern Collector** | [chat-pattern-collector/](./03-knowledge-collection/chat-pattern-collector/) | Collect design patterns and code templates from open-source IM projects | `02-Chat-Domain-Knowledge/` |

---

## OpenCode Skill Installation

```powershell
# Windows - install all OpenCode-compatible skills globally
$skills = @(
    "01-ai-development-pipeline\workflow-ticket-to-deploy",
    "01-ai-development-pipeline\jira-ticket-fetcher",
    "01-ai-development-pipeline\sdd-generator",
    "01-ai-development-pipeline\tdd-implementer",
    "01-ai-development-pipeline\test-verifier",
    "01-ai-development-pipeline\pr-creator",
    "01-ai-development-pipeline\deploy-doc-updater",
    "02-code-analysis\java-maven-project-analyzer",
    "02-code-analysis\architecture-analyzer-skill",
    "02-code-analysis\codebase-architecture-analyst"
)
foreach ($s in $skills) {
    $name = Split-Path $s -Leaf
    Copy-Item -Recurse -Force $s "$env:USERPROFILE\.config\opencode\skills\$name"
}

# Restart OpenCode to load
```

See each skill's `SKILL.md` for usage details.

---

## Skill Structure

### OpenCode Skill (executable)
```
skill-name/
├── SKILL.md            # Required: frontmatter (name, description) + workflow instructions
├── README.md           # Documentation (installation, usage, comparison)
├── templates/          # Document templates (optional)
└── references/         # Reference docs loaded on demand (optional)
```

### Concept Skill (documentation template)
```
skill-name/
├── README.md           # Skill description, usage, parameters
├── prompt.md           # AI prompt / instruction template
├── config.json         # Configuration (optional)
├── examples/           # Example inputs/outputs (optional)
└── scripts/            # Helper scripts (optional)
```

---

## Configuration

The AI development pipeline uses `.ai-workflow/config.yaml` in the project root:

```yaml
jira:
  base_url: https://your-domain.atlassian.net
  project_key: CBOL
  email: your-email@company.com
  api_token: ${JIRA_API_TOKEN}

github:
  owner: huangzhiqiu
  repo: cbol-refactor
  default_branch: main

limits:
  max_revision_loops: 3
  max_test_retries: 3
  max_ci_repair_loops: 3
```

See `.ai-workflow/config.yaml` for full configuration options.

---

## References

- AI-driven development reference projects: `05-References/ai-driven-development.md`
- Forge (approval gates, container isolation): https://github.com/forge-sdlc/forge
- Jira-Flow (TDD discipline, evidence discipline): https://github.com/jinx911/jira-flow
- ai-coding-workflow (pull-based, operation logs): https://github.com/wenttt/ai-coding-workflow
