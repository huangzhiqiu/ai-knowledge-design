---
name: code-review
description: Review uncommitted changes for security vulnerabilities, code quality issues, and best practices. Use before committing or raising a PR.
argument-hint: [path or leave blank for staged changes]
---

# Code Review

Comprehensive security and quality review of uncommitted changes.

## Step 1: Get Changed Files

```bash
git diff --name-only HEAD
```

## Step 2: For Each Changed File, Check

**Security (CRITICAL):**
- Hardcoded credentials, API keys, tokens
- SQL injection vulnerabilities
- XSS vulnerabilities
- Missing input validation
- Insecure dependencies
- Path traversal risks

**Code Quality (HIGH):**
- Functions > 50 lines
- Files > 800 lines
- Nesting depth > 4 levels
- Missing error handling
- `console.log` / debug statements left in
- TODO/FIXME comments in new code
- Missing tests for new logic

**Best Practices (MEDIUM):**
- Mutation patterns (prefer immutable)
- Missing tests for new code
- Accessibility issues (a11y) for UI code

## Step 3: Generate Report

```
SEVERITY  | FILE:LINE | ISSUE | SUGGESTED FIX
CRITICAL  | ...       | ...   | ...
HIGH      | ...       | ...   | ...
MEDIUM    | ...       | ...   | ...
```

## Step 4: Verdict

- **CRITICAL or HIGH found** → Block. List every issue. Do not approve.
- **MEDIUM only** → Approve with caveats. List issues for author awareness.
- **Clean** → APPROVED.

Never approve code with security vulnerabilities.
