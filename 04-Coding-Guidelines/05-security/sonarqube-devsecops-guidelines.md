# SonarQube & DevSecOps Guidelines

> Best practices for code quality with SonarQube, security scanning, and DevSecOps workflow integration.

## SonarQube Configuration

### Quality Gate (Mandatory)

```yaml
# sonar-project.properties
sonar.projectKey=cbol-messaging
sonar.projectName=CBOL Messaging Hub
sonar.projectVersion=1.0.0

# Source
sonar.sources=src/main/java
sonar.tests=src/test/java
sonar.java.binaries=target/classes
sonar.java.libraries=target/dependency/*.jar

# Coverage
sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
sonar.coverage.exclusions=**/config/**,**/dto/**,**/entity/**,**/*Application.java

# Exclusions
sonar.exclusions=
  **/target/**,
  **/generated/**,
  **/*Mapper.xml,
  **/proto/**

# Quality profile
sonar.java.source=17
sonar.java.encoding=UTF-8
```

### Quality Gate Thresholds

| Metric | Threshold | Severity |
|--------|-----------|----------|
| Coverage | >= 80% | Error (block merge) |
| New Code Coverage | >= 80% | Error |
| Duplicated Lines | <= 3% | Error |
| New Duplicated Lines | <= 3% | Error |
| Critical Issues | 0 | Error |
| Blocker Issues | 0 | Error |
| Major Issues | <= 5 | Warning |
| Security Hotspots Reviewed | 100% | Error |
| Vulnerabilities | 0 | Error |
| Code Smells (new) | <= 10 | Warning |
| Maintainability Rating | A | Error |
| Reliability Rating | A | Error |
| Security Rating | A | Error |

### Maven Integration

```xml
<!-- pom.xml -->
<properties>
    <sonar.qualitygate.wait>true</sonar.qualitygate.wait>
    <sonar.qualitygate.timeout>300</sonar.qualitygate.timeout>
</properties>

<build>
    <plugins>
        <!-- JaCoCo for coverage -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <goals><goal>prepare-agent</goal></goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals><goal>report</goal></goals>
                </execution>
            </executions>
        </plugin>

        <!-- SonarQube -->
        <plugin>
            <groupId>org.sonarsource.scanner.maven</groupId>
            <artifactId>sonar-maven-plugin</artifactId>
            <version>3.10.0.2594</version>
        </plugin>
    </plugins>
</build>
```

