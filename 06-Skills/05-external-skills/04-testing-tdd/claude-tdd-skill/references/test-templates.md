# Test Templates by Framework

Stack-specific test templates for the RED phase.

## JavaScript/TypeScript

### Jest
```typescript
// Feature: [FEATURE_NAME]
// Behavior: [WHAT_IT_SHOULD_DO]

describe('[FeatureName]', () => {
  describe('[methodName]', () => {
    it('should [expected behavior] when [condition]', () => {
      // Arrange
      const input = /* setup */;
      const expected = /* expected result */;

      // Act
      const result = /* call method */;

      // Assert
      expect(result).toEqual(expected);
    });

    it('should throw [ErrorType] when [invalid condition]', () => {
      // Arrange
      const invalidInput = /* invalid setup */;

      // Act & Assert
      expect(() => /* call method */).toThrow(/* ErrorType */);
    });
  });
});
```

### Jest with Async
```typescript
describe('[AsyncFeature]', () => {
  it('should resolve with [expected] when [condition]', async () => {
    // Arrange
    const input = /* setup */;

    // Act
    const result = await /* async call */;

    // Assert
    expect(result).toEqual(/* expected */);
  });

  it('should reject when [error condition]', async () => {
    // Arrange & Act & Assert
    await expect(/* async call */).rejects.toThrow(/* ErrorType */);
  });
});
```

### Vitest
```typescript
import { describe, it, expect, vi } from 'vitest';

describe('[FeatureName]', () => {
  it('should [behavior]', () => {
    // Same structure as Jest
  });
});
```

## Python

### pytest
```python
# test_[module_name].py
import pytest
from [module] import [function_or_class]


class TestFeatureName:
    """Tests for [FeatureName]"""

    def test_should_behavior_when_condition(self):
        """[Expected behavior] when [condition]"""
        # Arrange
        input_value = ...
        expected = ...

        # Act
        result = function_or_class(input_value)

        # Assert
        assert result == expected

    def test_should_raise_error_when_invalid(self):
        """Should raise [ErrorType] when [invalid condition]"""
        # Arrange
        invalid_input = ...

        # Act & Assert
        with pytest.raises(ErrorType):
            function_or_class(invalid_input)


# Parametrized test
@pytest.mark.parametrize("input_val,expected", [
    (1, "one"),
    (2, "two"),
    (3, "three"),
])
def test_should_map_correctly(input_val, expected):
    assert map_number(input_val) == expected
```

### pytest with Fixtures
```python
import pytest

@pytest.fixture
def sample_data():
    """Provides sample test data"""
    return {"key": "value"}

@pytest.fixture
def mock_service(mocker):
    """Mocks external service"""
    return mocker.patch('module.ExternalService')


def test_with_fixture(sample_data, mock_service):
    # Use fixtures
    mock_service.return_value.get.return_value = sample_data
    result = process_data()
    assert result["key"] == "value"
```

## C# / .NET

### xUnit
```csharp
// [FeatureName]Tests.cs
using Xunit;
using FluentAssertions;

namespace Project.Tests;

public class FeatureNameTests
{
    [Fact]
    public void MethodName_Should_Behavior_When_Condition()
    {
        // Arrange
        var sut = new FeatureName();
        var input = /* setup */;
        var expected = /* expected */;

        // Act
        var result = sut.MethodName(input);

        // Assert
        result.Should().Be(expected);
    }

    [Fact]
    public void MethodName_Should_Throw_When_InvalidCondition()
    {
        // Arrange
        var sut = new FeatureName();
        var invalidInput = /* invalid */;

        // Act
        var act = () => sut.MethodName(invalidInput);

        // Assert
        act.Should().Throw<InvalidOperationException>()
           .WithMessage("*expected message*");
    }

    [Theory]
    [InlineData(1, "one")]
    [InlineData(2, "two")]
    [InlineData(3, "three")]
    public void MethodName_Should_MapCorrectly(int input, string expected)
    {
        var result = sut.MapNumber(input);
        result.Should().Be(expected);
    }
}
```

### xUnit with Async
```csharp
public class AsyncFeatureTests
{
    [Fact]
    public async Task MethodName_Should_ReturnExpected_When_Condition()
    {
        // Arrange
        var sut = new AsyncFeature();

        // Act
        var result = await sut.GetDataAsync();

        // Assert
        result.Should().NotBeNull();
    }
}
```

