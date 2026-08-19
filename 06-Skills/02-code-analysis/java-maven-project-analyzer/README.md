# Java Maven Project Analyzer (OpenCode Skill)

> OpenCode skill for analyzing Java Maven multi-module projects. Generates `PROJECT_ARCHITECTURE.md` covering API interfaces, data models, core business logic, system boundaries, and dependencies.

## What It Does

This skill systematically analyzes a Java/Maven project and produces a comprehensive architecture document:

| Analysis Area | What It Extracts |
|---------------|-----------------|
| **Maven Structure** | Module hierarchy, internal/external dependencies, versions, plugins |
| **API Interfaces** | REST controllers, Feign/RPC clients, WebSocket/Netty handlers |
| **Data Models** | Entities, DTOs/VOs, Enums, Mappers/Repositories, SQL migrations |
| **Core Logic** | Service classes, state machines, event consumers, scheduled tasks, critical call chains |
| **System Boundaries** | External API integrations, caching, configuration, upstream/downstream systems |
| **Cross-Cutting** | Security, exception handling, logging/tracing, interceptors/AOP, testing |

## Installation

### Option 1: Copy to OpenCode global skills directory

```bash
# Windows
copy /E java-maven-project-analyzer %USERPROFILE%\.config\opencode\skills\

# Linux/Mac
cp -r java-maven-project-analyzer ~/.config/opencode/skills/
```

### Option 2: Project-local skills

```bash
# Copy to your project's .opencode/skills/ directory
cp -r java-maven-project-analyzer /path/to/your-project/.opencode/skills/
```

### Option 3: Symlink (recommended for keeping updated)

```bash
# Linux/Mac
ln -s /path/to/ai-knowledge-design/06-Skills/java-maven-project-analyzer ~/.config/opencode/skills/java-maven-project-analyzer

# Windows (Admin PowerShell)
New-Item -ItemType SymbolicLink -Path "$env:USERPROFILE\.config\opencode\skills\java-maven-project-analyzer" -Target "C:\Users\Administrator\Desktop\ai-knowledge-design\06-Skills\java-maven-project-analyzer"
```

After installation, **restart OpenCode** to load the skill.

## Usage

In OpenCode, trigger the skill with any of these phrases:

- "Analyze this Java project"
- "Map the Maven architecture"
- "Document the project structure"
- "Extract APIs and data models"
- "Help me understand this codebase"
- "Generate PROJECT_ARCHITECTURE.md"

The skill will automatically trigger when it detects `pom.xml` files in the project.

## Output

Generates `PROJECT_ARCHITECTURE.md` in the project root with 11 sections:

1. Project Overview
2. Tech Stack & Versions
3. Maven Module Structure (hierarchy + dependency table)
4. Architecture Diagram (ASCII)
5. API Interfaces (REST / Feign / WebSocket)
6. Data Model (Entities / DTOs / Enums / Schema)
7. Core Business Logic (Services / Flows / State Machines / Events)
8. System Boundaries (External Integrations / Cache / Config)
9. Cross-Cutting Concerns (Security / Exceptions / Logging)
10. Key Files Entry Points
11. Development Notes

Also updates `AGENTS.md` to reference the architecture map for future AI sessions.

## How It Works

The skill follows 7 phases:

```
Phase 1: Maven Structure Discovery  →  read all pom.xml, build module graph
Phase 2: API Interface Extraction   →  find Controllers, Feign clients, handlers
Phase 3: Data Model Extraction      →  find Entities, DTOs, Enums, Mappers
Phase 4: Core Business Logic        →  find Services, state machines, events, tasks
Phase 5: System Boundaries          →  find configs, external calls, caching
Phase 6: Cross-Cutting Concerns     →  security, exceptions, logging, AOP
Phase 7: Document Generation        →  write PROJECT_ARCHITECTURE.md
```

## Requirements

- OpenCode with `glob`, `grep`, `read`, `bash`, `write`, `edit` tools enabled
- Java/Maven project with `pom.xml` files
- (Optional) Maven CLI for `mvn dependency:tree`

## Based On

Inspired by [arielsand/my-opencode-skills](https://github.com/arielsand/my-opencode-skills) `architecture-map` skill, customized and extended specifically for Java Maven multi-module projects.