### CI Pipeline (GitHub Actions)

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Build and Test
        run: mvn clean verify -Pcoverage

      - name: SonarQube Scan
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
        run: mvn sonar:sonar -Dsonar.qualitygate.wait=true

      - name: Upload Coverage
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: target/site/jacoco/
```

## SonarQube Issue Types

### Bug (Reliability)

| Rule | Severity | Description | Fix |
|------|----------|-------------|-----|
| `java:S2259` | Critical | Null pointer dereference | Add null check or use Optional |
| `java:S2272` | Critical | `Iterator.next()` without `hasNext()` | Check `hasNext()` before `next()` |
| `java:S2446` | Critical | `clone()` without `super.clone()` | Call `super.clone()` |
| `java:S2696` | Major | Instance method used as static | Make method static or call on instance |
| `java:S3518` | Critical | `equals` method not symmetric | Fix symmetric comparison |

### Vulnerability (Security)

| Rule | Severity | Description | Fix |
|------|----------|-------------|-----|
| `java:S2076` | Blocker | OS command injection | Use `ProcessBuilder` with argument list, validate input |
| `java:S2078` | Blocker | LDAP injection | Use parameterized LDAP queries |
| `java:S2083` | Blocker | Path traversal | Validate and normalize file paths, use whitelist |
| `java:S2091` | Critical | XPath injection | Use parameterized XPath |
| `java:S2095` | Critical | SQL injection | Use PreparedStatement / ORM parameterization |
| `java:S2631` | Critical | Regex injection | Validate regex input, use `Pattern.quote()` |
| `java:S3329` | Critical | Cipher with no IV | Use `IvParameterSpec` with random IV |
| `java:S4423` | Blocker | Weak SSL/TLS protocol | Use TLS 1.2+ |
| `java:S4787` | Critical | Hardcoded encryption key | Use environment variables / secret manager |
| `java:S5527` | Critical | Cleartext transmission | Use HTTPS/WSS, not HTTP/WS |

### Security Hotspot (Security Review)

| Rule | Severity | Description | Review |
|------|----------|-------------|--------|
| `java:S2068` | Hotspot | Hardcoded credentials | Verify credentials are placeholders, move to env vars |
| `java:S4502` | Hotspot | CSRF protection disabled | Verify CSRF protection is intentionally disabled (e.g., API with JWT) |
| `java:S4507` | Hotspot | Debug mode enabled | Verify debug mode is only in dev profile |
| `java:S4792` | Hotspot | Log injection | Verify log input is sanitized |
| `java:S5122` | Hotspot | XML external entity (XXE) | Verify XXE prevention is configured |
| `java:S5131` | Hotspot | XSS in JSP/Servlet | Verify output encoding |
| `java:S5144` | Hotspot | Insecure cookie | Verify `Secure` and `HttpOnly` flags |
| `java:S5145` | Hotspot | Log injection | Verify no unsanitized user input in logs |
| `java:S5167` | Hotspot | HTTP method with CSRF | Verify CSRF token for state-changing methods |
| `java:S5300` | Hotspot | Insecure cookie | Verify `SameSite` attribute |

### Code Smell (Maintainability)

| Rule | Severity | Description | Fix |
|------|----------|-------------|-----|
| `java:S101` | Minor | Class name not PascalCase | Rename to PascalCase |
| `java:S106` | Minor | `System.out.println` used | Use SLF4J logger |
| `java:S115` | Minor | Constant not UPPER_SNAKE_CASE | Rename constant |
| `java:S117` | Minor | Variable not camelCase | Rename variable |
| `java:S119` | Minor | Generic type parameter not single letter | Use single letter (T, E, K, V) |
| `java:S125` | Minor | Commented out code | Remove or uncomment |
| `java:S1481` | Minor | Unused local variable | Remove unused variable |
| `java:S1854` | Major | Useless assignment | Remove redundant assignment |
| `java:S3776` | Critical | Cognitive complexity too high | Refactor, extract methods |
| `java:S3623` | Major | `==` used for String comparison | Use `.equals()` |
| `java:S1872` | Major | `getClass()` used instead of `instanceof` | Use `instanceof` |
| `java:S1118` | Major | Utility class has public constructor | Make constructor private |
| `java:S1133` | Major | Deprecated code not removed | Remove or document deprecation |
| `java:S1186` | Major | Empty method body | Add implementation or throw UnsupportedOperationException |

## DevSecOps Workflow

### Pipeline Stages

```
┌─────────────────────────────────────────────────────────────────────┐
│                        DevSecOps Pipeline                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. Pre-Commit (Developer Local)                                   │
│     ├── Git pre-commit hook                                        │
│     ├── Spotless / Checkstyle (format)                             │
│     ├── SpotBugs (static analysis)                                 │
│     ├── Secret scanning (gitleaks)                                 │
│     └── Unit tests (fast subset)                                   │
│                                                                     │
│  2. CI (Pull Request)                                              │
│     ├── Build + All unit tests                                     │
│     ├── JaCoCo coverage report                                     │
│     ├── SonarQube scan (quality gate)                             │
│     ├── OWASP Dependency Check (vulnerable deps)                  │
│     ├── Trivy (container image scan)                               │
│     ├── Semgrep (custom security rules)                           │
│     └── Integration tests                                          │
│                                                                     │
│  3. Pre-Deployment (Staging)                                       │
│     ├── DAST (Dynamic App Security Testing)                       │
│     ├── API security tests (OWASP ZAP)                            │
│     ├── Penetration test (automated subset)                        │
│     └── Performance/load test                                      │
│                                                                     │
│  4. Production Deployment                                           │
│     ├── Canary deployment (5% traffic)                             │
│     ├── Runtime security monitoring (Falco/Sysdig)                │
│     ├── WAF (Web Application Firewall)                             │
│     └── Incident response playbooks                                │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Pre-Commit Hook

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.5.0
    hooks:
      - id: trailing-whitespace
      - id: end-of-file-fixer
      - id: check-yaml
      - id: check-json
      - id: check-merge-conflict

  - repo: https://github.com/gitleaks/gitleaks
    rev: v8.18.2
    hooks:
      - id: gitleaks
        args: ["--verbose", "--redact"]

  - repo: local
    hooks:
      - id: spotless
        name: Spotless (Java format)
        entry: mvn spotless:apply
        language: system
        files: \.java$
        pass_filenames: false

      - id: unit-tests
        name: Unit tests (fast)
        entry: mvn test -Dtest=*UnitTest -q
        language: system
        pass_filenames: false
