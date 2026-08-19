# Skills

> Automation skills for collecting, organizing, and generating knowledge. Includes OpenCode-compatible skills (standard `SKILL.md` with frontmatter) and knowledge-base concept skills.

## What is a Skill?

A skill is a reusable automation module that performs specific knowledge management tasks:
- Collect domain knowledge from codebases
- Extract design patterns from open-source projects
- Analyze code structure
- Generate documentation

## Skill Categories

### OpenCode-Compatible Skills (standard SKILL.md + frontmatter)

These can be installed directly into OpenCode (`~/.config/opencode/skills/`) and triggered by natural language.

| Skill | Directory | Purpose |
|-------|-----------|---------|
| **Workflow: Ticket to Deploy** | [workflow-ticket-to-deploy/](./workflow-ticket-to-deploy/) | Full pipeline orchestration: Jira Ticket → Requirements → SDD → Code(TDD) → Test → PR → Deploy, with approval gates, evidence discipline, 3-strike escalation, operation logs |
| **Jira Ticket Fetcher** | [jira-ticket-fetcher/](./jira-ticket-fetcher/) | Stage 0: Fetch Jira ticket, structure content, extract acceptance criteria, clarity check, post clarification questions |
| **SDD Generator** | [sdd-generator/](./sdd-generator/) | Stage 2: Generate comprehensive SDD from requirements + knowledge base injection (12 sections: domain model, architecture, interfaces, data model, concurrency, security, testing, deployment, risks) |
| **Java Maven Project Analyzer** | [java-maven-project-analyzer/](./java-maven-project-analyzer/) | Analyze Java Maven multi-module projects → `PROJECT_ARCHITECTURE.md` (APIs, data models, business logic, system boundaries, dependencies) |
| **Architecture Analyzer** | [architecture-analyzer-skill/](./architecture-analyzer-skill/) | Deeply analyze any codebase → single `architecture.md` optimized for LLM consumption (16 sections: tech stack, design patterns, data layer, API layer, business flows, legacy guidance, with real code snippets) |
| **Codebase Architecture Analyst** | [codebase-architecture-analyst/](./codebase-architecture-analyst/) | File-level reverse engineering → 6 output folders (source files, dependency graph, architecture docs, OWASP security audit, interactive query DB, Mermaid visuals). Supports SAST tools (SpotBugs, PMD, semgrep, grype) |

### Knowledge-Base Concept Skills (documentation templates)

These are concept documents for knowledge collection workflows, not executable OpenCode skills.

| Skill | Directory | Purpose |
|-------|-----------|---------|
| CBOL Knowledge Collector | [cbol-knowledge-collector/](./cbol-knowledge-collector/) | Extract CBOL domain knowledge from existing code and related systems |
| Chat Pattern Collector | [chat-pattern-collector/](./chat-pattern-collector/) | Collect design patterns and code templates from open-source IM projects |
| Code Analyzer | [code-analyzer/](./code-analyzer/) | Analyze code structure, dependencies, and quality |
| Doc Generator | [doc-generator/](./doc-generator/) | Generate API docs, UML diagrams, and reports |

## OpenCode Skill Installation

```powershell
# Windows - install all OpenCode-compatible skills globally
$skills = "java-maven-project-analyzer", "architecture-analyzer-skill", "codebase-architecture-analyzer"
foreach ($s in $skills) {
    Copy-Item -Recurse -Force $s "$env:USERPROFILE\.config\opencode\skills\"
}

# Restart OpenCode to load
```

See each skill's `README.md` for project-local installation and usage details.

## Recommended Analysis Workflow

```
1. architecture-analyzer-skill     → Quick, comprehensive architecture.md (LLM context)
2. java-maven-project-analyzer     → Java/Maven-specific deep dive (if Java project)
3. codebase-architecture-analyst   → Exhaustive reverse engineering + security audit
```

## Skill Structure

### OpenCode Skill
```
skill-name/
├── SKILL.md            # Required: frontmatter (name, description) + workflow instructions
├── README.md           # Documentation (installation, usage, comparison)
└── references/         # Optional: reference docs loaded on demand
```

### Knowledge-Base Concept Skill
```
skill-name/
├── README.md           # Skill description, usage, parameters
├── prompt.md           # AI prompt / instruction template
├── config.json         # Configuration (optional)
├── examples/           # Example inputs/outputs (optional)
└── scripts/            # Helper scripts (optional)
```

## Skill Output Targets

| Skill | Output Goes To |
|-------|---------------|
| CBOL Knowledge Collector | `01-CBOL-Domain-Knowledge/` |
| Chat Pattern Collector | `02-Chat-Domain-Knowledge/` |
| Code Analyzer | `01-CBOL-Domain-Knowledge/module-structure/`, `database-schema/` |
| Doc Generator | `01-CBOL-Domain-Knowledge/api-definitions/`, `uml-diagrams/` |
| Java Maven Project Analyzer | `PROJECT_ARCHITECTURE.md` (project root) |
| Architecture Analyzer | `architecture.md` (project root or `.github/`) |
| Codebase Architecture Analyst | User-specified output path (6 folders) |
