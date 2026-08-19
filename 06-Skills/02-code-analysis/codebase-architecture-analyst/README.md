# Codebase Architecture Analyst (OpenCode Skill)

> Deep file-level reverse engineering of any codebase. Generates comprehensive architecture docs, interactive dependency graphs, security audits (OWASP Top 10), and impact analysis.

## Source

- **Original repo**: [toddysm/software-engineering-skills](https://github.com/toddysm/software-engineering-skills)
- **License**: MIT
- **Adapted for**: OpenCode (frontmatter compatible, no changes needed)

## What It Does

Performs deep, file-level analysis of a codebase to reverse-engineer its architecture:

| Capability | Description |
|-----------|-------------|
| **File-Level Understanding** | Reads docstrings/comments, analyzes code structure, catalogs all functions/classes/exports, determines each file's responsibility |
| **Bi-directional Dependencies** | "What depends on this file?" + "What does this file depend on?" — function-level granularity |
| **Impact Analysis** | "If I change X, what breaks?" — maps ripple effects |
| **Architecture Diagrams** | Mermaid component diagrams, data flow, deployment topology |
| **Security Audit** | OWASP Top 10, secrets detection, attack surface mapping, dependency CVEs |
| **SAST Integration** | Java: SpotBugs + find-sec-bugs, PMD, grype, semgrep (auto-detects available tools) |
| **Interactive Queries** | Queryable dependency database for follow-up questions |

## Trigger Phrases

- "Reverse engineer this project"
- "Generate architecture from code"
- "Explain this project"
- "Analyze codebase architecture"
- "Document this system's architecture"
- "Run a security audit"
- "Find security vulnerabilities in this code"
- "What files depend on [filename]?"
- "Show me the dependency graph"

## Output Structure

Results are saved to a user-specified path (NOT inside the analyzed project):

```
{output-path}/{project-name}/{timestamp}/
├── source-files/           # File inventory + per-file analysis + documentation map
├── dependencies/           # Dependency graph (JSON + interactive HTML) + impact analysis + circular deps
├── analysis/               # Security overview + architecture summary + component guide + tech decisions
├── security/               # Full vulnerability report + remediation guide + attack surface map + dependency audit
├── interactive/            # Queryable dependency DB + query examples
└── visuals/                # Mermaid architecture diagrams + dependency graphs
```

## Java/Kotlin SAST Tools

The skill auto-detects and uses these tools when available (falls back to AI-assisted analysis):

| Tool | Purpose |
|------|---------|
| `semgrep` | Multi-language static analysis (OWASP pack) |
| `SpotBugs` + `find-sec-bugs` | Java bytecode analysis + security bugs |
| `PMD` | Java source code analysis |
| `grype` | Dependency vulnerability scanner |
| `gitleaks` / `truffleHog3` | Secrets detection |

## Notes for OpenCode Users

- The original SKILL.md references Python helper scripts (`scripts/*.py`) that are **not included** in the upstream repo. OpenCode's AI agent will perform equivalent analysis using its built-in tools (`glob`, `grep`, `read`, `bash`).
- Tool names in the skill (e.g., `search_subagent`) are Copilot-specific; OpenCode maps them to its own tool set automatically.
- The skill will **ask you for an output path** before starting — it never modifies the analyzed project.

## Installation

```powershell
# Windows - global
Copy-Item -Recurse -Force codebase-architecture-analyst "$env:USERPROFILE\.config\opencode\skills\"

# Or project-local
Copy-Item -Recurse -Force codebase-architecture-analyst "your-project\.opencode\skills\"
```

Restart OpenCode after installation.

## Comparison with architecture-analyzer-skill

| Aspect | architecture-analyzer-skill | codebase-architecture-analyst |
|--------|---------------------------|-------------------------------|
| **Output** | Single `architecture.md` (LLM-optimized) | 6 folders (docs + JSON + HTML + security) |
| **Depth** | Pattern-focused, with real code snippets | File-level, function-level dependencies |
| **Security** | Basic conventions | Full OWASP audit + SAST tools |
| **Interactive** | No | Yes (queryable dependency DB) |
| **Best for** | Generating AI context for code generation | Deep reverse engineering + security audit |
| **Speed** | Faster (3 phases) | Slower (exhaustive file scan) |

**Recommended workflow**: Run `architecture-analyzer-skill` first for a quick AI context document, then run `codebase-architecture-analyst` for deep security and dependency analysis.
