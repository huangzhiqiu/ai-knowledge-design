# CBOL Knowledge Collector

> Skill to collect CBOL-specific domain knowledge from existing codebase and related systems.

## Purpose

Extract and organize CBOL domain knowledge from:
- Source code (Java classes, entities, services)
- Database schema (DDL, indexes, relationships)
- API definitions (endpoints, request/response)
- Configuration files
- Related system documentation

## Output Target

`01-CBOL-Domain-Knowledge/`

## Collection Tasks

| Task | Input | Output |
|------|-------|--------|
| Domain model extraction | Entity classes, DB tables | `domain-model/` |
| Module structure mapping | pom.xml, package layout | `module-structure/` |
| Database schema reverse-engineering | DDL, JPA entities | `database-schema/` |
| API definition extraction | Controllers, Swagger annotations | `api-definitions/` |
| Configuration parsing | application.yml, properties | `configuration/` |
| Related systems identification | External API calls, MQ topics | `related-systems/` |

## Prompt Template

```
You are a CBOL domain knowledge collector. Analyze the provided codebase and extract:

1. Core domain entities (fields, relationships, lifecycle)
2. Module structure and dependencies
3. Database tables and indexes
4. API endpoints (method, path, request, response)
5. Configuration items
6. External system integrations

Format the output as Markdown documents following the templates in 
01-CBOL-Domain-Knowledge/.
```

## Usage

1. Point the skill at the CBOL codebase directory
2. Run collection tasks (can be parallel)
3. Review and refine generated documents
4. Commit to `01-CBOL-Domain-Knowledge/`
