# TDD Implementer

> Skill for Stage 3 of the ticket-to-deploy workflow: implement code using Test-Driven Development (TDD), injecting knowledge from the CBOL knowledge base and coding guidelines.

## Purpose

Implement the code for a CBOL ticket following strict TDD discipline: RED → GREEN → REFACTOR → Commit. Each cycle produces a failing test, minimal code to pass, refactoring, and a commit. The skill injects coding guidelines, Java implementation references, and code templates from the knowledge base.

**TDD Discipline (from Jira-Flow best practices):**
- 🔴 RED: Write a failing test first, run it, report failure
- 🟢 GREEN: Write ONLY the minimal code to make the test pass, run it, report success
- 🔵 REFACTOR: Refactor to eliminate redundancy, ensure tests still pass
- 💾 Commit: Commit the atomic TDD cycle

**Prohibited:** Writing production code before a failing test exists.

## Input

| Parameter | Required | Description |
|-----------|----------|-------------|
| `jira_key` | Yes | Jira issue key, e.g. `CBOL-123` |
| `sdd_doc` | No | Path to SDD (default: `design/sdd/{KEY}.md`) |
| `start_from` | No | Resume from a specific TDD cycle number |

## Output

1. **Code**: Production code + unit tests in the project source tree
2. **Operation log**: `docs/operations/{JIRA-KEY}/03-implementation-v{N}.md`
3. **TDD cycle log**: `docs/operations/{JIRA-KEY}/tdd-cycles.md` (one entry per cycle)

## TDD Cycle Template

Each TDD cycle follows this exact sequence:

```markdown
## TDD Cycle {N}: {feature being implemented}

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
```

## Workflow

1. **Read SDD**: Parse the SDD to identify implementation units (each unit = one TDD cycle)
2. **Knowledge injection**:
   - Read `04-Coding-Guidelines/` (all documents)
   - Read `02-Chat-Domain-Knowledge/java-implementation/`
   - Read `02-Chat-Domain-Knowledge/code-templates/`
   - Read `02-Chat-Domain-Knowledge/data-structures/`
   - Read relevant `02-Chat-Domain-Knowledge/` documents by keyword
3. **Plan TDD cycles**: Break the implementation into atomic units (2-5 minutes each)
4. **Execute each TDD cycle**:
   a. Write failing test (RED)
   b. Run test, verify it fails (report output)
   c. Write minimal production code (GREEN)
   d. Run test, verify it passes (report output)
   e. Refactor (REFACTOR)
   f. Run all tests, verify still green
   g. Commit (atomic commit)
   h. Log the cycle
5. **After all cycles**: Run full test suite, collect evidence
6. **Write operation log** with summary, evidence, and handoff notes

## Knowledge Injection Checklist

Before starting implementation, the skill MUST have read:

- [ ] `04-Coding-Guidelines/java-coding-standards.md`
- [ ] `04-Coding-Guidelines/security-guidelines.md`
- [ ] `04-Coding-Guidelines/code-quality.md`
- [ ] `04-Coding-Guidelines/concurrency-guidelines.md`
- [ ] `04-Coding-Guidelines/exception-and-logging.md`
- [ ] `04-Coding-Guidelines/websocket-guidelines.md` (if WebSocket changes)
- [ ] `02-Chat-Domain-Knowledge/java-implementation/README.md`
- [ ] `02-Chat-Domain-Knowledge/data-structures/README.md`
- [ ] Relevant `02-Chat-Domain-Knowledge/code-templates/`

## Implementation Rules

### Naming Conventions
- Classes: PascalCase (`MessageService`, `ConversationHandler`)
- Methods: camelCase (`sendMessage`, `getConversation`)
- Constants: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT_MS`)
- Test classes: `{ClassUnderTest}Test`
- Test methods: `should{Behavior}When{Condition}`

### Package Structure
```
com.selfdevelopment.ai.messaging
├── domain/          # Domain entities, value objects, domain services
├── application/     # Application services, use cases
├── infrastructure/  # Repository implementations, external clients
├── interfaces/      # REST controllers, WebSocket handlers
└── config/          # Configuration classes
```

### Error Handling
- Use custom exception hierarchy (reference `04-Coding-Guidelines/exception-and-logging.md`)
- Never swallow exceptions
- Wrap checked exceptions in unchecked domain exceptions
- Include error code, message, and context in every exception

### Logging
- Use SLF4J with Logback
- Structured logging with MDC (traceId, userId, conversationId)
- Log entry/exit for service methods
- Log state transitions
- Never log sensitive data (passwords, tokens, PII)

### Concurrency
- Use `ConcurrentHashMap` for shared state (reference Turms lock-free design)
- Use `AtomicReference` / `AtomicLong` for counters
- Avoid `synchronized` blocks — use CAS where possible
- Use `CompletableFuture` for async operations
- Thread pool sizing: `Runtime.getRuntime().availableProcessors()` for CPU-bound (reference Turms)

## Evidence Collection

After each TDD cycle and at the end of implementation, collect:

```
## Evidence
- Test run command: `{command}`
- Test output: {passed count} passed, {failed count} failed
- Exit code: {0/1}
- Coverage: {line coverage}% line, {branch coverage}% branch
- Files changed: {count}
- Commits: {list of commit hashes}
```

## Quality Gates

Implementation is considered complete if ALL are true:
- [ ] Every acceptance criterion from SDD has corresponding code + tests
- [ ] All TDD cycles followed RED→GREEN→REFACTOR→Commit
- [ ] No production code was written before a failing test
- [ ] All tests pass (exit code 0)
- [ ] Line coverage >= 80%
- [ ] No Sonar critical/blocker issues
- [ ] Code follows all coding guidelines
- [ ] Operation log has complete evidence

## Usage

```
/tdd-implementer jira_key=CBOL-123
```

Resume from a specific cycle:
```
/tdd-implementer jira_key=CBOL-123 start_from=5
```

## Related Skills

- `workflow-ticket-to-deploy` — Orchestrator that invokes this skill at Stage 3
- `sdd-generator` — Provides the SDD that drives implementation
- `test-verifier` — Stage 4: verifies tests and collects evidence
- `code-analyzer` — Analyzes code quality and standards compliance

## References

- TDD (Kent Beck): https://en.wikipedia.org/wiki/Test-driven_development
- Jira-Flow TDD discipline: https://github.com/jinx911/jira-flow
- Google Java Style: https://google.github.io/styleguide/javaguide.html
- CBOL coding guidelines: `04-Coding-Guidelines/`
