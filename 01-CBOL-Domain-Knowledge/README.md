# CBOL Domain Knowledge

> CBOL project-specific domain knowledge. Fill in based on actual project codebase and related systems.

## What Goes Here

This directory is for **CBOL-specific** knowledge extracted from the existing codebase, related systems, and project documentation. Use the generic IM knowledge in [02-Chat-Domain-Knowledge](../02-Chat-Domain-Knowledge/) as reference.

## Sub-directories

| Directory | Description | Status |
|-----------|-------------|--------|
| [domain-model/](./domain-model/) | CBOL-specific entities and relationships | Template ready |
| [module-structure/](./module-structure/) | Maven/Gradle module decomposition | Template ready |
| [database-schema/](./database-schema/) | Tables, indexes, sharding strategy | Template ready |
| [api-definitions/](./api-definitions/) | API interface definitions in YAML (OpenAPI) | To be filled |
| [uml-diagrams/](./uml-diagrams/) | UML diagrams (class, sequence, component) | To be filled |
| [configuration/](./configuration/) | application.yml, environment configs | Template ready |
| [deployment-architecture/](./deployment-architecture/) | Deployment topology, capacity, CI/CD | Template ready |
| [related-systems/](./related-systems/) | Upstream/downstream systems, integrations | Template ready |
| [state-machine/](./state-machine/) | Self-developed lightweight state machine (design + API + integration) | Documented |

## Documents

| Document | Location | Description |
|----------|----------|-------------|
| Related Systems | [related-systems/related-systems.md](./related-systems/related-systems.md) | Integration points and data flow |

## How to Collect

1. **Domain Model**: Reverse-engineer from entity classes / database tables
2. **Module Structure**: Read pom.xml/build.gradle, map package responsibilities
3. **Database Schema**: Export DDL, document indexes and relationships
4. **API Definitions**: Generate from Swagger/OpenAPI annotations, or export from API gateway
5. **UML Diagrams**: Generate from code (PlantUML) or draw manually
6. **Configuration**: Extract from application.yml, note environment-specific overrides
7. **Deployment**: Document from Docker/K8s manifests, infrastructure-as-code
8. **Related Systems**: Trace API calls, message queue topics, database connections

## Template Usage

Each subdirectory contains a README with templates. Copy the template, fill in CBOL-specific details.
