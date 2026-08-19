# Doc Generator

> Skill to generate API documentation, UML diagrams, and reports.

## Purpose

Automatically generate documentation from code and configuration:
- OpenAPI/Swagger YAML definitions
- UML diagrams (class, sequence, component)
- Architecture reports
- API reference docs

## Output Targets

| Output | Directory |
|--------|-----------|
| API definitions (YAML) | `01-CBOL-Domain-Knowledge/api-definitions/` |
| UML diagrams | `01-CBOL-Domain-Knowledge/uml-diagrams/` |
| Deployment docs | `01-CBOL-Domain-Knowledge/deployment-architecture/` |

## Generation Tasks

### 1. API Definition Generation
- Parse Spring Boot controllers
- Extract request/response DTOs
- Generate OpenAPI 3.0 YAML
- Include auth, error codes, examples

### 2. UML Diagram Generation
- Class diagrams from entities
- Sequence diagrams from service flows
- Component diagrams from module structure
- Deployment diagrams from infrastructure

### 3. Report Generation
- Architecture decision records (ADR)
- API change logs
- Dependency reports
- Quality dashboards

## Output Formats

| Format | Tool | Use Case |
|--------|------|----------|
| OpenAPI YAML | SpringDoc / springfox | API specs |
| PlantUML | plantuml.jar | UML diagrams |
| Mermaid | mermaid-cli | Diagrams in Markdown |
| AsciiDoc | asciidoctor | Technical docs |
| Markdown | - | Knowledge base docs |

## Prompt Template

```
You are a documentation generator. Based on the provided code, generate:

1. OpenAPI 3.0 YAML for all REST endpoints (paths, schemas, security)
2. PlantUML class diagram for domain entities
3. PlantUML sequence diagram for the main message flow
4. Component diagram showing module relationships

Follow the conventions in 01-CBOL-Domain-Knowledge/api-definitions/ 
and uml-diagrams/.
```

## Usage

1. Run API doc generation (SpringDoc Maven plugin)
2. Run UML generation (PlantUML from code)
3. Review and refine generated docs
4. Store in appropriate directories