### NUnit
```csharp
using NUnit.Framework;

[TestFixture]
public class FeatureNameTests
{
    [Test]
    public void MethodName_Should_Behavior_When_Condition()
    {
        // Arrange
        var sut = new FeatureName();

        // Act
        var result = sut.MethodName();

        // Assert
        Assert.That(result, Is.EqualTo(expected));
    }

    [TestCase(1, "one")]
    [TestCase(2, "two")]
    public void MethodName_Should_MapCorrectly(int input, string expected)
    {
        var result = sut.MapNumber(input);
        Assert.That(result, Is.EqualTo(expected));
    }
}
```

## Go

### Go Test
```go
// feature_test.go
package mypackage

import (
    "testing"
    "github.com/stretchr/testify/assert"
    "github.com/stretchr/testify/require"
)

func TestFeatureName_Should_Behavior_When_Condition(t *testing.T) {
    // Arrange
    input := /* setup */
    expected := /* expected */

    // Act
    result := FeatureName(input)

    // Assert
    assert.Equal(t, expected, result)
}

func TestFeatureName_Should_Error_When_Invalid(t *testing.T) {
    // Arrange
    invalidInput := /* invalid */

    // Act
    _, err := FeatureName(invalidInput)

    // Assert
    require.Error(t, err)
    assert.Contains(t, err.Error(), "expected message")
}

// Table-driven test
func TestFeatureName_Mapping(t *testing.T) {
    tests := []struct {
        name     string
        input    int
        expected string
    }{
        {"one", 1, "one"},
        {"two", 2, "two"},
    }

    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            result := MapNumber(tt.input)
            assert.Equal(t, tt.expected, result)
        })
    }
}
```

## Java

### JUnit 5
```java
// FeatureNameTest.java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class FeatureNameTest {

    @Test
    @DisplayName("should [behavior] when [condition]")
    void methodName_Should_Behavior_When_Condition() {
        // Arrange
        var sut = new FeatureName();
        var input = /* setup */;
        var expected = /* expected */;

        // Act
        var result = sut.methodName(input);

        // Assert
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should throw when [invalid condition]")
    void methodName_Should_Throw_When_Invalid() {
        // Arrange
        var sut = new FeatureName();
        var invalidInput = /* invalid */;

        // Act & Assert
        assertThatThrownBy(() -> sut.methodName(invalidInput))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expected");
    }

    @ParameterizedTest
    @CsvSource({
        "1, one",
        "2, two",
        "3, three"
    })
    void methodName_Should_MapCorrectly(int input, String expected) {
        var result = sut.mapNumber(input);
        assertThat(result).isEqualTo(expected);
    }
}
```

## Rust

### Rust Test
```rust
// In src/lib.rs or tests/feature_test.rs

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn feature_name_should_behavior_when_condition() {
        // Arrange
        let input = /* setup */;
        let expected = /* expected */;

        // Act
        let result = feature_name(input);

        // Assert
        assert_eq!(result, expected);
    }

    #[test]
    #[should_panic(expected = "error message")]
    fn feature_name_should_panic_when_invalid() {
        let invalid_input = /* invalid */;
        feature_name(invalid_input); // Should panic
    }

    #[test]
    fn feature_name_should_return_error_when_invalid() -> Result<(), String> {
        let invalid_input = /* invalid */;
        match feature_name(invalid_input) {
            Err(e) => {
                assert!(e.contains("expected"));
                Ok(())
            }
            Ok(_) => Err("Expected error".to_string()),
        }
    }
}
```

## Test Naming Conventions

| Framework | Convention |
|-----------|------------|
| Jest/Vitest | `it('should [behavior] when [condition]')` |
| pytest | `test_should_behavior_when_condition` |
| xUnit | `MethodName_Should_Behavior_When_Condition` |
| NUnit | `MethodName_Should_Behavior_When_Condition` |
| Go | `TestFeatureName_Should_Behavior_When_Condition` |
| JUnit | `methodName_Should_Behavior_When_Condition` |
| Rust | `feature_name_should_behavior_when_condition` |

## Assertion Libraries

| Language | Recommended |
|----------|-------------|
| JS/TS | Jest built-in, `expect` |
| Python | pytest built-in, `assert` |
| C# | FluentAssertions |
| Go | testify/assert |
| Java | AssertJ |
| Rust | built-in `assert!`, `assert_eq!` |
