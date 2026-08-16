# Skills

> Automation skills for collecting, organizing, and generating knowledge.

## What is a Skill?

A skill is a reusable automation module that performs specific knowledge management tasks:
- Collect domain knowledge from codebases
- Extract design patterns from open-source projects
- Analyze code structure
- Generate documentation

## Skill Categories

| Skill | Directory | Purpose |
|-------|-----------|---------|
| CBOL Knowledge Collector | [cbol-knowledge-collector/](./cbol-knowledge-collector/) | Extract CBOL domain knowledge from existing code and related systems |
| Chat Pattern Collector | [chat-pattern-collector/](./chat-pattern-collector/) | Collect design patterns and code templates from open-source IM projects |
| Code Analyzer | [code-analyzer/](./code-analyzer/) | Analyze code structure, dependencies, and quality |
| Doc Generator | [doc-generator/](./doc-generator/) | Generate API docs, UML diagrams, and reports |

## Skill Structure

Each skill directory should contain:

```
skill-name/
├── README.md           # Skill description, usage, parameters
├── prompt.md           # AI prompt / instruction template
├── config.json         # Configuration (optional)
├── examples/           # Example inputs/outputs (optional)
└── scripts/            # Helper scripts (optional)
```

## How to Use

1. Choose a skill based on the task
2. Read the skill's README for instructions
3. Apply the skill's prompt/script to collect or generate knowledge
4. Store results in the appropriate knowledge domain directory

## Skill Output Targets

| Skill | Output Goes To |
|-------|---------------|
| CBOL Knowledge Collector | `01-CBOL-Domain-Knowledge/` |
| Chat Pattern Collector | `02-Chat-Domain-Knowledge/` |
| Code Analyzer | `01-CBOL-Domain-Knowledge/module-structure/`, `database-schema/` |
| Doc Generator | `01-CBOL-Domain-Knowledge/api-definitions/`, `uml-diagrams/` |
