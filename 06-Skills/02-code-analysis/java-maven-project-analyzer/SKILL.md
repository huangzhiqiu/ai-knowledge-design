---
name: java-maven-project-analyzer
description: Analyze a Java Maven multi-module project and generate PROJECT_ARCHITECTURE.md covering API interfaces, data models, core business logic, system boundaries, module dependencies, and external integrations. Use this skill when asked to "analyze the Java project", "map the Maven architecture", "document the project structure", "extract APIs and data models", "understand the codebase", "onboard to this Java project", or any time you need to systematically understand a Java/Maven codebase. Triggers on presence of pom.xml files.
---

# Java Maven Project Analyzer

Generate a comprehensive architectural map for Java Maven multi-module projects, covering API interfaces, data models, core business logic, system boundaries, and dependencies.

## Output

Create `PROJECT_ARCHITECTURE.md` in the project root. This file should be committed to version control.

## Process

Follow these phases in order. Use `glob`, `grep`, `read`, and `bash` tools extensively.

---

### Phase 1: Maven Structure Discovery

**1.1 Find all pom.xml files**
```bash
find . -name "pom.xml" -not -path "*/target/*" | sort
```

**1.2 Read the root pom.xml**
Extract:
- `groupId`, `artifactId`, `version`
- `<modules>` list (module hierarchy)
- `<properties>` (Java version, Spring Boot version, dependency versions)
- `<dependencyManagement>` (BOM imports, managed versions)
- `<build>` (plugins, Java compiler version)

**1.3 Read each sub-module pom.xml**
For each module, extract:
- Module name and description
- Dependencies (`<dependencies>`) — categorize as:
  - Internal modules (same groupId)
  - Spring Boot starters
  - Data access (MyBatis, JPA, Redis client, MongoDB)
  - Middleware (Kafka, RabbitMQ, Netty, gRPC)
  - Utilities (Lombok, Jackson, Guava, Apache Commons)
  - Testing (JUnit, Mockito, Testcontainers)
- Packaging type (`jar`, `war`, `pom`)
- Build plugins (Spring Boot Maven Plugin, etc.)

**1.4 Build module dependency graph**
Create a table showing which modules depend on which:
| Module | Depends On (Internal) | External Key Dependencies | Role |
|--------|----------------------|--------------------------|------|

---

### Phase 2: API Interface Extraction

**2.1 Find all Controller classes**
```bash
grep -rl "@RestController\|@Controller" --include="*.java" src/
```

**2.2 For each Controller, extract:**
- Class name and package
- Base URL (`@RequestMapping` at class level)
- All endpoints:
  - HTTP method (`@GetMapping`, `@PostMapping`, etc.)
  - Path
  - Method name
  - Request parameters/body type
  - Return type
  - Auth annotations (`@PreAuthorize`, `@Secured`)

**2.3 Find Feign / RPC clients**
```bash
grep -rl "@FeignClient\|@DubboService\|@DubboReference" --include="*.java" src/
```
Extract service name, URL, and interface methods.

**2.4 Find WebSocket / Netty handlers**
```bash
grep -rl "ChannelHandler\|@ServerEndpoint\|WebSocketHandler" --include="*.java" src/
```

**2.5 Organize APIs by domain module**
Group endpoints by business domain (user, message, conversation, etc.).

---

### Phase 3: Data Model Extraction

**3.1 Find Entity classes**
```bash
grep -rl "@Entity\|@TableName\|@Document" --include="*.java" src/
```
For each entity extract:
- Class name, table name
- Fields: name, type, annotations (`@Id`, `@Column`, `@TableField`)
- Relationships (`@OneToMany`, `@ManyToOne`, foreign keys)

**3.2 Find DTO / VO / Request / Response classes**
```bash
find src -name "*DTO.java" -o -name "*VO.java" -o -name "*Request.java" -o -name "*Response.java" -o -name "*Param.java"
```
Categorize by module and purpose.

**3.3 Find Enum classes**
```bash
find src -name "*Enum.java" -o -name "*Type.java" | grep -v target
```
List all enums and their values (status codes, message types, etc.).

**3.4 Find MyBatis Mapper / JPA Repository interfaces**
```bash
find src -name "*Mapper.java" -o -name "*Repository.java" -o -name "*Dao.java"
```
Note custom query methods.

**3.5 Find database migration scripts**
```bash
find . -path "*/db/migration/*" -o -path "*/sql/*" -name "*.sql" | grep -v target
```

---

### Phase 4: Core Business Logic

**4.1 Find Service classes**
```bash
find src -name "*Service.java" -o -name "*ServiceImpl.java" | grep -v target
```

**4.2 For each key Service, extract:**
- Class name and interface
- Public methods (name, parameters, return type)
- Key business logic summary (1-2 sentences per method)
- External dependencies (other services, mappers, external clients)

**4.3 Find state machines / workflow engines**
```bash
grep -rl "StateMachine\|@Stateful\|Workflow\|FlowEngine" --include="*.java" src/
```
Document states, transitions, and triggering events.

**4.4 Find event listeners / consumers**
```bash
grep -rl "@KafkaListener\|@RabbitListener\|@EventListener\|@StreamListener" --include="*.java" src/
```
Document: topic/queue name, event type, processing logic.

