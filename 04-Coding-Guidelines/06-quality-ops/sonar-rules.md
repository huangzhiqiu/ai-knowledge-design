# SonarQube Rules & Quality Configuration

> SonarQube quality gates, rule profiles, and Java-specific rule configurations for CBOL Messaging Hub.

## Quality Gate

### Default Quality Gate (CBOL Standard)

| Metric | Threshold | Operator | Severity |
|--------|-----------|----------|----------|
| Coverage | >= 80% | Less than | Error |
| New Code Coverage | >= 80% | Less than | Error |
| Duplicated Lines | <= 3% | Greater than | Error |
| New Duplicated Lines | <= 3% | Greater than | Error |
| Issues (Critical) | 0 | Greater than | Error |
| Issues (Blocker) | 0 | Greater than | Error |
| Issues (Major, new code) | <= 5 | Greater than | Warning |
| Security Hotspots Reviewed | 100% | Less than | Error |
| Vulnerabilities | 0 | Greater than | Error |
| Maintainability Rating | A | Worse than | Error |
| Reliability Rating | A | Worse than | Error |
| Security Rating | A | Worse than | Error |
| Complexity / Function | <= 15 | Greater than | Warning |
| Cognitive Complexity | <= 15 | Greater than | Warning |

### Quality Gate Conditions (sonar-project.properties)

```properties
# Quality gate waits for result
sonar.qualitygate.wait=true
sonar.qualitygate.timeout=300

# Coverage
sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
sonar.coverage.exclusions=
  **/config/**,
  **/dto/**,
  **/entity/**,
  **/*Application.java,
  **/proto/**,
  **/generated/**

# Duplication exclusions
sonar.cpd.exclusions=
  **/dto/**,
  **/entity/**,
  **/config/**

# Source encoding
sonar.sourceEncoding=UTF-8
sonar.java.source=17
sonar.java.target=17
```

## Rule Profiles

### Java Quality Profile (CBOL-Java)

Built on Sonar way (default), with custom activation/deactivation:

#### Activated (Additional Rules)

| Rule Key | Name | Severity | Type |
|----------|------|----------|------|
| `java:S117` | Variable names should comply with a naming convention | Minor | Code Smell |
| `java:S115` | Constant names should comply with a naming convention | Minor | Code Smell |
| `java:S101` | Class names should comply with a naming convention | Minor | Code Smell |
| `java:S106` | Standard outputs should not be used directly to log anything | Major | Code Smell |
| `java:S119` | Generic type parameters should be named with a single letter | Minor | Code Smell |
| `java:S125` | Sections of code should not be commented out | Minor | Code Smell |
| `java:S1481` | Unused local variables should be removed | Minor | Code Smell |
| `java:S1854` | Useless assignments should be removed | Major | Code Smell |
| `java:S3776` | Cognitive Complexity of functions should not be too high | Critical | Code Smell |
| `java:S3623` | "==" and "!=" should not be used when "equals" is intended | Major | Bug |
| `java:S1872` | Classes should not be compared by name | Major | Code Smell |
| `java:S1118` | Utility classes should not have public constructors | Major | Code Smell |
| `java:S1133` | Deprecated code should be removed | Major | Code Smell |
| `java:S1186` | Methods should not be empty | Major | Code Smell |
| `java:S2259` | Null pointers should not be dereferenced | Critical | Bug |
| `java:S2272` | `Iterator.next()` should be preceded by `hasNext()` | Critical | Bug |
| `java:S2446` | `clone()` should be overridden along with `finalize()` | Critical | Bug |
| `java:S2696` | Instance methods should not write to "static" fields | Major | Bug |
| `java:S3518` | `equals` methods should be symmetric | Critical | Bug |

#### Deactivated (False Positive Prone)

| Rule Key | Name | Reason for Deactivation |
|----------|------|------------------------|
| `java:S1192` | String literals should not be duplicated | Business messages often repeat; use constants for magic strings only |
| `java:S120` | Files should not be too long | Architecture may require longer files; use complexity metrics instead |
| `java:S138` | Functions should not be too long | Use cognitive complexity (S3776) instead of line count |
| `java:S107` | Constructors should not have too many parameters | Builder pattern may require many params; use @Builder |
| `java:S3740` | Raw types should not be used | Legacy code may need raw types for interop |