```

### Dependency Scanning

```xml
<!-- pom.xml - OWASP Dependency Check -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>9.0.9</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>  <!-- fail on High+ -->
        <suppressionFiles>
            <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
        </suppressionFiles>
    </configuration>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

### Container Scanning (Trivy)

```yaml
# .github/workflows/security.yml
- name: Build Docker image
  run: docker build -t cbol-messaging:${{ github.sha }} .

- name: Trivy vulnerability scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: cbol-messaging:${{ github.sha }}
    format: 'sarif'
    output: 'trivy-results.sarif'
    severity: 'CRITICAL,HIGH'
    exit-code: '1'  # fail on CRITICAL/HIGH

- name: Upload Trivy results to GitHub Security
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: 'trivy-results.sarif'
```

### Semgrep Custom Rules

```yaml
# .semgrep.yml
rules:
  - id: no-system-out
    patterns:
      - pattern: System.out.println(...)
    message: "Use SLF4J logger instead of System.out.println"
    languages: [java]
    severity: WARNING

  - id: no-hardcoded-secret
    patterns:
      - pattern-either:
          - pattern: String $VAR = "password";
          - pattern: String $VAR = "secret";
          - pattern: String $VAR = "token";
    message: "Potential hardcoded secret - use environment variables"
    languages: [java]
    severity: ERROR

  - id: no-sql-string-concat
    patterns:
      - pattern: |
          String $SQL = "..." + $VAR + "...";
          ...
          $STMT.executeQuery($SQL);
    message: "Potential SQL injection - use PreparedStatement"
    languages: [java]
    severity: ERROR
```

## Security Testing

### SAST (Static Application Security Testing)

| Tool | Purpose | Integration |
|------|---------|-------------|
| SonarQube | Code quality + security rules | CI pipeline, quality gate |
| SpotBugs + FindSecBugs | Java-specific security bugs | Maven plugin, CI |
| Semgrep | Custom security rules | Pre-commit + CI |
| CodeQL | GitHub native code scanning | GitHub Actions |

### DAST (Dynamic Application Security Testing)

| Tool | Purpose | Integration |
|------|---------|-------------|
| OWASP ZAP | Automated web app scanning | Staging environment, CI |
| Burp Suite | Manual + automated pentest | Security team |
| Nuclei | Template-based vulnerability scanning | CI, staging |

### SCA (Software Composition Analysis)

| Tool | Purpose | Integration |
|------|---------|-------------|
| OWASP Dependency Check | Vulnerable dependencies | Maven plugin, CI |
| Dependabot | Automated dependency updates | GitHub native |
| Snyk | Dependency + container scanning | CI, IDE plugin |
| Trivy | Container + dependency scanning | CI, container registry |

