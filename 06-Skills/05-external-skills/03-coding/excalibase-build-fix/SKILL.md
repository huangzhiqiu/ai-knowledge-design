---
name: build-fix
description: Incrementally fix build and type errors with minimal safe changes. Use when build or type-check fails.
argument-hint: [optional: specific error or file to focus on]
---

# Build Fix

Incrementally fix build and type errors one at a time.

## Step 1: Detect Build System and Run It

| Indicator | Build Command |
|-----------|---------------|
| `package.json` with `build` script | `npm run build` / `pnpm build` |
| `tsconfig.json` | `npx tsc --noEmit` |
| `Cargo.toml` | `cargo build 2>&1` |
| `pom.xml` | `mvn compile` |
| `build.gradle` | `./gradlew compileJava` |
| `go.mod` | `go build ./...` |

## Step 2: Parse and Group Errors

1. Capture stderr output
2. Group errors by file path
3. Sort by dependency order — fix imports/types before logic errors
4. Count total for progress tracking

## Step 3: Fix Loop (One Error at a Time)

For each error:
1. **Read the file** — see 10 lines around the error for context
2. **Diagnose** — root cause: missing import, wrong type, syntax error
3. **Fix minimally** — smallest change that resolves the error
4. **Re-run build** — verify error gone, no new errors introduced
5. **Move to next**

## Step 4: Guardrails — Stop and Ask If:

- A fix introduces more errors than it resolves
- Same error persists after 3 attempts (likely deeper issue)
- Fix requires architectural changes
- Missing dependencies (need `npm install`, `cargo add`, etc.)

## Step 5: Summary

```
Errors fixed:     X (list file paths)
Errors remaining: Y (if any)
New errors:       0
```

## Recovery Strategies

| Situation | Action |
|-----------|--------|
| Missing import | Check if package installed; suggest install command |
| Type mismatch | Read both type definitions; fix the narrower type |
| Circular dependency | Identify cycle; suggest extraction |
| Version conflict | Check lockfile/manifest for version constraints |