## Bug Detection Rules

### Null Safety

| Rule Key | Severity | Description | Fix Pattern |
|----------|----------|-------------|-------------|
| `java:S2259` | Critical | Null pointer dereference | Add null check or use `Optional.ofNullable()` |
| `java:S2632` | Major | Primitive wrappers should be compared with `equals()` | Use `.equals()` not `==` for Integer/Long |
| `java:S5411` | Major | Boxed "Boolean" should be avoided in boolean expressions | Use primitive `boolean` or explicit null check |
| `java:S2447` | Major | Methods returning arrays should not return null | Return empty array `new T[0]` |
| `java:S4274` | Major | Methods returning collections should not return null | Return `Collections.emptyList()` |

### Concurrency Bugs

| Rule Key | Severity | Description | Fix Pattern |
|----------|----------|-------------|-------------|
| `java:S2222` | Critical | Locks should be released | Use `try-finally` or try-with-resources |
| `java:S2445` | Critical | Entities relying on a lock should not be used with "Double-Checked Locking" | Use `volatile` or `AtomicReference` |
| `java:S3066` | Major | Two fields in the same class should not be updated in non-atomic operations | Use synchronized block or atomic compound operations |
| `java:S2142` | Major | "InterruptedException" should not be ignored | Restore interrupt status: `Thread.currentThread().interrupt()` |
| `java:S2184` | Major | `Math` operations should not overflow | Use `Math.addExact()`, `Math.multiplyExact()` |
| `java:S1217` | Major | Thread.run() should not be called directly | Call `thread.start()` not `thread.run()` |

### Logic Bugs

| Rule Key | Severity | Description | Fix Pattern |
|----------|----------|-------------|-------------|
| `java:S2272` | Critical | `Iterator.next()` should be preceded by `hasNext()` | Check `hasNext()` before `next()` |
| `java:S2189` | Critical | Loops should not be infinite | Add exit condition |
| `java:S3626` | Major | Jump statements should not be redundant | Remove redundant `break`/`continue`/`return` |
| `java:S2583` | Major | Conditions should not always evaluate to the same value | Fix always-true/false conditions |
| `java:S2589` | Major | Boolean expressions should not be gratuitous | Remove redundant boolean comparisons |

## Vulnerability Rules

### Injection

| Rule Key | Severity | Description | Fix Pattern |
|----------|----------|-------------|-------------|
| `java:S2076` | Blocker | OS command injection | Use `ProcessBuilder` with argument list, validate input |
| `java:S2078` | Blocker | LDAP injection | Use parameterized LDAP queries |
| `java:S2083` | Blocker | Path traversal | Validate and normalize paths, use whitelist |
| `java:S2091` | Critical | XPath injection | Use parameterized XPath |
| `java:S2095` | Critical | SQL injection | Use `PreparedStatement` / ORM parameterization |
| `java:S2631` | Critical | Regex injection | Validate regex input, use `Pattern.quote()` |

### Cryptography

| Rule Key | Severity | Description | Fix Pattern |
|----------|----------|-------------|-------------|
| `java:S3329` | Critical | Cipher with no IV | Use `IvParameterSpec` with random IV |
| `java:S4423` | Blocker | Weak SSL/TLS protocol | Use TLS 1.2+ |
| `java:S4787` | Critical | Hardcoded encryption key | Use environment variables / secret manager |
| `java:S5542` | Critical | Cipher algorithms should be robust | Use AES/GCM/NoPadding, not ECB |
| `java:S2278` | Critical | DES/DESede should not be used | Use AES-256 |
| `java:S2245` | Major | Pseudo-random number generators (PRNGs) should be secure | Use `SecureRandom` not `Random` for security |

### Security Hotspots (Review Required)

