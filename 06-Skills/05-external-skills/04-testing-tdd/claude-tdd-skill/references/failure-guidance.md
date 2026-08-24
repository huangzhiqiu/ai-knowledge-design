# Interactive Failure Guidance Reference

Patterns for helping developers when tests fail during the GREEN phase.

## Failure Analysis Framework

When a test fails, follow this diagnostic process:

```
1. Read the error message carefully
2. Identify the failure category
3. Provide targeted guidance
4. Offer specific next steps
```

## Failure Categories

### 1. Compilation/Syntax Errors

**Symptoms:**
- Code won't compile
- Syntax errors
- Type mismatches

**Guidance Template:**
```
The test can't run due to a compilation error:

Error: [specific error]
Location: [file:line]

This is typically caused by:
- [ ] Missing import/using statement
- [ ] Typo in function/variable name
- [ ] Type mismatch
- [ ] Missing dependency

Suggested fix: [specific fix based on error]
```

**Common Fixes by Language:**

| Language | Common Fix |
|----------|------------|
| TypeScript | Add missing type, fix import path |
| Python | Fix indentation, add import |
| C# | Add using, fix namespace |
| Go | Fix package import, add return type |

### 2. Test Framework Errors

**Symptoms:**
- Test not found
- Assertion method doesn't exist
- Setup/teardown failure

**Guidance Template:**
```
The test framework reported an issue:

Error: [framework error]

Possible causes:
- [ ] Missing test decorator/attribute
- [ ] Incorrect assertion method
- [ ] Setup fixture failed
- [ ] Test file naming convention

For [framework], ensure:
- [framework-specific checklist]
```

### 3. Assertion Failures

**Symptoms:**
- Expected vs. Actual mismatch
- Test runs but fails assertion

**Guidance Template:**
```
Test assertion failed:

Expected: [expected value]
Actual:   [actual value]

The implementation is not returning the expected result.

Analysis:
- Input:  [what was passed]
- Output: [what was returned]
- Diff:   [specific difference]

Questions to consider:
1. Is the expected value correct per the requirements?
2. Is the implementation logic correct?
3. Are there edge cases not handled?

Suggested debugging steps:
1. [specific step based on the failure]
```

### 4. Exception/Error Thrown

**Symptoms:**
- Unexpected exception
- Error thrown during execution

**Guidance Template:**
```
An exception was thrown during test execution:

Exception: [exception type]
Message:   [error message]
Stack:     [relevant stack trace lines]

This often indicates:
- [ ] Null/undefined reference
- [ ] Invalid input not handled
- [ ] Missing dependency or configuration
- [ ] Resource not found

Based on this [exception type], check:
- [specific things to check]
```

### 5. Timeout/Hang

**Symptoms:**
- Test doesn't complete
- Async operation never resolves

**Guidance Template:**
```
Test timed out after [X] seconds.

Common causes for [language/framework]:
- [ ] Async operation not awaited
- [ ] Promise never resolved
- [ ] Infinite loop
- [ ] Deadlock

Check:
1. All async operations have await/then
2. Mocks are correctly configured to resolve
3. No infinite loops in implementation
```

## Interactive Prompts by Phase

### During RED Verification

When test fails (expected):
```
Test failed as expected. The failure indicates:

[Analysis of what the test is checking]

Ready to implement? The goal is to make this test pass with
minimal code.

What's your approach? (or I can suggest the simplest implementation)
```

### During GREEN Implementation

When test still fails after implementation attempt:

**Attempt 1:**
```
Implementation attempt 1 didn't pass the test.

[Error analysis]

Suggestions:
1. [Most likely fix]
2. [Alternative approach]

Would you like to:
- Try suggestion #1
- Get more details about the error
- Take a different approach
```

**Attempt 2:**
```
Attempt 2: Test still failing.

Let's debug more carefully:

[Detailed error breakdown]

I recommend:
[Specific debugging action]

Want me to:
- Add logging to trace the issue
- Simplify the test case
- Review the requirements together
```

**Attempt 3+:**
```
Multiple attempts haven't resolved this. Let's step back.

Possible issues:
1. Test expectation might be incorrect
2. Understanding of requirements might differ
3. There may be a subtle bug in the approach

Questions:
- Should the expected value be [X] or could it be [Y]?
- Is there a prerequisite we're missing?
- Want to pair debug through this together?
```

## Error Message Parsing

### JavaScript/TypeScript (Jest)

```
FAIL src/feature.test.ts
  ● FeatureName › should do something

    expect(received).toBe(expected)

    Expected: "hello"
    Received: "world"

      10 |     const result = greet();
      11 |     expect(result).toBe("hello");
         |                    ^
```

**Parse:**
- Test: `FeatureName › should do something`
- Expected: `"hello"`
- Actual: `"world"`
- Location: `feature.test.ts:11`

### Python (pytest)

```
FAILED test_feature.py::test_should_do_something - AssertionError
>       assert result == "hello"
E       AssertionError: assert 'world' == 'hello'
E         - hello
E         + world
```

**Parse:**
- Test: `test_should_do_something`
- Expected: `'hello'`
- Actual: `'world'`
- Location: `test_feature.py`

### C# (xUnit)

```
Failed FeatureTests.Should_DoSomething [10 ms]
  Error Message:
   Assert.Equal() Failure
   Expected: hello
   Actual:   world
  Stack Trace:
     at FeatureTests.Should_DoSomething() in FeatureTests.cs:line 15
```

**Parse:**
- Test: `FeatureTests.Should_DoSomething`
- Expected: `hello`
- Actual: `world`
- Location: `FeatureTests.cs:15`

## Debugging Suggestions by Error Type

### Null Reference / Undefined

```
Suggestions for null/undefined errors:

1. Check if the object is being created:
   - Is the constructor being called?
   - Are dependencies injected?

2. Check the call chain:
   - Which property/method returns null?
   - Add null checks or logging

3. Check initialization order:
   - Is setup running before the test?
   - Are async operations completing?
```

### Type Mismatch

```
Suggestions for type mismatch:

1. Check the return type:
   - Function declares: [declared type]
   - Actually returns: [actual type]

2. Check input types:
   - Expected: [expected type]
   - Received: [received type]

3. For complex objects:
   - Use typeof/instanceof to debug
   - Check nested property types
```

### Collection/Array Issues

```
Suggestions for collection errors:

1. Check length/count:
   - Expected items: [N]
   - Actual items: [M]

2. Check element equality:
   - Order might matter
   - Deep equality vs reference

3. Check for mutations:
   - Is the original modified?
   - Are items being added/removed correctly?
```

## Checkpoint on Failure

After any failed attempt, update progress.md:

```markdown
### Cycle N: [Feature]
- [x] RED: Test written (HH:MM)
- [ ] GREEN: In progress
  - Attempt 1 (HH:MM): [error summary]
  - Attempt 2 (HH:MM): [error summary]
  - **Current status**: [what we know so far]
```

Save to context.json:
```json
{
  "current_phase": "green",
  "green_attempts": 2,
  "last_error": {
    "type": "assertion",
    "expected": "hello",
    "actual": "world",
    "location": "feature.test.ts:11"
  },
  "debugging_notes": "Suspect issue with..."
}
```

## Escalation Path

If after 3-4 attempts the test still fails:

1. **Simplify the test**
   - Can we test something smaller first?
   - Is the test doing too much?

2. **Verify requirements**
   - Re-read the original requirement
   - Ask clarifying questions

3. **Take a break / context switch**
   - Save full state
   - Document current understanding
   - Fresh eyes often help

4. **Pair programming mode**
   - Walk through code line by line
   - Explain the logic out loud
   - Question every assumption
