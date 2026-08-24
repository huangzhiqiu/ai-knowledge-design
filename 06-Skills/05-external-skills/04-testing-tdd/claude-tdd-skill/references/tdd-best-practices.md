# TDD Best Practices Reference

Comprehensive guide to Test-Driven Development methodology.

## The TDD Cycle

### Red-Green-Refactor

```
    ┌─────────┐
    │   RED   │ ◄── Write failing test
    └────┬────┘
         │
         ▼
    ┌─────────┐
    │  GREEN  │ ◄── Write minimal code to pass
    └────┬────┘
         │
         ▼
    ┌─────────┐
    │REFACTOR │ ◄── Improve code, keep tests green
    └────┬────┘
         │
         └──────► Repeat
```

### Phase Rules

**RED Phase**
- Write ONE test at a time
- Test should fail for the RIGHT reason
- Test should be as simple as possible
- Name clearly describes expected behavior

**GREEN Phase**
- Write the MINIMUM code to pass
- "Fake it till you make it" is acceptable
- Don't optimize or clean up yet
- Just make the test pass

**REFACTOR Phase**
- Clean up code AND tests
- Remove duplication
- Improve naming
- Extract methods/functions
- Tests must stay green throughout

## Test Quality

### Good Test Characteristics (FIRST)

| Principle | Description |
|-----------|-------------|
| **F**ast | Tests run quickly (<100ms each) |
| **I**ndependent | Tests don't depend on each other |
| **R**epeatable | Same result every time |
| **S**elf-validating | Pass/fail is obvious |
| **T**imely | Written before production code |

### Arrange-Act-Assert (AAA)

```
// Arrange - Set up test data and conditions
// Act     - Execute the behavior being tested
// Assert  - Verify the expected outcome
```

Keep each section small. If Arrange is large, consider fixtures/factories.

### One Assertion Per Test

Prefer multiple focused tests over one test with many assertions:

**Bad:**
```javascript
it('should handle user', () => {
  const user = createUser('John');
  expect(user.name).toBe('John');
  expect(user.id).toBeDefined();
  expect(user.createdAt).toBeInstanceOf(Date);
  expect(user.isActive).toBe(true);
});
```

**Good:**
```javascript
describe('createUser', () => {
  it('should set the name', () => {
    const user = createUser('John');
    expect(user.name).toBe('John');
  });

  it('should generate an id', () => {
    const user = createUser('John');
    expect(user.id).toBeDefined();
  });

  // ... more focused tests
});
```

## Test Doubles

### Types of Test Doubles

| Type | Purpose | When to Use |
|------|---------|-------------|
| **Dummy** | Placeholder, never used | Satisfy parameter requirements |
| **Stub** | Returns canned answers | Control indirect inputs |
| **Spy** | Records calls | Verify indirect outputs |
| **Mock** | Verifies expectations | Complex interaction verification |
| **Fake** | Working implementation | Replace heavy dependencies |

### When to Mock

**Mock these:**
- External services (APIs, databases)
- File system operations
- Network calls
- Time/randomness
- Third-party libraries with side effects

**Don't mock these:**
- Value objects
- Pure functions
- Your own code (usually)
- Standard library (usually)

## Common TDD Patterns

### Triangulation

When unsure how to generalize, add more specific tests:

```javascript
// Test 1
it('should return 2 for add(1, 1)', () => {
  expect(add(1, 1)).toBe(2);
});

// Implementation could be: return 2;

// Test 2 - Triangulate
it('should return 5 for add(2, 3)', () => {
  expect(add(2, 3)).toBe(5);
});

// Now must generalize: return a + b;
```

### Start with the Simplest Case

Order tests from simple to complex:
1. Null/empty input
2. Single/minimal input
3. Typical case
4. Edge cases
5. Error cases

### Test Behavior, Not Implementation

**Bad (tests implementation):**
```javascript
it('should call database.save once', () => {
  userService.create(user);
  expect(database.save).toHaveBeenCalledTimes(1);
});
```

**Good (tests behavior):**
```javascript
it('should persist the user', () => {
  userService.create(user);
  const saved = userService.findById(user.id);
  expect(saved).toEqual(user);
});
```