| Rule Key | Severity | Description | Review Action |
|----------|----------|-------------|---------------|
| `java:S2068` | Hotspot | Hardcoded credentials | Verify placeholders, move to env vars |
| `java:S4502` | Hotspot | CSRF protection disabled | Verify intentional (JWT API) |
| `java:S4507` | Hotspot | Debug mode enabled | Verify dev profile only |
| `java:S4792` | Hotspot | Log injection | Sanitize log input |
| `java:S5122` | Hotspot | XML external entity (XXE) | Configure XXE prevention |
| `java:S5131` | Hotspot | XSS in JSP/Servlet | Output encoding |
| `java:S5144` | Hotspot | Insecure cookie | Set `Secure` + `HttpOnly` |
| `java:S5145` | Hotspot | Log injection | No unsanitized user input in logs |
| `java:S5167` | Hotspot | HTTP method with CSRF | CSRF token for state-changing methods |
| `java:S5300` | Hotspot | Insecure cookie | Set `SameSite` attribute |
| `java:S5527` | Hotspot | Cleartext transmission | Use HTTPS/WSS |

## Code Smell Rules

### Complexity

| Rule Key | Severity | Description | Threshold |
|----------|----------|-------------|-----------|
| `java:S3776` | Critical | Cognitive Complexity of functions should not be too high | > 15 |
| `java:S1541` | Critical | Methods should not be too complex (cyclomatic) | > 10 |
| `java:S1067` | Major | Expressions should not be too complex | > 3 operators |
| `java:S1142` | Major | Return statements should not be excessive | > 3 per method |
| `java:S134` | Major | Control flow statements should not be nested too deeply | > 3 levels |

### Design

| Rule Key | Severity | Description | Fix Pattern |
|----------|----------|-------------|-------------|
| `java:S1118` | Major | Utility classes should not have public constructors | Private constructor |
| `java:S1610` | Major | Abstract classes without abstract methods should be interfaces | Convert to interface |
| `java:S110` | Major | Inheritance tree should not be too deep | <= 5 levels |
| `java:S1694` | Major | An abstract class with only one subclass should be merged | Merge or make concrete |
| `java:S2387` | Major | Child class field names should not shadow parent fields | Rename fields |

### Duplication & Dead Code

| Rule Key | Severity | Description | Fix Pattern |
|----------|----------|-------------|-------------|
| `java:S125` | Minor | Sections of code should not be commented out | Remove or uncomment |
| `java:S1481` | Minor | Unused local variables should be removed | Remove |
| `java:S1068` | Major | Unused private fields should be removed | Remove |
| `java:S1133` | Major | Deprecated code should be removed | Remove or document timeline |
| `java:S1186` | Major | Methods should not be empty | Add implementation or throw `UnsupportedOperationException` |
| `java:S1854` | Major | Useless assignments should be removed | Remove redundant assignment |

## Best Practice Rules

### Logging

| Rule Key | Severity | Description | Fix Pattern |
|----------|----------|-------------|-------------|
| `java:S106` | Major | Standard outputs should not be used directly to log anything | Use SLF4J logger |
| `java:S2629` | Major | Logging arguments should be passed in the right way | Use parameterized `{}` not concatenation |
| `java:S4792` | Hotspot | Logging should not be vulnerable to injection attacks | Sanitize user input in logs |

### Exception Handling

| Rule Key | Severity | Description | Fix Pattern |
|----------|----------|-------------|-------------|
| `java:S1181` | Critical | Throwable and Error should not be caught | Catch specific exceptions |
| `java:S1166` | Major | Exception handlers should preserve the original exception | Pass cause to new exception |
| `java:S2737` | Major | "catch" clauses should do more than rethrow | Add handling or remove catch |
| `java:S2142` | Major | "InterruptedException" should not be ignored | Restore interrupt status |
| `java:S3655` | Major | "assert" statements should not be used for parameter checking | Use explicit validation |

### Resource Management

| Rule Key | Severity | Description | Fix Pattern |
|----------|----------|-------------|-------------|
| `java:S2095` | Critical | Resources should be closed | Use try-with-resources |
| `java:S2222` | Critical | Locks should be released | Use try-finally |
| `java:S4925` | Major | "SocketOutputStream" should not be flushed in a loop | Buffer writes |

## Maven Plugin Configuration

