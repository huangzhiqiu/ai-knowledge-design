---
name: analyze
description: Analyze a Java Maven project - extract interfaces, data models, main logic, system boundaries, dependencies
arguments:
  - name: path
    description: Path to the Java Maven project (default: current directory)
    required: false
---

# Project Analysis

Load and execute the `java-maven-project-analyzer` skill to analyze the target Java Maven project.

## What It Extracts

- API interfaces and endpoints
- Data models (entities, DTOs, value objects)
- Main business logic and service layer
- System boundaries and module dependencies
- External dependencies (Maven, libraries)
- Architecture patterns and design principles

## Usage

```
/analyze path=./path/to/project
/analyze
```

## Output

The analysis results will be saved to `docs/analysis/` directory with structured markdown reports.
