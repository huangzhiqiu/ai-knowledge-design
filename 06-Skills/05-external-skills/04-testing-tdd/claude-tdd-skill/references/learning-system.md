# TDD Learning System Reference

The TDD skill includes a cross-session learning system that improves recommendations over time by tracking patterns, failures, and successful strategies.

## Learning Database Structure

### Location
`.tdd/learnings.json`

### Schema

```json
{
  "version": "1.0",
  "patterns": {
    "success": [
      {
        "id": "pattern-001",
        "pattern": "Factory-based test data",
        "context": "When testing with complex entities",
        "benefit": "60% reduction in test setup code",
        "example": "Use UserFactory.create() instead of inline object",
        "learned_from": ["session-id-1", "session-id-2"],
        "frequency": 5,
        "last_seen": "2026-01-29"
      }
    ],
    "antipatterns": [
      {
        "id": "antipattern-001",
        "pattern": "Testing private methods directly",
        "context": "Internal implementation details",
        "problem": "Tests break when refactoring internals",
        "solution": "Test through public interface only",
        "example": "Instead of testing _validateInternal(), test validate()",
        "learned_from": ["session-id-3"],
        "frequency": 2,
        "last_seen": "2026-01-28"
      }
    ]
  },
  "tips": [
    {
      "id": "tip-001",
      "trigger": "async testing",
      "tip": "Always use async/await pattern with proper assertions",
      "example": "await expect(promise).resolves.toBe(value)",
      "learned_from": ["session-id-1"],
      "frequency": 3
    }
  ],
  "failure_patterns": [
    {
      "id": "failure-001",
      "error_pattern": "Cannot find module",
      "regex": "Cannot find module '([^']+)'",
      "common_cause": "Missing npm dependency",
      "quick_fix": "npm install {captured_module}",
      "frequency": 8,
      "avg_resolution_time_seconds": 30
    },
    {
      "id": "failure-002",
      "error_pattern": "Expected X, Received undefined",
      "regex": "Expected .+, Received undefined",
      "common_cause": "Function not returning value",
      "diagnostic_steps": [
        "Check function has return statement",
        "Check for early returns",
        "Add console.log before return"
      ],
      "frequency": 12
    }
  ],
  "last_updated": "2026-01-29T12:00:00Z"
}
```

## How Learning Works

### 1. Pattern Collection

During TDD sessions, agents identify patterns:

**Success Patterns** (tdd-session-reviewer):
- Techniques that reduced cycle time
- Practices that prevented failures
- Approaches that improved code quality

**Anti-patterns** (tdd-session-reviewer):
- Practices that caused problems
- Techniques that wasted time
- Approaches that led to brittle tests

### 2. Failure Pattern Learning

The `tdd-failure-analyzer` agent tracks:
- Error message patterns
- Common causes for each pattern
- Successful resolution strategies
- Time to resolution

### 3. Tip Generation

Tips are generated when:
- A pattern is seen multiple times
- A technique consistently helps
- A common mistake is identified

### 4. Pattern Application

Agents use learnings proactively:

| Agent | How It Uses Learnings |
|-------|----------------------|
| `tdd-test-designer` | Suggests proven patterns for test design |
| `tdd-test-writer` | Avoids known antipatterns |
| `tdd-failure-analyzer` | Quick-matches known failure patterns |
| `tdd-refactorer` | Suggests successful refactoring approaches |
| `tdd-session-reviewer` | Compares session to historical patterns |

## Learning Queries

### Find Applicable Success Patterns

```javascript
function findApplicablePatterns(context) {
  return learnings.patterns.success.filter(p =>
    context.includes(p.context) ||
    context.includes(p.pattern)
  );
}
```

### Match Failure Pattern

```javascript
function matchFailurePattern(errorMessage) {
  for (const pattern of learnings.failure_patterns) {
    const match = errorMessage.match(new RegExp(pattern.regex));
    if (match) {
      return {
        pattern,
        captures: match.slice(1),
        quickFix: pattern.quick_fix.replace('{captured_module}', match[1])
      };
    }
  }
  return null;
}
```

### Get Tips for Context

```javascript
function getTipsForContext(context) {
  return learnings.tips.filter(tip =>
    context.toLowerCase().includes(tip.trigger.toLowerCase())
  );
}
```

## Updating Learnings

### Adding a Success Pattern

```javascript
function addSuccessPattern(pattern) {
  const existing = learnings.patterns.success.find(p => p.pattern === pattern.pattern);
  if (existing) {
    existing.frequency++;
    existing.learned_from.push(pattern.session_id);
    existing.last_seen = new Date().toISOString();
  } else {
    learnings.patterns.success.push({
      id: generateId(),
      ...pattern,
      frequency: 1,
      last_seen: new Date().toISOString()
    });
  }
}
```

### Recording Failure Resolution

```javascript
function recordFailureResolution(errorMessage, resolution, timeSeconds) {
  const match = matchFailurePattern(errorMessage);
  if (match) {
    // Update existing pattern
    match.pattern.frequency++;
    match.pattern.avg_resolution_time_seconds =
      (match.pattern.avg_resolution_time_seconds * (match.pattern.frequency - 1) + timeSeconds)
      / match.pattern.frequency;
  } else {
    // Create new failure pattern
    learnings.failure_patterns.push({
      id: generateId(),
      error_pattern: extractPattern(errorMessage),
      regex: createRegex(errorMessage),
      common_cause: resolution.cause,
      quick_fix: resolution.fix,
      frequency: 1,
      avg_resolution_time_seconds: timeSeconds
    });
  }
}
```

## Metrics History

### Location
`.tdd/metrics-history.json`

### Schema

```json
{
  "version": "1.0",
  "sessions": [
    {
      "session_id": "2026-01-29_user-auth",
      "date": "2026-01-29",
      "mode": "feature",
      "metrics": {
        "total_cycles": 4,
        "total_tests": 12,
        "total_time_minutes": 135,
        "avg_cycle_time_minutes": 33.75,
        "avg_green_attempts": 1.8,
        "coverage_delta": 15
      }
    }
  ],
  "aggregates": {
    "total_sessions": 10,
    "total_cycles": 42,
    "total_tests_written": 156,
    "avg_cycle_time_minutes": 28.5,
    "avg_green_attempts": 1.6,
    "success_rate": 0.95
  },
  "trends": {
    "cycle_time": "improving",
    "green_attempts": "stable",
    "coverage": "improving"
  }
}
```

## Privacy Considerations

Learning data is stored locally and can be:
- **Shared** with team via git (learnings.json, stack.md)
- **Kept private** via .gitignore (context.json files)

### Recommended .gitignore

```gitignore
# Private session data
.tdd/sessions/*/context.json
.tdd/archive/*/context.json

# Optional: Keep learnings private
# .tdd/learnings.json
```

## Maintenance

### Pruning Old Patterns

Patterns not seen in 30+ days with low frequency can be pruned:

```javascript
function pruneOldPatterns() {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - 30);

  learnings.patterns.success = learnings.patterns.success.filter(p =>
    new Date(p.last_seen) > cutoff || p.frequency > 3
  );
}
```

### Exporting for Team

```bash
# Export learnings for team sharing
cp .tdd/learnings.json ./tdd-learnings-export.json

# Import team learnings
# (merge logic should be implemented by the skill)
```

## Integration with Other Skills

The learning system can be queried by:
- `spec-driven-dev` - For test design patterns
- `clean-code` - For refactoring patterns
- `test-runner` - For failure pattern matching