```xml
<!-- pom.xml -->
<properties>
    <sonar.qualitygate.wait>true</sonar.qualitygate.wait>
    <sonar.qualitygate.timeout>300</sonar.qualitygate.timeout>
    <sonar.java.source>17</sonar.java.source>
    <sonar.java.target>17</sonar.java.target>
    <sonar.sourceEncoding>UTF-8</sonar.sourceEncoding>
</properties>

<build>
    <plugins>
        <!-- SonarQube Scanner -->
        <plugin>
            <groupId>org.sonarsource.scanner.maven</groupId>
            <artifactId>sonar-maven-plugin</artifactId>
            <version>3.10.0.2594</version>
        </plugin>

        <!-- JaCoCo Coverage -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <id>prepare-agent</id>
                    <goals><goal>prepare-agent</goal></goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals><goal>report</goal></goals>
                </execution>
            </executions>
        </plugin>

        <!-- SpotBugs (additional static analysis) -->
        <plugin>
            <groupId>com.github.spotbugs</groupId>
            <artifactId>spotbugs-maven-plugin</artifactId>
            <version>4.8.3.0</version>
            <executions>
                <execution>
                    <goals><goal>check</goal></goals>
                    <configuration>
                        <effort>Max</effort>
                        <threshold>Medium</threshold>
                        <failOnError>false</failOnError>
                    </configuration>
                </execution>
            </executions>
        </plugin>

        <!-- OWASP Dependency Check -->
        <plugin>
            <groupId>org.owasp</groupId>
            <artifactId>dependency-check-maven</artifactId>
            <version>9.0.9</version>
            <executions>
                <execution>
                    <goals><goal>check</goal></goals>
                    <configuration>
                        <failBuildOnCVSS>7</failBuildOnCVSS>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## CI Integration (GitHub Actions)

```yaml
# .github/workflows/sonarqube.yml
name: SonarQube Analysis

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  sonarqube:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Shallow clones should be disabled for better analysis

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Build and Test with Coverage
        run: mvn clean verify -Pcoverage

      - name: SonarQube Scan
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
        run: |
          mvn sonar:sonar \
            -Dsonar.qualitygate.wait=true \
            -Dsonar.qualitygate.timeout=300

      - name: Upload JaCoCo Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: target/site/jacoco/
```

## Issue Resolution Workflow

```
New Issue Detected by SonarQube
    │
    ├── Blocker / Critical
    │   ├── Must fix before merge
    │   ├── Assign to developer immediately
    │   └── Add to current sprint
    │
    ├── Major
    │   ├── Fix within current sprint
    │   ├── Can defer with justification
    │   └── Track in tech debt backlog
    │
    ├── Minor
    │   ├── Fix when convenient
    │   ├── Batch with other refactoring
    │   └── Track in tech debt backlog
    │
    └── Security Hotspot
        ├── Review within 24 hours
        ├── Mark as "Safe" or "Fixed"
        └── 100% must be reviewed before release
```

## Suppression Rules

### When to Suppress

Only suppress an issue when:
1. It's a confirmed false positive
2. The rule doesn't apply to this specific context
3. There's a documented architectural reason

### How to Suppress

```java
// ✅ Good - suppression with justification
@SuppressWarnings("java:S2259")  // Null check is done in validate() method above
public void process(String input) {
    validate(input);
    input.length();  // Safe: validate() throws if null
}

// ✅ Good - SonarQube issue suppression via annotation
@SuppressWarnings("java:S1068")  // Field is used by reflection (Jackson deserialization)
private String internalField;

// ❌ Bad - suppression without justification
@SuppressWarnings("all")
public void messyMethod() { ... }
```

### Suppression Review

- All `@SuppressWarnings` annotations must include the specific rule key (not `"all"`)
- Suppressions are reviewed quarterly
- Suppressed issues are tracked in `docs/tech-debt/suppressions.md`

## References

- SonarQube Java Rules: https://rules.sonarsource.com/java
- SonarQube Quality Gates: https://docs.sonarsource.com/sonarqube/latest/user-guide/quality-gates/
- SonarQube Quality Profiles: https://docs.sonarsource.com/sonarqube/latest/instance-administration/quality-profiles/
- JaCoCo: https://www.jacoco.org/jacoco/trunk/doc/
- SpotBugs: https://spotbugs.github.io/
- OWASP Dependency Check: https://owasp.org/www-project-dependency-check/
- Clean Code Taxonomy: https://docs.sonarsource.com/sonarqube/latest/user-guide/clean-code-taxonomy/

---

*Last updated: 2026-08-19*
