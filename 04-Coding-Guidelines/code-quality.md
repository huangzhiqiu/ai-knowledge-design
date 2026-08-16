# Code Quality Standards

> Code quality metrics, thresholds, and enforcement based on SonarQube and industry standards.

## Quality Metrics & Thresholds

### Complexity

| Metric | Threshold | Description |
|--------|-----------|-------------|
| Cyclomatic Complexity | ≤ 10 per function | Independent paths through code |
| Cognitive Complexity | ≤ 15 per function | Human-readability complexity |
| Class complexity | ≤ 200 methods | Avoid God classes |
| Nesting depth | ≤ 4 levels | Deep nesting is hard to follow |

### Size

| Metric | Threshold |
|--------|-----------|
| Method length | ≤ 50 lines |
| Class length | ≤ 500 lines |
| File length | ≤ 1000 lines |
| Parameter count | ≤ 7 per method |
| Line length | ≤ 120 characters |

### Duplication

| Metric | Threshold |
|--------|-----------|
| Duplicated lines | ≤ 3% of total |
| Duplicated blocks | ≥ 10 lines considered duplicate |
| Same file duplication | 0 (refactor immediately) |

### Coverage

| Metric | Threshold |
|--------|-----------|
| Line coverage | ≥ 80% |
| Branch coverage | ≥ 75% |
| New code coverage | ≥ 90% |
| Critical code coverage | 100% (security, payment) |

### Issues

| Severity | Threshold |
|----------|-----------|
| Blocker | 0 |
| Critical | 0 |
| Major | ≤ 5 (or 0 for new code) |
| Minor | ≤ 20 |
| Code smells | ≤ 10 per 1000 lines |

## SonarQube Quality Gate

### Default Quality Gate (Sonar Way)

| Condition | Operator | Value |
|-----------|----------|-------|
| Coverage on New Code | is less than | 80.0% |
| Duplicated Lines on New Code | is greater than | 3.0% |
| Reliability Rating on New Code | is worse than | A |
| Security Rating on New Code | is worse than | A |
| Maintainability Rating on New Code | is worse than | A |
| Security Hotspots Reviewed on New Code | is less than | 100% |

### Ratings

| Rating | Reliability/Security/Maintainability |
|--------|--------------------------------------|
| A | 0 bugs/vulnerabilities/code smells |
| B | ≤ 0.1% of cost |
| C | ≤ 1% of cost |
| D | ≤ 20% of cost |
| E | > 20% of cost |

## Code Smells (Common)

### Major Issues (must fix)

| Smell | Description | Fix |
|-------|-------------|-----|
| Duplicated code | Copy-pasted blocks | Extract method/class |
| Long method | > 50 lines | Extract methods |
| God class | Does everything | Split by responsibility |
| Long parameter list | > 7 params | Use parameter object |
| Deep nesting | > 4 levels | Early return, guard clauses |
| Shotgun surgery | Change requires editing many files | Consolidate logic |
| Feature envy | Method uses another class more than own | Move method |
| Data clumps | Same group of fields together | Extract object |

### Minor Issues (should fix)

| Smell | Description |
|-------|-------------|
| Magic numbers | Unnamed numeric literals |
| Dead code | Unused variables, methods, classes |
| Commented-out code | Should be removed (Git history) |
| Empty catch block | Swallows exceptions |
| Too many imports | Indicates high coupling |
| Inconsistent naming | Violates naming conventions |

## Refactoring Techniques

### Extract Method
```java
// Before
public void printInvoice() {
    printHeader();
    for (Item item : items) {
        System.out.println(item.getName() + " " + item.getPrice());
    }
    printFooter();
}

// After
public void printInvoice() {
    printHeader();
    printItems();
    printFooter();
}

private void printItems() {
    for (Item item : items) {
        System.out.println(item.getName() + " " + item.getPrice());
    }
}
```

### Replace Nested Conditional with Guard Clauses
```java
// Before
public double getPayAmount() {
    double result;
    if (isDead) {
        result = deadAmount();
    } else {
        if (isSeparated) {
            result = separatedAmount();
        } else {
            if (isRetired) {
                result = retiredAmount();
            } else {
                result = normalPayAmount();
            }
        }
    }
    return result;
}

// After
public double getPayAmount() {
    if (isDead) return deadAmount();
    if (isSeparated) return separatedAmount();
    if (isRetired) return retiredAmount();
    return normalPayAmount();
}
```

## Testing Quality

### Test Principles (AIR)
- **A**utomatic: runs without manual intervention
- **I**ndependent: no dependencies between tests
- **R**epeatable: same result every time

### Test Coverage (BCDE)
- **B**order: boundary values
- **C**orrect: correct input/output
- **D**esign: design-level testing
- **E**rror: error conditions and exceptions

### Test Naming
```java
// Pattern: methodName_condition_expectedResult
@Test
void sendMessage_emptyContent_throwsValidationException() { ... }

@Test
void sendMessage_validMessage_returnsWithSeqId() { ... }
```

### Test Structure (AAA)
```java
@Test
void testSomething() {
    // Arrange
    User user = new User("test");
    
    // Act
    userService.create(user);
    
    // Assert
    assertNotNull(user.getId());
}
```

## Code Review Checklist

### Correctness
- [ ] Logic is correct and handles edge cases
- [ ] No off-by-one errors
- [ ] Null checks where needed
- [ ] Error handling complete

### Security
- [ ] Input validation present
- [ ] No SQL injection (parameterized queries)
- [ ] No hardcoded secrets
- [ ] Authorization checks in place

### Performance
- [ ] No N+1 queries
- [ ] No unnecessary database calls in loops
- [ ] Appropriate caching
- [ ] Efficient data structures chosen

### Maintainability
- [ ] Methods ≤ 50 lines
- [ ] Classes have single responsibility
- [ ] No duplicated code
- [ ] Meaningful names
- [ ] Comments explain "why", not "what"

### Testing
- [ ] Unit tests for new logic
- [ ] Edge cases covered
- [ ] Tests are independent and repeatable
- [ ] No flaky tests

## CI/CD Quality Gates

1. **Build**: compiles successfully
2. **Unit tests**: all pass
3. **Static analysis**: SonarQube quality gate passed
4. **Dependency scan**: no critical vulnerabilities
5. **Code coverage**: ≥ threshold
6. **Integration tests**: pass (if applicable)
7. **Manual review**: approved by reviewer(s)

## References
- SonarQube Rules: https://rules.sonarsource.com/java
- Clean Code by Robert C. Martin
- Refactoring by Martin Fowler
- Google Engineering Practices Documentation
