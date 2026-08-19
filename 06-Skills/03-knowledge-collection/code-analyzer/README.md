# Code Analyzer

> Skill to analyze code structure, dependencies, and quality metrics.

## Purpose

Analyze the CBOL codebase to extract:
- Module dependency graph
- Class hierarchy and relationships
- Code quality metrics
- Technical debt identification

## Output Targets

| Output | Directory |
|--------|-----------|
| Module structure | `01-CBOL-Domain-Knowledge/module-structure/` |
| Database schema | `01-CBOL-Domain-Knowledge/database-schema/` |
| Domain model | `01-CBOL-Domain-Knowledge/domain-model/` |

## Analysis Tasks

### 1. Module Dependency Analysis
- Parse pom.xml / build.gradle
- Build dependency graph
- Identify circular dependencies
- Document layer rules

### 2. Class Structure Analysis
- Extract entity classes and fields
- Map service interfaces and implementations
- Identify controller endpoints
- Document inheritance hierarchies

### 3. Database Analysis
- Parse JPA/Hibernate entities
- Extract table mappings
- Identify indexes and constraints
- Map relationships (1:1, 1:N, N:M)

### 4. Code Quality Scan
- Identify code smells
- Check Sonar rule compliance
- Document technical debt
- Suggest refactoring opportunities

## Prompt Template

```
You are a Java code analyzer. Analyze the provided codebase and produce:

1. Module dependency graph (Mermaid format)
2. Core class list with responsibilities
3. Entity-to-table mapping
4. API endpoint inventory
5. Code quality findings (grouped by severity)

Use the templates in 01-CBOL-Domain-Knowledge/ for output format.
```

## Tools

| Tool | Purpose |
|------|---------|
| jdeps | Java dependency analysis |
| Structure101 | Architecture visualization |
| SonarQube | Code quality metrics |
| PlantUML | Diagram generation |
