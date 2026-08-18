---
name: codebase-architecture-analyst
description: Analyzes codebases to reverse engineer architecture, generating comprehensive architectural diagrams and detailed documentation. Examines code structure, dependencies, patterns, security, performance, and scalability considerations. Triggers on phrases like "reverse engineer this project", "generate architecture from code", "explain this project", "analyze codebase architecture", "document this system's architecture", or "analyze this GitHub repository". Supports cloning a main repository from a URL and adding dependent repositories as git submodules before analysis. Provides deep file-level analysis, interactive dependency querying, and human-readable security/architecture insights.
---

# Codebase Architecture Analyst

This skill performs comprehensive, deep analysis of codebases with file-level understanding, interactive dependency querying, and human-readable architectural insights. It scans every source file, understands their purpose through documentation and code analysis, maps detailed dependencies, and enables interactive exploration of the codebase structure.

## When to Use This Skill

Trigger this skill when users ask to:
- "Reverse engineer this project"
- "Generate architecture from code"
- "Explain this project"  
- "Analyze codebase architecture"
- "Document this system's architecture"
- "Analyze this GitHub repository"
- "Clone and analyze this repository"
- "Analyze the repository at [URL]"
- "What files depend on [filename]?"
- "Show me the dependency graph for [component]"
- "Explain the security architecture"
- "Give me a human-readable overview of [aspect]"
- "Run a security analysis on this codebase"
- "Find security vulnerabilities in this code"
- "Perform a security audit"
- "Scan for vulnerabilities"
- "Check for OWASP issues"
- "Find secrets or credentials in the code"
- "Analyze the attack surface"
- "Generate a security report"

## Deep Analysis Capabilities

### File-Level Understanding
- **Documentation Extraction**: Reads docstrings, comments, and README files in each source file
- **Code Purpose Analysis**: Analyzes code structure when documentation is missing
- **Function/Class Mapping**: Catalogs all exported functions, classes, and their purposes
- **File Responsibility**: Determines each file's role and responsibility in the system

### Interactive Dependency Analysis
- **Bi-directional Dependencies**: "What depends on this file?" and "What does this file depend on?"
- **Function-level Dependencies**: Granular mapping of function and class usage
- **Dependency Querying**: Answer specific questions about relationships
- **Impact Analysis**: "If I change this file, what else is affected?"

### Human-Readable Insights
- **Security Overview**: Plain English explanation of security patterns and concerns
- **Architecture Summary**: Clear description of system organization and design
- **Component Relationships**: How different parts of the system work together
- **Technology Decisions**: Why certain technologies and patterns were chosen

## Enhanced Analysis Workflow

### Phase 1: Deep Source File Analysis
1. **Scan all source files** using `search_subagent` to discover every code file
2. **Analyze each file individually** using `scripts/analyze_source_files.py`:
   - Extract documentation, docstrings, and comments
   - Analyze code structure and purpose when docs are missing
   - Catalog functions, classes, exports, and interfaces
   - Determine file responsibility and role
3. **Create file inventory** with detailed descriptions for every source file
4. **Map technology stack** from actual code analysis

### Phase 2: Deep Dependency & Relationship Mapping  
1. **Extract granular dependencies** using `scripts/deep_dependency_analyzer.py`:
   - File-to-file dependencies (imports, requires, includes)
   - Function-to-function usage patterns
   - Class inheritance and composition relationships
   - API and interface dependencies
2. **Build bi-directional dependency graph** with queryable structure
3. **Create impact analysis mappings** ("what affects what")
4. **Generate dependency query database** for interactive exploration
5. **Generate interactive dependency graph** using `scripts/generate_dependency_graph.py`:
   - Reads `dependency-graph.json` and `circular-dependencies.json` from the analysis output
   - Produces a self-contained `dependency-graph.html` in `dependencies/`
   - WebGL rendering (Sigma.js + Graphology) handles 10K+ nodes smoothly
   - Features: search, category filters, node inspector, ForceAtlas2 layout, circular dependency highlighting, minimap
   - **Always run this script** after dependency analysis completes

### Phase 3: Human-Readable Analysis & Documentation
1. **Generate human-readable overviews** using analysis data:
   - **Security Overview**: Plain English security analysis
   - **Architecture Summary**: Clear system organization explanation  
   - **Component Guide**: How different parts work together
2. **Create interactive dependency explorer** for answering questions
3. **Generate comprehensive documentation** with file-level insights
4. **Produce visual diagrams** showing detailed relationships

## Analysis Approach by Codebase Type

### Web Applications
- Identify frontend framework (React, Vue, Angular)
- Map component hierarchy and state management
- Analyze routing and page structure
- Document API integration patterns

### Backend APIs
- Map endpoint structure and routing
- Identify middleware and authentication
- Analyze database integration patterns
- Document service layer architecture

### Microservices
- Map service boundaries and communication
- Analyze inter-service dependencies
- Document deployment and orchestration
- Identify shared infrastructure patterns

### Monolithic Applications
- Map layer separation (presentation, business, data)
- Analyze module organization
- Document internal APIs and interfaces
- Identify refactoring opportunities

## Interactive Dependency Querying

### Query Types Supported
- **"What files depend on [filename]?"** - Find all files that import/use this file
- **"What does [filename] depend on?"** - Show all dependencies of this file
- **"Show me the dependency tree for [component]"** - Hierarchical view of dependencies
- **"If I change [filename], what else is affected?"** - Impact analysis
- **"What functions from [filename] are used by [other-file]?"** - Function-level usage
- **"Show me all circular dependencies"** - Detect dependency cycles
- **"What are the entry points to the system?"** - Find main/root files

### Query Execution Process
1. **Parse user question** to identify query type and target files/components
2. **Search dependency database** built during analysis phase
3. **Generate relevant subgraph** showing requested relationships
4. **Provide human-readable explanation** of findings
5. **Suggest follow-up questions** for deeper exploration

## Human-Readable Analysis Outputs

### Security Analysis Overview
**Example Output**: "This system implements authentication through JWT tokens stored in HTTP-only cookies. The main security concerns are in the user input validation (3 files lack proper sanitization) and the API endpoints don't have rate limiting. The password handling uses bcrypt which is good, but some environment variables containing secrets are logged in development mode."

### Architecture Analysis Overview  
**Example Output**: "This is a layered web application with React frontend, Express.js backend, and PostgreSQL database. The frontend components are well-organized by feature, the backend follows REST API patterns with middleware for auth and logging, and database access uses an ORM with proper migrations. The main architectural strength is clear separation of concerns, but the frontend state management could benefit from a more structured approach."

### Component Relationship Guide
**Example Output**: "The UserService component is central to the system - it's used by 8 other components for authentication, profile management, and user data. If you're modifying user-related functionality, you'll likely need to touch UserService, UserController (handles HTTP requests), UserModel (database schema), and AuthMiddleware (validates permissions)."

## Script Execution & Output Structure

### Enhanced Analysis Scripts
1. **File Analysis**: `scripts/analyze_source_files.py` - Deep file-level understanding
2. **Dependency Analysis**: `scripts/deep_dependency_analyzer.py` - Granular relationship mapping  
3. **Dependency Graph**: `scripts/generate_dependency_graph.py` - Interactive HTML dependency graph visualization
4. **Query Engine**: `scripts/dependency_query_engine.py` - Interactive dependency exploration
5. **Human Reports**: `scripts/generate_readable_reports.py` - Plain English insights
6. **Visual Generation**: `scripts/enhanced_diagram_generator.py` - Detailed architecture diagrams

### Output Location

Before starting any analysis, **always ask the user where to save the output**:

> "Where would you like to save the analysis results? Please provide an absolute path (e.g., `/Users/you/analysis-results` or `~/Documents/code-analysis`). The output will be saved under `{your-path}/{project-name}/{timestamp}/` and will not be written into the project being analyzed."

If the user does not provide a path, prompt again — do not default to saving inside the project being analyzed. The analyzed project's directory must remain unmodified.

Store the confirmed path as `{output-base-path}` and use it as the root for all output files throughout the analysis.

### Output Structure
```
{output-base-path}/{project-name}/{timestamp}/
├── source-files/
│   ├── file-inventory.json          # Catalog of every source file with descriptions
│   ├── file-analysis/               # Individual file analysis results
│   └── documentation-map.json      # Documentation extracted from files
├── dependencies/
│   ├── dependency-graph.json        # Complete bi-directional dependency map
│   ├── dependency-graph.html        # Interactive graph visualization (open in browser)
│   ├── function-dependencies.json   # Function-level usage patterns
│   ├── impact-analysis.json        # "What affects what" mappings
│   └── circular-dependencies.json   # Dependency cycle detection
├── analysis/
│   ├── security-overview.md         # High-level security analysis (architecture focus)
│   ├── architecture-overview.md     # Plain English architecture explanation  
│   ├── components-guide.md          # How components relate and interact
│   └── technology-decisions.md      # Why certain choices were made
├── security/                        # Full security vulnerability analysis
│   ├── detailed-security-analysis.md  # Comprehensive narrative security report
│   ├── vulnerability-report.json       # Machine-readable unified findings
│   ├── remediation-guide.md            # Prioritized fix guide with code examples
│   ├── attack-surface-map.md           # All external input points cataloged
│   ├── dependency-audit.md             # Dependency vulnerability + supply chain
│   └── tool-scan-results/              # Raw output from each automated tool
│       ├── bandit/
│       ├── semgrep/
│       ├── pip-audit/
│       ├── detect-secrets/
│       └── {other-tools}/
├── interactive/
│   ├── dependency-query-db.json     # Queryable dependency database
│   └── query-examples.md           # Example questions you can ask
└── visuals/
    ├── detailed-architecture.md     # Enhanced Mermaid diagrams
    ├── dependency-graphs.md         # Component relationship diagrams
    └── security-model.md            # Security architecture visualization
```

---

## Security Vulnerability Testing Framework

This section defines how to perform a comprehensive security analysis beyond the high-level security overview. When a user requests a security audit or the analysis warrants deep security examination, execute this framework in addition to (or instead of) the standard architecture analysis.

---

### Security Test Categories

Organize the security analysis around these categories. For each, document: findings, severity (Critical / High / Medium / Low / Info), file locations, and remediation advice.

#### Category 1 — OWASP Top 10 (2021)

| ID | Vulnerability | What to Check in Code |
|---|---|---|
| A01 | Broken Access Control | Authorization checks on every route/function; privilege escalation paths; IDOR patterns; directory traversal |
| A02 | Cryptographic Failures | Weak algorithms (MD5, SHA1, DES, RC4); plaintext secrets; unencrypted data at rest/transit; improper key management; missing TLS enforcement |
| A03 | Injection | SQL/NoSQL/LDAP/XPath injection; OS command injection; template injection; eval/exec with user input; subprocess with shell=True |
| A04 | Insecure Design | Missing threat model controls; insecure defaults; lack of rate limiting; business logic flaws; trust boundary violations |
| A05 | Security Misconfiguration | Debug mode in production; default credentials; unnecessary features enabled; verbose error messages exposing internals; permissive CORS |
| A06 | Vulnerable Components | Outdated dependencies with known CVEs; unmaintained packages; transitive dependency risks |
| A07 | Auth & Identity Failures | Weak password policies; missing MFA; insecure session tokens; credential exposure in logs/errors; brute-force vulnerability |
| A08 | Data Integrity Failures | Unsafe deserialization (pickle, yaml.load, marshal); unsigned data; auto-update without integrity checks |
| A09 | Logging & Monitoring Failures | Secrets in log output; insufficient audit logging; log injection; PII in logs |
| A10 | SSRF | User-controlled URLs fetched server-side; insufficient URL validation; internal network reachability |

#### Category 2 — Language-Specific Vulnerability Patterns

**Python:**
- `eval()`, `exec()`, `compile()` with unsanitized input
- `pickle.loads()`, `marshal.loads()`, `yaml.load()` (unsafe), `shelve` with untrusted data
- `subprocess.run(shell=True, ...)` or `os.system()` with user-controlled input
- `open()` with user-controlled file paths (path traversal)
- `__import__()` or `importlib.import_module()` with user-controlled module names
- Regex denial-of-service (ReDoS) via catastrophic backtracking
- `tempfile.mktemp()` (non-atomic) vs `tempfile.mkstemp()` (safe)
- Hardcoded credentials or secrets in source files
- `assert` statements used for security checks (stripped in optimized mode)
- Integer overflow in cryptographic operations
- Timing attacks in credential comparison (use `hmac.compare_digest`)
- Mutable default arguments leaking state between calls

**JavaScript / TypeScript:**
- `eval()`, `Function()`, `setTimeout(string)` with user input
- Prototype pollution via `Object.assign`, `_.merge`, or similar
- `innerHTML`, `document.write()`, `outerHTML` with untrusted data (XSS)
- `child_process.exec()` with user-controlled strings
- `require()` with user-controlled module paths
- Missing `httpOnly` / `secure` / `SameSite` cookie flags
- JWT without algorithm enforcement (`alg: none` attack)
- Path traversal via `path.join()` with user input
- ReDoS in regex patterns

**Go:**
- `os/exec.Command` with unsanitized arguments
- `fmt.Sprintf` format string injection in non-string contexts
- Integer overflow / underflow in crypto operations
- `ioutil.ReadFile` / `os.Open` with user-controlled paths
- Missing TLS certificate verification (`InsecureSkipVerify: true`)
- Goroutine leaks leading to resource exhaustion

**Java:**
- SQL injection via string concatenation in JDBC
- Deserialization via `ObjectInputStream.readObject()` with untrusted data
- XML External Entity (XXE) in XML parsers without `FEATURE_SECURE_PROCESSING`
- `Runtime.exec()` / `ProcessBuilder` with user input
- JNDI lookup injection (Log4Shell pattern)
- Unsafe reflection via `Class.forName()` with user input

#### Category 3 — Secrets & Credential Exposure

Search for:
- Hardcoded API keys, tokens, and passwords in source files
- Credentials committed to version control (including git history)
- Secrets in configuration files not excluded from VCS
- Environment variable names suggesting secrets logged at startup
- Base64-encoded strings that decode to credentials
- Private keys (PEM/DER) in repository
- Database connection strings with embedded passwords
- `.env` files committed to source control

Patterns to scan for (regex):
```
(password|passwd|pwd|secret|api_key|apikey|access_token|auth_token|private_key|client_secret)\s*[=:]\s*['"][^'"]{8,}['"]
(AKIA[0-9A-Z]{16})                          # AWS Access Key ID
(sk-[a-zA-Z0-9]{32,})                        # OpenAI API key
(ghp_[a-zA-Z0-9]{36})                        # GitHub Personal Access Token
(eyJ[A-Za-z0-9-_=]+\.[A-Za-z0-9-_=]+\.?)    # JWT tokens
-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----
```

#### Category 4 — Dependency Vulnerabilities

- Match all declared dependencies against public CVE databases (NVD, OSV, GitHub Advisory)
- Check for dependencies with no recent activity (abandoned packages)
- Check for typosquatting risk in dependency names
- Identify dependencies with known supply chain incidents
- Check lock files are present and committed (prevents dependency confusion attacks)
- Verify integrity hashes in lock files are not manually modified