### Secret Scanning

| Tool | Purpose | Integration |
|------|---------|-------------|
| gitleaks | Git history secret scanning | Pre-commit + CI |
| trufflehog | Deep secret scanning | CI |
| GitHub Secret Scanning | Native secret detection | GitHub native |

## Security Response

### Vulnerability Management

```
Severity: Critical (CVSS 9.0-10.0)
  ├── SLA: 24 hours to fix or mitigate
  ├── Notification: Security team + Engineering lead
  └── Action: Hotfix deployment, incident report

Severity: High (CVSS 7.0-8.9)
  ├── SLA: 7 days to fix
  ├── Notification: Security team
  └── Action: Fix in next release, workaround documented

Severity: Medium (CVSS 4.0-6.9)
  ├── SLA: 30 days to fix
  └── Action: Track in backlog, fix in regular release

Severity: Low (CVSS 0.1-3.9)
  ├── SLA: 90 days to fix
  └── Action: Track in backlog, fix when convenient
```

### Incident Response

```
1. Detection
   ├── Alert from WAF / IDS / runtime security
   ├── Anomaly in logs / metrics
   └── External report (bug bounty, customer)

2. Triage (within 1 hour)
   ├── Assess severity and scope
   ├── Assign incident commander
   └── Determine if active exploit

3. Containment
   ├── Isolate affected systems
   ├── Block malicious IPs / users
   ├── Disable vulnerable features
   └── Preserve evidence (logs, memory dumps)

4. Eradication
   ├── Identify root cause
   ├── Apply security patch
   ├── Remove backdoors / unauthorized access
   └── Reset compromised credentials

5. Recovery
   ├── Restore systems from clean backup
   ├── Monitor for recurrence
   ├── Gradually restore traffic
   └── Verify security controls

6. Post-Incident (within 7 days)
   ├── Write incident report
   ├── Identify lessons learned
   ├── Update security controls
   └── Schedule follow-up review
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| No quality gate | Low quality code merged | SonarQube quality gate blocks merge |
| `System.out.println` in production | No log levels, no structure, performance | SLF4J logger with proper levels |
| Ignoring SonarQube warnings | Accumulated tech debt | Fix warnings, set thresholds |
| No dependency scanning | Vulnerable dependencies in production | OWASP Dependency Check + Dependabot |
| No secret scanning | Secrets committed to git | gitleaks pre-commit + CI |
| Security only at the end | Expensive to fix, vulnerabilities in prod | Shift-left: pre-commit + CI + staging |
| No DAST | Runtime vulnerabilities undetected | OWASP ZAP in staging |
| Hardcoded credentials | Source leak = credential compromise | Environment variables / secret manager |
| No incident response plan | Chaos during security incident | Documented IR plan + regular drills |
| Disabling security rules to pass CI | False sense of security | Fix issues, don't suppress rules |
| No security training | Developers introduce vulnerabilities | Regular security training + secure coding guidelines |
| Permissive CORS (`*` + credentials) | Any site can make authenticated requests | Whitelist specific origins |
| No rate limiting | Brute force, DoS | Rate limit on auth + sensitive endpoints |

## References

- SonarQube Rules: https://rules.sonarsource.com/java
- SonarQube Quality Gates: https://docs.sonarsource.com/sonarqube/latest/user-guide/quality-gates/
- OWASP Top 10: https://owasp.org/www-project-top-ten/
- OWASP ASVS: https://owasp.org/www-project-application-security-verification-standard/
- OWASP Dependency Check: https://owasp.org/www-project-dependency-check/
- Trivy: https://github.com/aquasecurity/trivy
- Semgrep: https://semgrep.dev/
- gitleaks: https://github.com/gitleaks/gitleaks
- DevSecOps: https://www.devsecops.org/
- NIST SSDF: https://csrc.nist.gov/Projects/ssdf
