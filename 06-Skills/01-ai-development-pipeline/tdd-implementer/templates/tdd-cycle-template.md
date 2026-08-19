# TDD Cycles — {JIRA-KEY}

> Log of every TDD cycle (RED → GREEN → REFACTOR → Commit) for this ticket.

---

## TDD Cycle 1: {feature being implemented}

### 🔴 RED — Write Failing Test
**Test file:** `{path/to/TestClass.java}`
**Test method:** `{methodName}`
**Test code:**
```java
{test code}
```

**Run command:** `{command}`
**Result:** ❌ FAILED
**Failure output:**
```
{test failure output}
```

### 🟢 GREEN — Write Minimal Code
**Production file:** `{path/to/Class.java}`
**Code added:**
```java
{minimal code}
```

**Run command:** `{command}`
**Result:** ✅ PASSED
**Output:**
```
{test pass output}
```

### 🔵 REFACTOR — Eliminate Redundancy
**Changes:**
- {refactoring change 1}
- {refactoring change 2}

**Run command:** `{command}`
**Result:** ✅ PASSED (all tests still green)

### 💾 Commit
**Commit hash:** `{hash}`
**Commit message:** `{message}`

---

## TDD Cycle 2: {feature being implemented}

### 🔴 RED — Write Failing Test
**Test file:** `{path/to/TestClass.java}`
**Test method:** `{methodName}`
**Test code:**
```java
{test code}
```

**Run command:** `{command}`
**Result:** ❌ FAILED
**Failure output:**
```
{test failure output}
```

### 🟢 GREEN — Write Minimal Code
**Production file:** `{path/to/Class.java}`
**Code added:**
```java
{minimal code}
```

**Run command:** `{command}`
**Result:** ✅ PASSED
**Output:**
```
{test pass output}
```

### 🔵 REFACTOR — Eliminate Redundancy
**Changes:**
- {refactoring change 1}

**Run command:** `{command}`
**Result:** ✅ PASSED (all tests still green)

### 💾 Commit
**Commit hash:** `{hash}`
**Commit message:** `{message}`

---

## Summary

| Cycle | Feature | Test File | Commit | Status |
|-------|---------|-----------|--------|--------|
| 1 | {feature} | {file} | {hash} | ✅ |
| 2 | {feature} | {file} | {hash} | ✅ |

**Total cycles:** {count}
**Total commits:** {count}
**All tests pass:** ✅ / ❌
**Final test run:** `{command}` → {passed} passed, {failed} failed → exit {code}
