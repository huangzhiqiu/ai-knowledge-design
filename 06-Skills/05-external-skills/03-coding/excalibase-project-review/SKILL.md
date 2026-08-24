---
name: project-review
description: Whole-project quality audit covering architecture, code quality, security, test coverage, docs, and dependencies. Use when reviewing an entire codebase, onboarding to a new project, or before a major release.
argument-hint: [path or leave blank for current project]
---

# Project Review

Comprehensive audit of the entire project. Deploy multiple review agents in parallel for speed.

## Step 1: Orientation

Read the project root to understand what we're dealing with:
- `README.md`, `CLAUDE.md`, `package.json` / `pom.xml` / `go.mod` / `Cargo.toml`
- Directory structure — run `ls` and identify the major modules
- Identify language/framework: TypeScript, Java/Spring, Go, Rust, Python, etc.

## Step 2: Parallel Review (Launch Subagents)

Launch up to 5 review perspectives in parallel:

### 2a. Architecture Review
- Separation of concerns — are layers clean (controller → service → repository)?
- Single responsibility — are files/modules focused?
- Coupling — can modules be changed independently?
- Scalability — are there obvious bottlenecks?
- Use `/architecture-review` skill or `architect` agent.

### 2b. Code Quality
- File size — any files > 800 lines?
- Function size — any functions > 50 lines?
- Nesting depth > 4 levels?
- Dead code — unused exports, imports, files?
- Naming — are identifiers clear and consistent?
- Use `code-reviewer` agent.

### 2c. Security
- Hardcoded secrets (grep for API keys, passwords, tokens)
- SQL injection risks
- Input validation at system boundaries
- Auth/authz checks on all endpoints
- Dependencies with known CVEs
- Use `security-reviewer` agent.

### 2d. Test Coverage
- Does a test suite exist?
- What types: unit, integration, E2E?
- Run test suite — what's the pass rate?
- Estimate coverage — is it 80%+?
- Are both API integration tests (`/integration-testing`) and UI tests (`/ui-testing`) present?
- TDD evidence — are tests in the commit history before implementation?

### 2e. Documentation & DevEx
- README: setup instructions, architecture overview, contribution guide?
- API docs: OpenAPI/Swagger or equivalent?
- Inline comments on non-obvious logic?
- Docker/docker-compose for local dev?
- CI/CD pipeline configured?

## Step 3: Dependency Health

```bash
# Node.js
npm audit
npx depcheck

# Java
mvn dependency:tree
mvn dependency-check:check

# Go
go mod tidy
govulncheck ./...

# Python
pip-audit
```

## Step 4: Report

```markdown
# Project Review: [Project Name]

## Summary
[2-3 sentence overall assessment]

## Findings

### CRITICAL (must fix)
| # | Category | Finding | File:Line | Recommendation |
|---|----------|---------|-----------|----------------|

### HIGH (should fix)
| # | Category | Finding | File:Line | Recommendation |
|---|----------|---------|-----------|----------------|

### MEDIUM (nice to fix)
| # | Category | Finding | File:Line | Recommendation |
|---|----------|---------|-----------|----------------|

## Scores (1-5)
- Architecture:  X/5
- Code Quality:  X/5
- Security:      X/5
- Test Coverage: X/5
- Documentation: X/5

## Top 3 Recommended Actions
1. ...
2. ...
3. ...
```

## Step 5: Next Steps

Based on the report:
- CRITICAL findings → fix immediately
- HIGH findings → create issues/tasks
- MEDIUM findings → track for future sprints
- Run `/architecture-review` for deeper architecture analysis if score < 3