## TDD Anti-Patterns

### Tests That Don't Fail

If a test never fails, it provides no value:
- Always verify test fails before implementation
- Mutation testing can find these

### Testing Private Methods

**Bad:**
```javascript
// Testing internal helper
it('should correctly parse date string', () => {
  expect(service._parseDate('2024-01-01')).toEqual(new Date(...));
});
```

**Good:**
```javascript
// Test through public interface
it('should accept date in ISO format', () => {
  const result = service.schedule('2024-01-01');
  expect(result.date).toEqual(new Date(...));
});
```

### Overly DRY Tests

Tests can repeat themselves - clarity matters more than DRYness:

**Too DRY:**
```javascript
const testCases = [/* complex setup */];
testCases.forEach(tc => {
  it(`should ${tc.description}`, () => {
    expect(func(tc.input)).toBe(tc.expected);
  });
});
```

Sometimes explicit tests are clearer, even if repetitive.

### Test Pollution

Tests that affect each other:
- Shared mutable state
- Tests that must run in order
- Tests that don't clean up

Fix with: `beforeEach`, `afterEach`, test isolation.

## Red Phase Tips

### Make It Fail First

Before writing implementation:
1. Run the test
2. Confirm it fails
3. Read the failure message
4. Ensure it fails for the expected reason

### Descriptive Test Names

Use this pattern: `should [expected behavior] when [condition]`

Examples:
- `should return empty array when input is null`
- `should throw ValidationError when email is invalid`
- `should calculate total including tax when items are present`

### Start from the Assertion

Write tests backwards:
1. What do I want to assert?
2. What action produces that result?
3. What setup do I need?

## Green Phase Tips

### The Simplest Thing That Works

Literally the simplest:
```javascript
// Test: should return 'hello' for greeting()
// Simplest implementation:
function greeting() {
  return 'hello';
}
```

Then add more tests to drive out generalization.

### Transformation Priority Premise

Order of transformations from simple to complex:
1. `null` → constant
2. constant → variable
3. unconditional → conditional
4. scalar → collection
5. statement → recursion

Prefer earlier transformations over later ones.

## Refactor Phase Tips

### Code Smells to Address

- **Duplication**: Extract method/constant
- **Long method**: Break into smaller methods
- **Poor naming**: Rename for clarity
- **Magic numbers**: Extract to constants
- **Deep nesting**: Simplify conditions

### Refactoring with Confidence

1. Ensure all tests pass
2. Make ONE small change
3. Run tests
4. If green, continue
5. If red, revert and try smaller change

### Test Refactoring

Tests need refactoring too:
- Remove duplication (shared fixtures)
- Improve test names
- Simplify assertions
- Extract test helpers

## TDD for Different Scenarios

### Bug Fixes

1. Write test that reproduces the bug (RED)
2. Verify it fails for the right reason
3. Fix the bug (GREEN)
4. Refactor if needed
5. Bug is now covered by regression test

### Legacy Code

1. Identify change point
2. Write characterization tests (capture current behavior)
3. Make changes with TDD
4. Keep characterization tests as safety net

### Refactoring Existing Code

1. Ensure existing tests cover the code
2. If not, add characterization tests
3. Refactor in small steps
4. Run tests after each step

## Metrics for TDD Success

| Metric | Target |
|--------|--------|
| Test coverage | 70-90% (higher for critical paths) |
| Test execution time | < 10 seconds for unit tests |
| Mutation score | > 80% |
| Defect rate | Should decrease over time |
| Time in red | Minimize (small failing tests) |

## Resources

- [Test-Driven Development by Example](https://www.amazon.com/Test-Driven-Development-Kent-Beck/dp/0321146530) - Kent Beck
- [Growing Object-Oriented Software, Guided by Tests](https://www.amazon.com/Growing-Object-Oriented-Software-Guided-Tests/dp/0321503627) - Freeman & Pryce
- [Working Effectively with Legacy Code](https://www.amazon.com/Working-Effectively-Legacy-Michael-Feathers/dp/0131177052) - Michael Feathers
