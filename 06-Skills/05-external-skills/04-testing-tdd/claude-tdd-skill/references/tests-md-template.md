# Test Documentation Template

This template defines the format for `.tdd/tests.md` - a cumulative test documentation file updated after each TDD session.

## File Structure

```markdown
# Test Documentation

This document describes all tests written during TDD sessions, organized by test class.

**Last Updated**: YYYY-MM-DD HH:MM:SS
**Total Tests**: N

---

## Table of Contents

1. [TestClassName1](#testclassname1)
2. [TestClassName2](#testclassname2)
...

---

## TestClassName1

**Session**: YYYY-MM-DD_session-name
**File**: path/to/TestFile.cs (or .ts, .js, etc.)

Brief description of what this test class covers.

| Test Name | Description | Input | Expected Result |
|-----------|-------------|-------|-----------------|
| `TestMethodName1` | Short description of test purpose | Key inputs summarized | Expected outcome |
| `TestMethodName2` | Short description of test purpose | Key inputs summarized | Expected outcome |

---
```

## Column Guidelines

### Test Name
- Use backticks for code formatting
- Include full method name as written in code
- Example: `` `Should_ValidateEmail_WhenFormatIsCorrect` ``

### Description
- Write in active voice: "Verifies...", "Ensures...", "Confirms..."
- Keep concise: 5-15 words
- Focus on the "why" not the "how"
- Good: "Verifies valid email format passes validation"
- Bad: "Tests the email validation method with a valid email string"

### Input
- Summarize key test inputs
- Use format: `ParameterName: value`
- For complex objects: show key properties only
- Multiple inputs: separate with commas or line breaks
- Examples:
  - `Email: "user@example.com"`
  - `ECM: 30, Speed: 50`
  - `State: InTrip, Records: 3`

### Expected Result
- Start with action verb: Returns, Throws, Sets, Creates, etc.
- Include specific values where meaningful
- Examples:
  - `Returns true, no validation errors`
  - `Throws ArgumentNullException`
  - `Status changes to InTrip, TripStarted event published`

## Description Generation from Test Names

### Common Patterns

| Test Name Pattern | Description Template |
|-------------------|---------------------|
| `Should_[Action]_When_[Condition]` | Verifies [action] when [condition] |
| `[Method]_With[Input]_Returns[Output]` | Verifies [method] returns [output] with [input] |
| `[Method]_Throws[Exception]_When[Condition]` | Verifies [method] throws [exception] when [condition] |
| `[Method]_[Scenario]_[ExpectedBehavior]` | Verifies [method] [expected behavior] in [scenario] |
| `Test[Feature][Behavior]` | Verifies [feature] [behavior] |

### Examples

| Test Name | Generated Description |
|-----------|----------------------|
| `Should_ValidateEmail_WhenFormatIsCorrect` | Verifies email validation passes when format is correct |
| `ProcessRecord_WithEcm30_StartsTripAndPublishes` | Verifies ECM30 starts trip and publishes event |
| `Constructor_NullLogger_ThrowsArgumentNullException` | Verifies constructor throws ArgumentNullException when logger is null |
| `GetRecordsToTrim_WhenDisabled_ReturnsEmptyResult` | Verifies trimming returns empty when disabled |

## Organizing Tests

### By Test Class
- Group all tests from the same class together
- Add session reference for traceability
- Include file path for navigation

### Section Order
1. Constructor/Setup tests
2. Happy path tests
3. Edge case tests
4. Error handling tests
5. Integration tests

### Subsections (optional)
For large test classes, use subsections:

```markdown
## DeviceGrainTests

### Trip Start Tests

| Test Name | Description | Input | Expected Result |
|-----------|-------------|-------|-----------------|
| ... | ... | ... | ... |

### Trip End Tests

| Test Name | Description | Input | Expected Result |
|-----------|-------------|-------|-----------------|
| ... | ... | ... | ... |
```

## Updating tests.md

### When Adding New Tests
1. Find or create section for test class
2. Append new rows to the table
3. Update "Total Tests" count in header
4. Update "Last Updated" timestamp

### When Tests Are Renamed
1. Find existing row by old name
2. Update test name column
3. Update description if meaning changed

### When Tests Are Deleted
1. Remove row from table
2. Update "Total Tests" count
3. Add note in session if significant

## Example Complete Entry

```markdown
## DeviceGrainTests

**Session**: 2026-01-30_tripbuilder-refactor
**File**: Ituran.Bi.TripBuilder.Orleans.UnitTests/Grains/DeviceGrainTests.cs

Core grain functionality tests for the device grain that manages trip state.

| Test Name | Description | Input | Expected Result |
|-----------|-------------|-------|-----------------|
| `ProcessRecordAsync_WithEcm30_StartsTripAndPublishes` | Verifies ECM30 (engine start) initiates a new trip | DeviceRecord: ECM=30, Mileage=1000 | TripStarted=true, Status=InTrip, event published |
| `ProcessRecordAsync_WithEcm41_EndsTripAndPublishes` | Verifies ECM41 (key off) ends an active trip | DeviceRecord: ECM=41 (after trip) | TripEnded=true, Status=NonTrip, event published |
| `ForceEndTripAsync_WhenInTrip_EndsTrip` | Verifies forced termination for timeout scenarios | Active trip, EndReason=NoRecordsReceived | Returns true, TripEnded published |
| `ForceEndTripAsync_WhenNotInTrip_ReturnsFalse` | Verifies force end is no-op when no trip active | No active trip | Returns false |
```