#### Category 5 — Cryptographic Implementation

- Hash functions used: prohibit MD5 and SHA1 for security purposes (allow for checksums)
- Symmetric encryption: prohibit ECB mode; require authenticated encryption (AES-GCM, ChaCha20-Poly1305)
- Asymmetric encryption: require key sizes ≥2048 bits for RSA, ≥256 bits for EC
- Random number generation: prohibit `random` module for security; require `secrets` or `os.urandom`
- Password hashing: require bcrypt, argon2, or scrypt; prohibit MD5/SHA for passwords
- TLS: require TLS 1.2+; prohibit SSLv2, SSLv3, TLS 1.0, TLS 1.1
- Key derivation: require PBKDF2, bcrypt, or argon2; prohibit simple hashing
- Certificate validation: must not skip cert verification
- Nonce/IV reuse: check for reused initialization vectors

#### Category 6 — Input Validation & Sanitization

- All external inputs validated before use (CLI args, env vars, files, network responses)
- File paths sanitized for traversal sequences (`../`, `..\\`, URL-encoded variants)
- Integer inputs checked for overflow/underflow before arithmetic
- String lengths validated before processing
- Structured data (JSON, XML, YAML) parsed with size/depth limits
- Regular expressions audited for catastrophic backtracking (ReDoS)
- Filenames sanitized before use in filesystem operations
- URLs validated for scheme and host before fetch

#### Category 7 — Error Handling & Information Disclosure

- Stack traces not exposed in user-facing output
- Internal paths not revealed in error messages
- Database errors not propagated to API responses
- Version numbers and framework info not leaked in headers/responses
- Sensitive field values not included in exception messages
- Logging of request/response bodies excludes sensitive fields

#### Category 8 — Race Conditions & Concurrency

- TOCTOU (Time-Of-Check-Time-Of-Use): check-then-act on filesystem resources
- File creation in shared temp directories without exclusive access
- Non-atomic operations on shared resources without locking
- Signal handler safety (async-signal-safe functions only)
- Concurrent map/dict access without synchronization (in goroutines, threads)

---

### Automated Tool Scanning Approach

#### Language Detection

Before selecting tools, identify the language(s) in the codebase by checking:
- File extensions (`.py`, `.js`, `.ts`, `.go`, `.java`, `.kt`, `.cs`, `.rb`, `.php`, `.rs`, `.c`, `.cpp`, `.swift`)
- Build/manifest files: `pyproject.toml`, `setup.py`, `package.json`, `go.mod`, `pom.xml`, `build.gradle`, `*.csproj`, `Gemfile`, `composer.json`, `Cargo.toml`, `Makefile`, `CMakeLists.txt`
- Configuration files: `Dockerfile`, `*.tf`, `*.yaml`/`*.yml` (K8s/Ansible), `*.bicep`

A project may have multiple languages (e.g., Python backend + TypeScript frontend + Terraform IaC). Run tools for **all** detected languages.

#### Tool Selection by Language

For each detected language, run the tools listed below and save output to `{output-base-path}/{project-name}/{timestamp}/security/tool-scan-results/{tool-name}/`.

---

**Python** — detect via `.py` files or `pyproject.toml` / `setup.py` / `requirements.txt`

| Tool | Purpose | Install | Command |
|---|---|---|---|
| `bandit` | Python SAST — common security anti-patterns | `pip install bandit` | `bandit -r src/ -f json -o bandit-results.json` |
| `semgrep` | SAST with Python + secrets rule packs | `pip install semgrep` | `semgrep --config=p/python --config=p/secrets --json -o semgrep-results.json src/` |
| `pip-audit` | Dependency CVEs via PyPI Advisory DB | `pip install pip-audit` | `pip-audit --format json -o pip-audit-results.json` |
| `safety` | Dependency vulnerabilities via Safety DB | `pip install safety` | `safety check --json > safety-results.json` |
| `detect-secrets` | Secrets baseline scanner | `pip install detect-secrets` | `detect-secrets scan src/ > detect-secrets-results.json` |
| `truffleHog3` | Secrets in git history + filesystem | `pip install truffleHog3` | `trufflehog3 filesystem src/ --format json > trufflehog-results.json` |
| `pysa` / `pyre` | Interprocedural taint analysis | `pip install pyre-check` | `pyre analyze` (requires `.pyre_configuration`) |
| `mypy` | Type safety — catches unsafe coercions | `pip install mypy` | `mypy src/ --strict --json-report mypy-report/` |

---

**JavaScript / TypeScript** — detect via `.js`, `.ts`, `.jsx`, `.tsx` files or `package.json`

| Tool | Purpose | Install | Command |
|---|---|---|---|
| `njsscan` | Node.js SAST | `pip install njsscan` | `njsscan --json -o njsscan-results.json src/` |
| `eslint` + security plugins | SAST via lint rules | `npm install eslint eslint-plugin-security eslint-plugin-no-unsanitized` | `eslint --format json -o eslint-results.json src/` |
| `semgrep` | SAST with JS/TS rule packs | (see Python) | `semgrep --config=p/javascript --config=p/typescript --json -o semgrep-results.json src/` |
| `npm audit` | Dependency CVEs (built-in, no install) | built-in | `npm audit --json > npm-audit-results.json` |
| `snyk` | Dependency + code vulnerability scanning | `npm install -g snyk` | `snyk test --json > snyk-dep-results.json && snyk code test --json > snyk-code-results.json` |
| `gitleaks` | Secrets in git history | `brew install gitleaks` | `gitleaks detect --source . --report-format json --report-path gitleaks-results.json` |
| `retire.js` | Vulnerable JavaScript library versions | `npm install -g retire` | `retire --outputformat json --outputpath retire-results.json` |

---

**Go** — detect via `.go` files or `go.mod`

| Tool | Purpose | Install | Command |
|---|---|---|---|
| `gosec` | Go SAST | `go install github.com/securego/gosec/v2/cmd/gosec@latest` | `gosec -fmt json -out gosec-results.json ./...` |
| `govulncheck` | Official Go vulnerability checker (Go team) | `go install golang.org/x/vuln/cmd/govulncheck@latest` | `govulncheck -json ./... > govulncheck-results.json` |
| `semgrep` | SAST with Go rule pack | (see Python) | `semgrep --config=p/golang --json -o semgrep-results.json ./...` |
| `nancy` | Dependency CVEs via Sonatype OSS Index | `go install github.com/sonatype-nexus-community/nancy@latest` | `go list -json -m all \| nancy sleuth --output json > nancy-results.json` |

---

**Java / Kotlin** — detect via `.java`, `.kt` files or `pom.xml` / `build.gradle`

