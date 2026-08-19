# Architecture Analyzer Skill

A GitHub Copilot/AI agent **skill** that deeply analyzes a software codebase (any language or framework) and produces a single comprehensive `architecture.md` file optimized for **LLM consumption** — so any AI assistant can build, extend, debug, and test your codebase with high precision.

The output is not documentation for humans. It's an **LLM context document**: tech stack, project map, design patterns, data layer, modules, infrastructure, API layer, testing conventions, build commands, feature walkthroughs, business flows, and "don't do this" guardrails — all with **real code snippets extracted from your repo**.

Works with .NET, Node.js/TypeScript, Python, Java/Kotlin, Go, Ruby, PHP, Rust, and polyglot repos.

---

## How I use it

I run this skill once per repo (or after relevant project update) to generate `.github/architecture.md`, then **reference that file from `copilot-instructions.md`, `AGENTS.md`, and scoped `*.instructions.md` files**. This keeps top-level instructions short while giving the model deep architectural context on demand.

### Example: `.github/copilot-instructions.md`

Only the snippet that references `architecture.md` is shown. The rest of your top-level instructions (quick reference table, coding conventions, etc.) goes around it.

```markdown
<!-- ...your other top-level instructions... -->

## Architecture Reference

Before making non-trivial code changes (new features, refactors, new modules, new entities),
read `.github/architecture.md`. It documents the project's full architecture, patterns,
conventions, dependency rules, and "don't do this" guardrails with real code examples.
Do not read it for simple fixes or minor edits.

<!-- ...rest of your instructions... -->
```

### Example: scoped `*.instructions.md`

Same idea for scoped instructions — point at the relevant sections of `architecture.md` and keep only the few rules that must always be in context.

```markdown
---
applyTo: "<your glob here>"
---

<!-- ...your scoped rules... -->

For full patterns and code examples, read `.github/architecture.md` (Sections X, Y).

<!-- ...the 2–3 most critical rules that must always be loaded... -->
```

The pattern: top-level and scoped instructions stay small and link into specific sections of `architecture.md`. Everything deep — patterns, code snippets, conventions — lives in the architecture doc and is pulled in on demand.

---

## Setup

This is a Copilot skill — a folder containing `SKILL.md` and supporting reference files. To install it for use with VS Code Copilot or other agent surfaces that support skills:

### Option 1: User-level (available in all workspaces)

Copy or clone this folder into your user skills directory:

**Windows**
```powershell
git clone https://github.com/<your-fork>/architecture-analyzer "$env:USERPROFILE\.agents\skills\architecture-analyzer"
```

**macOS / Linux**
```bash
git clone https://github.com/<your-fork>/architecture-analyzer ~/.agents/skills/architecture-analyzer
```

### Option 2: Repo-level (committed alongside your code)

Place the folder under `.github/skills/architecture-analyzer/` in your repo so it travels with the project.

### Verify

In a Copilot chat, ask:

> Use the architecture-analyzer skill to generate architecture.md for this repo.

The agent should pick up the skill, run through the three phases (Detect → Explore → Synthesize), and write `architecture.md` to the workspace root (or wherever you ask).

---

## What the skill produces

A single Markdown file with these sections (filled with real code from your repo):

1. Project Identity
2. Tech Stack
3. Solution Architecture (project map + dependency rules)
4. Core Design Patterns (CQRS, Result types, validation, pipelines, events)
5. Data Layer (DbContext / sessions, entity config, transactions, read-vs-write split)
6. Application Layer / Modules
7. Infrastructure Layer
8. API Layer (controllers, middleware, auth per project, CORS)
9. Testing Conventions
10. Coding Conventions
11. Build & Run Commands
12. How to Add a New Feature (step-by-step)
13. Multi-Tenancy / Cross-Cutting Concerns
14. Key Business Flows
15. Legacy Code Guidance
16. Quick Reference — Where Does This Go?

See [`references/output-template.md`](references/output-template.md) for the full template and [`SKILL.md`](SKILL.md) for the analysis workflow.

---

## License

[MIT](LICENSE) © 2026 David Szorad