**4.5 Find scheduled tasks**
```bash
grep -rl "@Scheduled\|@XxlJob\|@ElasticJob" --include="*.java" src/
```
Document: cron expression, task name, purpose.

**4.6 Identify critical business flows**
For the top 3-5 most important business operations (e.g., "send message", "transfer to agent"), trace the full call chain:
```
Controller -> Service -> (StateMachine) -> Mapper/Repository -> DB
                                    -> EventPublisher -> MQ -> Consumer
                                    -> ExternalClient -> Third-party API
```

---

### Phase 5: System Boundaries & External Integrations

**5.1 Find configuration files**
```bash
find src/main/resources -name "application*.yml" -o -name "application*.yaml" -o -name "application*.properties" -o -name "bootstrap*.yml"
```
Extract:
- Server port, context path
- DataSource config (URL pattern, not credentials)
- Redis, Kafka, RabbitMQ, Elasticsearch configs
- External service URLs (mask sensitive parts)
- Feature flags

**5.2 Find external API calls**
```bash
grep -rn "RestTemplate\|WebClient\|HttpClient\|OkHttp\|FeignClient" --include="*.java" src/ | grep -v "import\|target"
```
Document each external integration:
| Integration | Protocol | Purpose | Config Key | Module |
|-------------|----------|---------|------------|--------|

**5.3 Find caching layers**
```bash
grep -rn "@Cacheable\|@CacheEvict\|RedisTemplate\|StringRedisTemplate" --include="*.java" src/
```
Document cache names, key patterns, TTL.

**5.4 Document system context diagram**
ASCII diagram showing:
- This system (with modules)
- Upstream systems (callers)
- Downstream systems (databases, MQ, external APIs, caches)

---

### Phase 6: Cross-Cutting Concerns

**6.1 Security**
- Auth mechanism (JWT, OAuth2, Session, API Key)
- Security filter chain configuration
- Permission/role model

**6.2 Exception handling**
- Global exception handler (`@ControllerAdvice`, `@RestControllerAdvice`)
- Custom exception hierarchy
- Error code enum

**6.3 Logging**
- Logging framework (Logback, Log4j2)
- MDC/trace ID usage
- Sensitive data masking

**6.4 Interceptors / AOP**
- Request interceptors
- AOP aspects (logging, performance, transaction)

**6.5 Testing**
- Test framework and structure
- Key test utilities
- Integration test approach (Testcontainers, MockMvc)

---

### Phase 7: Document Generation

Write `PROJECT_ARCHITECTURE.md` with these sections:

```markdown
# Project Architecture

## 1. Project Overview
- Business purpose, primary users, core value proposition (3-5 sentences)

## 2. Tech Stack & Versions
| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Language | Java | 17 | |
| Framework | Spring Boot | 3.x | |
| Build | Maven | 3.x | |
| Database | MySQL | 8.0 | |
| ... | | | |

## 3. Maven Module Structure
### 3.1 Module Hierarchy (tree)
### 3.2 Module Dependency Table
### 3.3 Module Responsibility Summary

## 4. Architecture Diagram (ASCII)
[System context + internal module layout]

## 5. API Interfaces
### 5.1 REST Endpoints (by domain)
### 5.2 Feign/RPC Clients
### 5.3 WebSocket/Netty Endpoints

## 6. Data Model
### 6.1 Core Entities (with fields and relationships)
### 6.2 DTO/VO Summary
### 6.3 Key Enums
### 6.4 Database Schema Summary

## 7. Core Business Logic
### 7.1 Service Layer Overview
### 7.2 Critical Business Flows (call chains)
### 7.3 State Machines
### 7.4 Event Consumers & Producers
### 7.5 Scheduled Tasks

## 8. System Boundaries
### 8.1 External Integrations Table
### 8.2 Caching Strategy
### 8.3 Configuration Summary

## 9. Cross-Cutting Concerns
### 9.1 Security
### 9.2 Exception Handling
### 9.3 Logging & Tracing
### 9.4 Interceptors/AOP

## 10. Key Files Entry Points
[10-15 most important files with one-line description]

## 11. Development Notes
- Build commands
- Run commands
- Testing approach
- Known limitations / TODOs
```

---

## Constraints

- **NO raw code dumps** — summarize logic in prose and tables
- **DO NOT expose credentials** — mask passwords, tokens, API keys in config excerpts
- Use bullet points, tables, and ASCII diagrams
- Be specific about fully-qualified class names and file paths
- If a section doesn't apply, note "N/A" and explain why
- Keep the document under 800 lines — it's a map, not a novel
- For large projects (>20 modules), focus on top-level modules and drill into key ones
- Use `mvn dependency:tree` if available for accurate dependency analysis

## AGENTS.md Integration

After creating `PROJECT_ARCHITECTURE.md`:
1. Check if `AGENTS.md` exists in project root
2. If it does not reference `PROJECT_ARCHITECTURE.md`, add:
```markdown
## Project Architecture Context
Before making any changes, ALWAYS read `PROJECT_ARCHITECTURE.md` in the project root.
It contains: tech stack, Maven module structure, API interfaces, data models,
core business logic locations, system boundaries, and external integrations.
```
3. If `AGENTS.md` does not exist, create it with this section.

## Completion

Inform the user:
> Architecture map saved to `PROJECT_ARCHITECTURE.md`
> AGENTS.md updated to reference architecture context.
> Suggest committing both files to version control.