| Tool | Purpose | Install | Command |
|---|---|---|---|
| `semgrep` | SAST with Java rule pack | (see Python) | `semgrep --config=p/java --json -o semgrep-results.json src/` |
| `SpotBugs` + `find-sec-bugs` | Bytecode-level SAST with security plugin | `brew install spotbugs` or Maven plugin | `spotbugs -textui -xml:withMessages -output spotbugs-results.xml target/classes/` |
| `PMD` | Source-level SAST including security rules | `brew install pmd` | `pmd check -d src/ -R category/java/security.xml -f json > pmd-results.json` |
| `OWASP Dependency-Check` | CVE matching for JARs/Maven/Gradle deps | [download CLI](https://jeremylong.github.io/DependencyCheck/) | `dependency-check --scan . --format JSON --out dc-results/` |
| `grype` | Container + dependency CVE scanner | `brew install grype` | `grype dir:. -o json > grype-results.json` |

---

**C# / .NET** — detect via `.cs`, `.vb` files or `*.csproj` / `*.sln`

| Tool | Purpose | Install | Command |
|---|---|---|---|
| `semgrep` | SAST with C# rule pack | (see Python) | `semgrep --config=p/csharp --json -o semgrep-results.json src/` |
| `Security Code Scan` | Roslyn-based .NET SAST analyzer | NuGet package `SecurityCodeScan.VS2019` | Runs automatically during `dotnet build`; output in build warnings |
| `Roslyn Security Guard` | Additional Roslyn SAST rules | NuGet package `RoslynSecurityGuard` | Runs during `dotnet build` |
| `dotnet list package --vulnerable` | Dependency CVE check (built-in .NET CLI) | built-in | `dotnet list package --vulnerable --include-transitive > dotnet-vuln-results.txt` |
| `grype` | Dependency CVE scanner | `brew install grype` | `grype dir:. -o json > grype-results.json` |

---

**Ruby** — detect via `.rb` files or `Gemfile`

| Tool | Purpose | Install | Command |
|---|---|---|---|
| `brakeman` | Rails/Ruby SAST (industry standard) | `gem install brakeman` | `brakeman -f json -o brakeman-results.json .` |
| `bundle-audit` | Dependency CVEs via Ruby Advisory DB | `gem install bundler-audit` | `bundle-audit check --format json > bundle-audit-results.json` |
| `semgrep` | SAST with Ruby rule pack | (see Python) | `semgrep --config=p/ruby --json -o semgrep-results.json .` |

---

**PHP** — detect via `.php` files or `composer.json`

| Tool | Purpose | Install | Command |
|---|---|---|---|
| `phpcs` + `phpcs-security-audit` | SAST via code sniffer with security rules | `composer require --dev squizlabs/php_codesniffer pheromone/phpcs-security-audit` | `phpcs --standard=Security --report=json src/ > phpcs-results.json` |
| `Psalm` | Static analysis with taint tracking | `composer require --dev vimeo/psalm` | `psalm --taint-analysis --output-format=json > psalm-results.json` |
| `local-php-security-checker` | Dependency CVEs via Symfony Advisory DB | [download binary](https://github.com/fabpot/local-php-security-checker) | `local-php-security-checker --format=json > php-dep-results.json` |
| `semgrep` | SAST with PHP rule pack | (see Python) | `semgrep --config=p/php --json -o semgrep-results.json src/` |

---

**Rust** — detect via `.rs` files or `Cargo.toml`

| Tool | Purpose | Install | Command |
|---|---|---|---|
| `cargo audit` | Dependency CVEs via RustSec Advisory DB | `cargo install cargo-audit` | `cargo audit --json > cargo-audit-results.json` |
| `cargo deny` | Dependency policy: CVEs, licenses, duplicates | `cargo install cargo-deny` | `cargo deny check advisories --format json > cargo-deny-results.json` |
| `semgrep` | SAST with Rust rule pack | (see Python) | `semgrep --config=p/rust --json -o semgrep-results.json src/` |
| `clippy` | Lint including correctness + unsafe usage | built-in | `cargo clippy --message-format=json 2>&1 > clippy-results.json` |

---

**C / C++** — detect via `.c`, `.cpp`, `.h`, `.hpp` files or `Makefile` / `CMakeLists.txt`

| Tool | Purpose | Install | Command |
|---|---|---|---|
| `flawfinder` | Pattern-based C/C++ SAST | `pip install flawfinder` | `flawfinder --csv src/ > flawfinder-results.csv` |
| `cppcheck` | C/C++ static analysis with security checks | `brew install cppcheck` | `cppcheck --enable=all --output-file=cppcheck-results.xml --xml src/` |
| `clang-tidy` | LLVM-based linting with security checkers | `brew install llvm` | `clang-tidy src/**/*.cpp -checks='cert-*,clang-analyzer-security*' -export-fixes=clang-tidy-results.yaml` |
| `semgrep` | SAST with C/C++ rule pack | (see Python) | `semgrep --config=p/c --json -o semgrep-results.json src/` |

---

**Swift / Objective-C** — detect via `.swift`, `.m`, `.h` files or `*.xcodeproj` / `Package.swift`

| Tool | Purpose | Install | Command |
|---|---|---|---|
| `semgrep` | SAST with Swift/iOS rule pack | (see Python) | `semgrep --config=p/swift --json -o semgrep-results.json .` |
| `mobsfscan` | Mobile security SAST (iOS + Android) | `pip install mobsfscan` | `mobsfscan --json -o mobsfscan-results.json .` |
| `xcodebuild` analyzer | Clang static analyzer (built-in Xcode) | built-in | `xcodebuild analyze -scheme YourScheme \| xcpretty > xcode-analyze-results.txt` |

---

**Infrastructure as Code (IaC)** — detect via `*.tf`, `*.yaml`/`*.yml`, `Dockerfile`, `*.bicep`, `*.json` (CloudFormation)

| Tool | Purpose | Detects | Command |
|---|---|---|---|
| `checkov` | Comprehensive IaC SAST | Terraform, K8s, Dockerfile, CloudFormation, ARM, Bicep | `pip install checkov && checkov -d . --output json > checkov-results.json` |
| `trivy` | Filesystem + IaC + container scanning | Terraform, Dockerfile, K8s manifests | `brew install trivy && trivy fs --format json -o trivy-results.json .` |
| `tfsec` | Terraform-specific security scanner | Terraform only (more rules than checkov for TF) | `brew install tfsec && tfsec . --format json --out tfsec-results.json` |
| `kics` | IaC security scanning | Terraform, K8s, Dockerfile, Ansible, CloudFormation | `brew install kics && kics scan -p . -o kics-results/ --report-formats json` |
| `hadolint` | Dockerfile linting with security rules | Dockerfile only | `brew install hadolint && hadolint -f json Dockerfile > hadolint-results.json` |

---

**Multi-Language / Universal** — always run regardless of detected language

| Tool | Purpose | Notes |
|---|---|---|
| `semgrep` (OWASP pack) | OWASP Top 10 coverage across all languages | `semgrep --config=p/owasp-top-ten --json -o semgrep-owasp-results.json .` |
| `gitleaks` | Secrets across entire git history | `gitleaks detect --source . --report-format json --report-path gitleaks-results.json` |
| `truffleHog3` | Deep secrets scan including git history | `trufflehog3 filesystem . --format json > trufflehog-results.json` |
| `grype` | Universal dependency CVE scanner (all ecosystems) | `grype dir:. -o json > grype-results.json` |
| `syft` | SBOM generation (lists all dependencies) | `brew install syft && syft dir:. -o json > sbom.json` |
| `CodeQL` | Semantic analysis (GitHub Actions native) | Add `.github/workflows/codeql.yml`; supports C/C++, C#, Go, Java, JS/TS, Python, Ruby, Swift |
| `SonarQube` / `SonarCloud` | Comprehensive SAST + dependency checking | Docker: `docker run -d --name sonarqube -p 9000:9000 sonarqube:latest` then `sonar-scanner` |

---

#### Tool Availability and Fallback Strategy

When executing the security analysis, follow this decision tree for each tool:

1. **Check if the tool is available**: run `which {tool}` for system-wide/non-Python tools, or `.venv/bin/{tool} --version` for Python tools installed in the workspace virtual environment
2. **If available**: execute it immediately with JSON output
3. **If not available — try Docker**: check if Docker is running (`docker info`). If yes, use the container variant:
   - `semgrep`: `docker run --rm -v "$(pwd):/src" semgrep/semgrep semgrep --config=p/python --json /src`
   - `sonarqube`: `docker run -d -p 9000:9000 sonarqube:latest` then `sonar-scanner`
   - `trivy`: `docker run --rm -v "$(pwd):/src" aquasec/trivy fs --format json /src`
   - `checkov`: `docker run --rm -v "$(pwd):/src" bridgecrew/checkov -d /src --output json`
4. **If Docker is also unavailable — provide manual instructions**: document in `security/tool-scan-results/{tool-name}/SKIPPED.md` the exact command to run, what it checks, and why it was skipped. Include install instructions.
5. **Never fail silently**: always record which tools ran, which were skipped, and why, in `security/detailed-security-analysis.md` under "Scope & Methodology".

#### Tool Execution Workflow

> **Python virtual environment**: For all `pip install` commands and Python-based tool invocations in this workflow, use `.venv/bin/pip install` and `.venv/bin/<tool>` when the tool is installed in the workspace virtual environment. The `.venv/` must exist at the workspace root (see Step 0.5 in the Analysis Execution Steps section).

1. **Detect all languages and IaC types** from file extensions and manifest files
2. **Select applicable tools** from the per-language tables above, plus always run the Universal tools
3. **Check tool availability** for each; apply the fallback strategy above
4. **Execute in this order**: secrets scanners first → dependency scanners → SAST tools → universal tools
5. **Save all raw output** to `{output-base-path}/{project-name}/{timestamp}/security/tool-scan-results/{tool-name}/`
6. **Interpret each tool's output** using the Tool Output Interpretation Guide below
7. **Normalize findings** into the unified `vulnerability-report.json` schema
8. **De-duplicate** findings reported by multiple tools for the same issue (keep highest-confidence source)
9. **Assign final severity** using CVSS 3.1 base score where available, else use tool-assigned severity
10. **Proceed to AI-assisted manual review** to discover issues tools missed

#### Tool Output Interpretation Guide

After running each tool, interpret its output before incorporating findings. Each tool has different severity scales, false-positive rates, and coverage areas:

**`bandit` (Python)**
- Severity levels: HIGH / MEDIUM / LOW (map to finding severity directly)
- Confidence levels: HIGH / MEDIUM / LOW — **only promote LOW confidence findings if corroborated by code review**
- Common false positives: B101 (assert in tests — suppress in test files), B105/B106/B107 (hardcoded passwords in test fixtures), B311 (random for non-security use)
- High-signal test IDs to prioritize: B602 (subprocess shell=True), B301/B302 (pickle), B506 (unsafe YAML), B105 (hardcoded passwords outside tests), B608 (SQL injection)
- Interpretation: read `issue_text` and `test_id` fields; cross-reference `filename` and `line_number` with actual code to confirm exploitability

**`semgrep`**
- Severity from `extra.severity`: ERROR (critical/high), WARNING (medium), INFO (low)
- Check `extra.metadata.confidence` — LOW confidence rules generate many false positives
- Rules from `p/secrets` pack: treat any match as High until manually verified to be a real secret vs. example/test value
- Check `extra.metadata.cwe` and `extra.metadata.owasp` fields to classify the finding
- Interpretation: read `extra.message` for plain English explanation; `path` and `start.line` for location; `extra.fix` for suggested remediation if present

**`pip-audit` / `safety` / `npm audit` / `cargo audit` / `bundle-audit` / `govulncheck` / `grype`** (dependency scanners)
- These report **known CVEs against pinned versions** — all findings are true positives by definition
- Assess severity from the CVSS score in the CVE record, not the tool's own severity label
- Check if the vulnerable code path is actually reachable in the project (a CVE in a transitive dep that isn't called is still a finding but lower priority)
- For each CVE: note fixed version, check if upgrading would break compatibility, and record in `dependency-audit.md`
- `govulncheck` specifically reports only **reachable** vulnerable functions — treat its findings as higher priority than generic dependency scanners

**`detect-secrets` / `truffleHog3` / `gitleaks`** (secrets scanners)
- Every finding must be **manually verified** as a real secret vs. a test value, placeholder, or example string
- Follow up: check git history to see if a real secret was ever committed (`git log -S "found-value"`)
- Classify as: Real Secret (Critical), Historical Exposure (High, if in git history but removed), False Positive (suppress with explanation)
- For real secrets: immediately flag as Critical; remediation requires rotating the credential, not just removing the code

**`gosec` (Go)**
- Severity: HIGH / MEDIUM / LOW; Confidence: HIGH / MEDIUM / LOW
- Same confidence guidance as bandit — deprioritize LOW confidence unless code review confirms
- High-signal rule IDs: G101 (hardcoded credentials), G304 (file path from variable), G204 (subprocess with variable), G401-G403 (weak crypto), G501-G502 (weak hash)

**`brakeman` (Ruby/Rails)**
- Confidence levels: High / Medium / Weak — **Weak findings require manual verification before reporting**
- All High confidence findings are generally true positives
- Warning types to prioritize: `SQL`, `Command Injection`, `Remote Code Execution`, `Mass Assignment`, `Deserialize`

**`SpotBugs` + `find-sec-bugs` (Java)**
- Bug categories to prioritize: `INJECTION` (SQL, LDAP, XPath), `CRYPTO` (weak algorithms), `DESERIALIZATION`, `PATH_TRAVERSAL`
- Confidence (priority) levels 1-3: 1 = high confidence, 3 = low
- Cross-reference with source to confirm the data flow

**`psalm` (PHP taint analysis)**
- Taint errors are generally true positives — follow the taint source/sink chain in the output
- Taint sources: `TaintedInput`, `TaintedHtml`, `TaintedSql` — trace to the affected sink

**`cppcheck` / `flawfinder` (C/C++)**
- `flawfinder` risk levels 0-5: prioritize 4 and 5 (buffer overflows, format strings, race conditions)
- `cppcheck` severity: error > warning > style — focus on `error` and security-tagged warnings
- C/C++ findings almost always require code review to confirm exploitability given pointer complexity

**`checkov` / `trivy` / `tfsec` / `kics` (IaC)**
- Map checkov check IDs to CIS benchmarks or cloud provider security baselines
- `FAILED` checks are definite misconfigurations; assess true risk based on exposure (internet-facing vs. internal)
- trivy `CRITICAL`/`HIGH` misconfig findings should always be reported; `LOW` can be informational

**`CodeQL`**
- Results are high-confidence semantic findings — treat all results as true positives until code review disproves
- Alert rules map to CWEs; use the `@kind path-problem` alerts which show the full data-flow path
- Path-problem alerts (showing source → sink) are the most actionable — include the full path in findings

**General Interpretation Principles**
- **Corroboration**: a finding reported by two independent tools at the same location is almost certainly a true positive
- **Context is everything**: a `subprocess(shell=True)` is critical if the argument is user-controlled, informational if it's a hardcoded string
- **Severity adjustment**: downgrade tool-assigned severity if the finding is in test code, unreachable code, or behind strong input validation; upgrade if the attack surface is internet-facing
- **False positive documentation**: if a finding is suppressed as a false positive, document *why* in `vulnerability-report.json` under `suppressed_findings`

---

### AI-Assisted Security Discovery Methodology

Automated tools catch known patterns, but the AI agent can discover issues that require contextual reasoning. Apply these techniques systematically:

#### Technique 1 — Manual Taint Analysis

Trace user-controlled data from sources to sinks without relying on tool detection:

1. **Identify all input sources** in the codebase:
   - CLI arguments (`sys.argv`, `argparse`, `click` options)
   - Environment variables (`os.environ`)
   - File reads (contents of files specified by the user)
   - Network responses (data returned from external services)
   - Database query results
   - Deserialized data (JSON, YAML, pickle)

2. **Identify all dangerous sinks**:
   - OS command execution (`subprocess`, `os.system`, `eval`)
   - File system writes (with user-controlled paths)
   - SQL query construction
   - HTML rendering
   - Deserialization calls
   - Cryptographic operations

3. **Trace data flow** from each source to each sink:
   - Does user-controlled data reach the sink unmodified?
   - Are there validation/sanitization steps between source and sink?
   - Can the validation be bypassed?
   - Are there indirect paths through intermediate variables or function calls?

4. **Document each taint path** found, even if sanitized, noting the sanitization quality.

#### Technique 2 — Authentication & Authorization Logic Review

Manually review auth logic for flaws that tools miss:

1. **Authentication bypass conditions**: Are there code paths that skip authentication (early returns, exception handlers, feature flags)?
2. **Token validation completeness**: Are all required claims validated (expiry, issuer, audience, algorithm)?
3. **Session fixation**: Is the session token regenerated after privilege elevation?
4. **Credential comparison**: Are credentials compared in constant time to prevent timing attacks?
5. **Multi-step auth flow**: Can any step be skipped by crafting requests out of order?
6. **Password reset flows**: Is the reset token sufficiently random and short-lived?
7. **Auth state in shared memory**: Can auth state leak between requests in a concurrent environment?

#### Technique 3 — Business Logic Vulnerability Analysis

These are high-value findings that automated tools cannot detect:

1. **Order of operations flaws**: Can operations be performed in a sequence not intended by the designer?
2. **Numeric logic errors**: Integer overflow, float precision issues, off-by-one errors in security-relevant calculations
3. **State machine violations**: Can the system reach an invalid state through a sequence of valid operations?
4. **Negative value handling**: What happens if a quantity or count is negative?
5. **Boundary condition behavior**: What happens at exactly 0, MAX_INT, empty string, null/None?
6. **Trust boundaries**: Does the code correctly distinguish between trusted internal calls and untrusted external calls?

#### Technique 4 — Cryptographic Implementation Review

Review cryptographic code for implementation flaws:

1. Read every file that imports cryptographic libraries
2. Verify: correct algorithm, correct mode, correct key size
3. Check for: nonce/IV reuse, key derivation correctness, padding oracle susceptibility
4. Verify random number generation uses cryptographically secure sources
5. Check for custom cryptographic implementations (a red flag)
6. Verify certificate pinning or validation is not bypassed

#### Technique 5 — Dependency & Supply Chain Analysis

Beyond CVE lookups, reason about supply chain risk:

1. **Identify all direct dependencies** and their publishers
2. **Flag single-maintainer packages** with no governance structure
3. **Check for typosquatting risk**: are packages named suspiciously close to popular packages?
4. **Review postinstall scripts** in package.json or setup.py hooks that run on install
5. **Check for pinned vs. floating versions**: unpinned dependencies are a supply chain risk
6. **Review `setup.py` or `pyproject.toml` for install-time code execution**

#### Technique 7 — Attack Surface Enumeration

Systematically catalog every point where the system accepts untrusted input:

1. List all CLI entry points and their accepted inputs
2. List all network-facing endpoints (if applicable)
3. List all files read from disk that contain external data
4. List all environment variables that affect security behavior
5. List all external processes invoked
6. For each surface: what is the trust level of the input? What is the blast radius if exploited?

#### AI Security Review Checklist (run through each source file)

For every file in the codebase, answer:
- [ ] Does this file accept external input? If so, is it validated?
- [ ] Does this file perform OS command execution? Is the command data tainted?
- [ ] Does this file deserialize external data? Is the deserializer safe?
- [ ] Does this file perform cryptographic operations? Are the algorithms correct?
- [ ] Does this file handle credentials or secrets? Are they handled safely?
- [ ] Does this file write to the filesystem? Are paths sanitized?
- [ ] Does this file make network requests? Does it validate the response?
- [ ] Does this file log data? Could sensitive data appear in logs?
- [ ] Does this file share state across requests/calls? Could it leak between callers?
- [ ] Does this file use third-party libraries in a way consistent with their secure usage docs?

---

### Security Analysis Output Structure

```
security/
├── detailed-security-analysis.md   # Comprehensive narrative security report (primary deliverable)
├── vulnerability-report.json       # Machine-readable unified findings from all sources
├── remediation-guide.md            # Prioritized fix guide with code examples
├── attack-surface-map.md           # All external input points cataloged
├── dependency-audit.md             # Dependency vulnerability and supply chain analysis
└── tool-scan-results/
    ├── bandit/                      # bandit JSON output (Python)
    ├── semgrep/                     # semgrep JSON output
    ├── pip-audit/                   # pip-audit JSON output (Python)
    ├── detect-secrets/              # detect-secrets baseline
    ├── trufflehog/                  # truffleHog scan output
    └── {other-tools}/               # Additional tool outputs
```

#### `detailed-security-analysis.md` Document Structure

This is the primary deliverable — a thorough narrative report that combines AI analysis, tool findings, and manual review. Structure it as follows:

```markdown
# Security Analysis — {Project Name}
**Date:** {date}  **Analyst:** codebase-architecture-analyst  **Version:** {commit/tag}

## Executive Summary
- Overall risk profile (Critical / High / Medium / Low)
- Finding counts by severity
- Top 3 most critical issues (brief)
- Recommended immediate actions

## Scope & Methodology
- Files analyzed, lines of code, languages detected
- Tools executed (with exact versions and commands run)
- Tools skipped (with reason: not installed, Docker unavailable, not applicable)
- AI-assisted techniques applied
- Exclusions and limitations

## Tool Results Interpretation

For each tool that was executed, include a subsection:

### {Tool Name} — Summary
- **Version:** {version}
- **Command:** `{exact command run}`
- **Total raw findings:** {n}
- **After false-positive filtering:** {n}
- **Findings promoted to report:** {n}

**Key findings from this tool:**
Narrative description of the most significant findings, explaining *why* they matter in the context of this specific codebase, not just restating the tool output. Include:
- Which findings are confirmed true positives and why
- Which findings were suppressed as false positives and why
- Any patterns in the findings (e.g., "bandit flagged subprocess.run across 6 files — all pass user-controlled registry names")
- Coverage gaps this tool cannot address in this codebase

**False positives suppressed:**
List each suppressed finding with the reason (test-only code, constant value, unreachable, compensating control present).

*(Repeat this subsection for every tool executed)*

## Findings — Critical
Each finding includes:
- Finding ID (e.g., VULN-001), title, CWE/CVE reference
- Detected by: {tool(s) or "AI analysis"}
- Affected file(s) and line numbers
- Description of the vulnerability
- Proof of concept / attack scenario
- Remediation recommendation with code example
- References (OWASP, NVD, etc.)

## Findings — High
(same structure per finding)

## Findings — Medium
(same structure per finding)

## Findings — Low / Informational
(same structure per finding)

## Attack Surface Analysis
- Entry points enumerated
- Trust boundaries identified
- Data flow through sensitive operations

## Dependency Security Analysis
- Vulnerable dependencies with CVEs
- Abandoned/unmaintained packages
- Supply chain risk assessment

## Cryptographic Assessment
- Algorithms and implementations reviewed
- Compliance with current standards

## Authentication & Authorization Assessment
- Auth mechanism review
- Authorization logic review
- Credential handling review

## Secrets & Credential Exposure
- Findings from automated secret scanning
- Manual review results

## Security Posture Summary
- Strengths: what the codebase does well
- Weaknesses: systematic gaps
- Risk ranking of identified issues

## Remediation Roadmap
- Immediate (fix before next release)
- Short-term (fix within 30 days)
- Long-term (architectural improvements)

## Appendix A: Tool Commands Run
Full list of every command executed, with version and exit code.

## Appendix B: Files Reviewed
## Appendix C: CWE/CVE References
```

#### `vulnerability-report.json` Schema

```json
{
  "metadata": {
    "project": "string",
    "analysis_date": "ISO8601",
    "analyzer_version": "string",
    "languages_detected": ["python", "typescript", "terraform"],
    "tools_used": ["bandit", "semgrep", "pip-audit", "..."],
    "tools_skipped": [{"tool": "pysa", "reason": "not installed; Docker unavailable"}],
    "total_findings": 0,
    "findings_by_severity": {"critical": 0, "high": 0, "medium": 0, "low": 0, "info": 0},
    "suppressed_count": 0
  },
  "tool_results": [
    {
      "tool": "bandit",
      "version": "1.7.x",
      "command": "bandit -r src/ -f json -o bandit-results.json",
      "exit_code": 0,
      "raw_finding_count": 12,
      "promoted_finding_count": 4,
      "suppressed_finding_count": 8,
      "interpretation_summary": "string — narrative of key patterns, false positives removed, and coverage gaps",
      "raw_output_path": "tool-scan-results/bandit/bandit-results.json"
    }
  ],
  "findings": [
    {
      "id": "VULN-001",
      "title": "string",
      "severity": "critical|high|medium|low|info",
      "confidence": "high|medium|low",
      "category": "injection|auth|crypto|secrets|dependency|...",
      "cwe_ids": ["CWE-78"],
      "cve_ids": ["CVE-2024-XXXX"],
      "owasp_category": "A03:2021",
      "source": "bandit|semgrep|ai-analysis|manual",
      "corroborated_by": ["semgrep"],
      "file": "relative/path/to/file.py",
      "line_start": 42,
      "line_end": 45,
      "code_snippet": "string",
      "description": "string",
      "attack_scenario": "string",
      "remediation": "string",
      "remediation_code_example": "string",
      "references": ["https://..."]
    }
  ],
  "suppressed_findings": [
    {
      "tool": "bandit",
      "rule_id": "B101",
      "file": "tests/test_auth.py",
      "line": 15,
      "reason": "assert in test code — not reachable in production"
    }
  ],
  "scan_errors": []
}
```

---

## Usage Examples

### Initial Analysis
**Trigger**: "Analyze codebase architecture for /path/to/project"
**Result**: Complete deep analysis with file inventory, dependency maps, and human-readable insights

### Dependency Queries  
**Trigger**: "What files depend on UserService.py?"
**Result**: List of all files importing UserService with usage context

**Trigger**: "Show me the dependency tree for the authentication system"
**Result**: Visual dependency graph focused on auth-related components

### Human-Readable Insights
**Trigger**: "Give me a security overview of this system"
**Result**: Plain English security analysis highlighting strengths and concerns

**Trigger**: "Explain the architecture in simple terms"
**Result**: Clear, non-technical explanation of how the system is organized

### Impact Analysis
**Trigger**: "If I modify the database layer, what else will be affected?"
**Result**: Impact analysis showing all dependent components and potential breakage points

### Security Vulnerability Analysis
**Trigger**: "Run a security analysis on this codebase" / "Find security vulnerabilities" / "Perform a security audit"
**Result**: Complete `security/` output directory with:
- `detailed-security-analysis.md` — full findings narrative organized by severity
- `vulnerability-report.json` — machine-readable findings from tools + AI analysis
- `remediation-guide.md` — prioritized, actionable fixes with code examples
- `attack-surface-map.md` — all external input points with trust levels
- `dependency-audit.md` — CVE findings and supply chain risk assessment
- `tool-scan-results/` — raw output from each automated tool executed

**Trigger**: "What security tools should I run on this Python project?"
**Result**: Tailored tool list for the detected language with exact install and run commands

**Trigger**: "Check for hardcoded secrets" / "Scan for credentials in code"
**Result**: Focused secrets scanning using detect-secrets and truffleHog patterns + AI review of suspicious string literals

## Reference Materials (loaded as needed)

- **File Analysis Patterns**: See [references/source_file_analysis.md](references/source_file_analysis.md)
- **Dependency Query Guide**: See [references/dependency_queries.md](references/dependency_queries.md)  
- **Human-Readable Templates**: See [references/readable_analysis_templates.md](references/readable_analysis_templates.md)
- **Visual Generation Patterns**: See [references/enhanced_diagrams.md](references/enhanced_diagrams.md)

## Git Workflow Rules

These rules apply whenever this skill (or any agent using it) commits or pushes changes to a repository — including changes to the skill itself, generated output files, or any other workspace modifications.

**Branch safety:**
- **Never push directly to `main`** (or any other protected branch such as `master`, `release/*`, `production`). All changes must go through a feature branch.
- If a commit accidentally lands on `main` locally, cherry-pick it to the correct feature branch, then run `git fetch origin` and reset `main` to the updated `origin/main` before pushing anywhere.

**Branch selection:**
- Before running any `git push`, confirm which branch is active with `git branch` or `git status`.
- **If the target branch to push to is not explicitly known or was not specified by the user, always ask before pushing.** Do not infer or assume the correct branch.

**Suggested push workflow:**
```sh
# 1. Verify you are on the correct branch
git status

# 2. Stage and commit
git add <files>
git commit -m "<message>"

# 3. Push only the current feature branch — never main
git push origin <feature-branch-name>
```

## Analysis Execution Steps

### 0. Repository Setup (REQUIRED WHEN ANALYZING A REMOTE REPOSITORY)

When the user asks to analyze a GitHub repository (or any remote Git repository), follow this interactive setup before proceeding to the output location and analysis steps.

#### Step R1 — Ask for the Main Repository URL

Ask the user:

> "What is the URL of the main repository you would like to analyze? (e.g., `https://github.com/owner/repo`)"

Wait for the user's response and store it as `{main-repo-url}`. Derive the repository name (the last path segment without `.git`) and store it as `{repo-name}`.

#### Step R2 — Ask for the Clone Location

Ask the user:

> "Where would you like to clone the repository? Please provide an absolute path to the parent directory (e.g., `/Users/you/projects`). The repository will be cloned to `{clone-base-path}/{repo-name}/`."

Wait for the user's response. Store this parent directory path as `{clone-base-path}`, and the full clone destination as `{project-path}` (i.e., `{clone-base-path}/{repo-name}`). This path becomes the root for all subsequent analysis steps.

Clone the main repository:

```sh
git clone {main-repo-url} {project-path}
```

#### Step R3 — Ask About Dependent Repositories

Ask the user:

> "Does this repository depend on other repositories that should be included in the analysis? (yes / no)"

If the user answers **no**, proceed directly to [Step 0: Confirm Output Location](#0-confirm-output-location-required-first-step).

If the user answers **yes**, continue to Step R4.

#### Step R4 — Collect and Clone Dependency Repositories

For each dependency, ask for both the URL **and** the desired local path in a single prompt:

> "Please provide the URL of the dependency repository and the local path where it should be placed inside the main repository (e.g., `deps/my-library`). Providing an explicit path keeps dependencies organized and avoids collisions with existing directories."

Derive a suggested default path from the repository name (last URL segment without `.git`) using the convention `deps/{repo-name}` and offer it to the user:

> "Suggested path: `deps/{dep-repo-name}`. Press Enter to accept or type a different path."

For each URL + path pair provided:
1. Add it as a git submodule at the specified path inside the main repository:
   ```sh
   cd {project-path}
   git submodule add {dependency-url} {submodule-path}
   ```
2. Initialize and update all submodules recursively:
   ```sh
   git submodule update --init --recursive
   ```
3. Then ask:
   > "Is there another dependency repository to add? (yes / no)"
4. If yes, repeat this step with the next dependency URL and path.
5. If no, proceed to Step R5.

#### Step R5 — Confirm Dependencies and Proceed

After all submodule additions are complete, run:
```sh
git submodule status
```

Report the full list of cloned submodules to the user with their paths and commit SHAs, then confirm:

> "All repositories have been cloned. I'll now proceed with the full architectural analysis of `{repo-name}` and its dependencies."

Proceed to [Step 0: Confirm Output Location](#0-confirm-output-location-required-first-step). Use `{project-path}` as the root for all analysis.

---

### 0. Confirm Output Location (REQUIRED FIRST STEP)
- **If the repository was cloned in Step R2 above**, `{project-path}` is already known — treat it as the analysis workspace root. This workspace may be modified as part of repository setup (for example, running `git submodule add` / `git submodule update` against a fresh clone), but it must not be used as the destination for generated analysis artifacts.
- **Ask the user for an output path** before doing anything else: "Where would you like to save the analysis results? Please provide an absolute path (e.g., `/Users/you/analysis-results` or `~/Documents/code-analysis`). The output will be saved under `{your-path}/{project-name}/{timestamp}/` and will not be written into `{project-path}` or any of the repositories being analyzed."
- Wait for the user's response and store it as `{output-base-path}`
- Do NOT proceed until an explicit path is provided
- Do NOT default to saving inside `{project-path}` or any repository under analysis — **analysis output must not be written into the project working tree**.
- Before running any commands that modify git metadata or the working tree (such as `git submodule add` or `git submodule update`), explicitly confirm with the user that you are operating on a fresh, disposable analysis clone and remind them that no analysis results will be written into that clone.
- Create the output directory: `{output-base-path}/{project-name}/{timestamp}/`
- All files generated by this skill must be written under this path

### 0.5. Python Virtual Environment Setup

All Python analysis scripts and Python-based security scanning tools must be run using the project's virtual environment, **not** the system Python. This prevents dependency conflicts and ensures consistent tooling across environments.

**Set up the virtual environment** (one-time; skip if `.venv/` already exists at the workspace root):

```sh
python3 -m venv .venv
.venv/bin/pip install --upgrade pip
```

**Where `.venv/` lives**: at the root of the `software-engineering-skills` workspace — the same directory that contains the `.github/` folder where this skill resides. All commands below assume your current working directory is this workspace root.

**Mandatory rules for all subsequent steps:**
- Run all analysis scripts as: `.venv/bin/python3 .github/skills/codebase-architecture-analyst/scripts/<script>.py` — never bare `python3`
- Install all Python security scanning tools as: `.venv/bin/pip install <tool>` — never bare `pip install`
- Check Python tool availability as: `.venv/bin/<tool> --version` (not `which <tool>`, which may find a different system version)

### 1. Project Setup & File Discovery
- Extract project name from the analyzed project path and create the timestamped analysis folder at `{output-base-path}/{project-name}/{timestamp}/`
- Use search_subagent to discover ALL source files in the codebase, including files inside any git submodule directories (dependent repositories cloned in Step R4 above)
- Create source file inventory with complete file tracking, noting which files belong to the main repository vs. which belong to each submodule dependency
- Initialize analysis databases and query structures

### 2. Deep File-Level Analysis
- Run `.venv/bin/python3 .github/skills/codebase-architecture-analyst/scripts/analyze_source_files.py` on every discovered source file
- Extract documentation, comments, and code structure from each file
- Catalog functions, classes, exports, and interfaces per file
- Build comprehensive file-to-purpose mapping

### 3. Dependency & Relationship Mapping
- Execute `.venv/bin/python3 scripts/deep_dependency_analyzer.py` for granular dependency analysis
- Map file-to-file, function-to-function, and class relationships
- Build bi-directional dependency database for queries
- Generate impact analysis and circular dependency detection

### 4. Human-Readable Report Generation
- Run `.venv/bin/python3 scripts/generate_readable_reports.py` to create plain English insights
- Generate security overview, architecture summary, and component guides
- Create technology decision analysis and recommendations

### 5. Interactive Query System Setup
- Build dependency query database using analysis results
- Create interactive query engine for answering dependency questions
- Generate query examples and exploration guides

### 6. Visual Generation & Final Documentation
- Execute enhanced diagram generation with detailed relationships
- Create comprehensive documentation with file-level insights
- Generate complete analysis package with all outputs

### 7. Security Vulnerability Analysis

This phase produces the `security/` output directory with the detailed security report. Run after Phase 3 (dependency mapping) so the dependency data is available for supply chain analysis.

**Step 7a — Detect Language(s) and Select Tools**
- Identify primary language(s) from file extensions and build configuration
- Select applicable tools from the "Automated Tool Scanning Approach" section above
- Note any tools unavailable in the current environment with manual run instructions

**Step 7b — Execute Automated Scanners**
- Run each selected tool using the per-language tables in the Automated Tool Scanning Approach section
- Apply the Tool Availability and Fallback Strategy (check installed → try Docker → document skip)
- Save raw JSON output to `{output-base-path}/{project-name}/{timestamp}/security/tool-scan-results/{tool-name}/`
- For each completed scan, apply the Tool Output Interpretation Guide to filter false positives before promoting findings

**Step 7c — AI-Assisted Manual Review**
For each security test category:
1. Perform taint analysis (Technique 1): trace all input sources to dangerous sinks
2. Review auth/authorization logic (Technique 2) for bypass conditions
3. Audit cryptographic implementations (Technique 4) file by file
4. Run the AI Security Review Checklist on every source file that handles external data
5. Enumerate and document the complete attack surface (Technique 7)

**Step 7d — Normalize and De-duplicate Findings**
- Combine tool findings and AI-identified findings into `vulnerability-report.json`
- Remove duplicates where multiple tools report the same issue at the same line
- Assign final severity per CVSS 3.1 or tool-assigned severity
- Classify each finding by OWASP category and CWE ID

**Step 7e — Generate Security Documents**
1. Write `security/detailed-security-analysis.md` following the document structure template in the Security Analysis Output Structure section above
2. Write `security/remediation-guide.md` with prioritized fixes, ordered Critical → High → Medium → Low, including concrete code examples for each fix
3. Write `security/attack-surface-map.md` enumerating every external input point with trust level and blast radius
4. Write `security/dependency-audit.md` listing all dependencies, their versions, known CVEs, last-updated date, and maintenance status

**Step 7f — Update Security Overview**
- Update `analysis/security-overview.md` with a summary of findings from the detailed analysis
- Add a link to `security/detailed-security-analysis.md` for the full report

### 4. Documentation Creation
- Combine all analysis data into comprehensive documentation
- Use `assets/analysis_template.md` as structure guide
- Save final documentation to `{output-base-path}/{project-name}/{timestamp}/documentation.md`

### 5. Report Generation (Optional)
- Create interactive HTML report using `assets/report_template.html`
- Populate template with analysis data
- Save as `{output-base-path}/{project-name}/{timestamp}/report.html`

### 6. Evolution Analysis & Summary
- **Compare with previous versions** (if available) using `.venv/bin/python3 scripts/analyze_evolution.py`
- **Generate evolution report** showing architectural changes over time
- **Update project evolution summary** with cross-version insights
- **Create/update latest symlink** to point to current analysis
- **Display evolution insights** in conversation along with current analysis
- **Provide file links** to all generated documents including evolution reports