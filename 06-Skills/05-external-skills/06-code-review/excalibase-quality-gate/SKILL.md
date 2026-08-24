---
name: quality-gate
description: Run lint, type-check, and format checks on a file or project. Use before committing or as a final quality check.
argument-hint: [path] [--fix] [--strict]
disable-model-invocation: true
---

# Quality Gate

Run the full quality pipeline: format, lint, types.

## Usage

```
/quality-gate           — check current directory
/quality-gate src/      — check specific path
/quality-gate --fix     — auto-fix where possible
/quality-gate --strict  — fail on warnings too
```

## Pipeline

### Step 1: Detect Language and Tooling

| File | Tools to Run |
|------|-------------|
| `package.json` | prettier, eslint, tsc |
| `tsconfig.json` | tsc --noEmit |
| `pyproject.toml` | black, ruff, mypy |
| `go.mod` | gofmt, go vet, staticcheck |
| `Cargo.toml` | cargo fmt, cargo clippy |
| `pom.xml` / `build.gradle` | checkstyle, spotbugs |

### Step 2: Run Checks in Order

1. **Format** — `prettier --check` / `black --check` / `gofmt -l`
2. **Lint** — `eslint` / `ruff` / `go vet`
3. **Types** — `tsc --noEmit` / `mypy` / `cargo check`

With `--fix`: run formatters in write mode first, then re-check.

### Step 3: Report

```
FORMAT:  PASS / X issues
LINT:    PASS / X warnings, Y errors
TYPES:   PASS / X errors

Overall: PASS / FAIL
```

- **FAIL** → list every issue with file:line and suggested fix
- **PASS** → ready to commit
