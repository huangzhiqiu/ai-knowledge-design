# Threat Modeling Guidelines

> Best practices for threat modeling in CBOL Messaging Hub. Covers STRIDE methodology, data flow diagrams, attack surface analysis, risk assessment, and mitigation strategies.

## What is Threat Modeling?

```
Threat modeling = systematic approach to identify, prioritize, and mitigate security threats

Process:
  1. Decompose application (understand what we're building)
  2. Identify threats (what could go wrong?)
  3. Mitigate threats (how do we prevent/respond?)
  4. Validate threats (did we miss anything?)

Output:
  - Data Flow Diagrams (DFD)
  - Threat list with severity ratings
  - Mitigation plan
  - Security requirements
```

## When to Threat Model

```
✅ Threat model when:
  - Designing a new feature/service
  - Making significant architecture changes
  - Adding new external integrations
  - Handling new types of sensitive data
  - Before security review/audit
  - After a security incident (post-mortem)

❌ Don't threat model:
  - Trivial UI changes (no new data flow)
  - Internal refactoring (no external interface change)
  - Bug fixes (no new attack surface)
```

## STRIDE Methodology

### STRIDE Categories

| Category | Threat | Description | Example |
|----------|--------|-------------|---------|
| **S**poofing | Pretending to be someone else | Fake user identity, forged JWT |
| **T**ampering | Modifying data or code | Message content modification, API request tampering |
| **R**epudiation | Denying an action | User denies sending a message, no audit log |
| **I**nformation Disclosure | Exposing data to unauthorized parties | Message content leaked, PII in logs |
| **D**enial of Service | Making system unavailable | Flood WebSocket connections, API rate limit bypass |
| **E**levation of Privilege | Gaining unauthorized access | User becomes admin, vertical privilege escalation |

### STRIDE by Element Type

| Element | S | T | R | I | D | E |
|---------|---|---|---|---|---|---|
| External Entity | ✅ | | ✅ | | | |
| Process | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Data Store | | ✅ | ✅ | ✅ | ✅ | |
| Data Flow | ✅ | ✅ | | ✅ | | |

## Threat Modeling Process

### Step 1: Create Data Flow Diagram (DFD)

```
Elements:
  - External Entity (square): User, Third-party API
  - Process (circle): Auth Service, Message Service
  - Data Store (two parallel lines): MySQL, Redis, MongoDB
  - Data Flow (arrow): Request/Response between elements
  - Trust Boundary (dotted line): Boundary between trust levels

Example DFD for CBOL Messaging:

  ┌──────────┐        ┌─────────────────────────────────────────────┐
  │   User   │────────│  Trust Boundary (Internet ↔ Internal)       │
  │ (Browser)│        │                                             │
  └──────────┘        │   ┌──────────┐    ┌──────────────────┐   │
                      │   │API Gateway│───►│  Auth Service    │   │
                      │   └────┬─────┘    └────────┬─────────┘   │
                      │        │                     │             │
                      │        ▼                     ▼             │
                      │   ┌──────────┐    ┌──────────────────┐   │
                      │   │ Message  │───►│  MySQL (users)   │   │
                      │   │ Service  │    └──────────────────┘   │
                      │   └────┬─────┘                           │
                      │        │                                 │
                      │        ▼                                 │
                      │   ┌──────────┐    ┌──────────────────┐   │
                      │   │ MongoDB  │    │  Redis (cache)   │   │
                      │   │(messages)│    └──────────────────┘   │
                      │   └──────────┘                           │
                      └─────────────────────────────────────────────┘
```

### Step 2: Identify Threats (STRIDE per element)

```
For each element in DFD, ask STRIDE questions:

External Entity (User):
  - Spoofing: Can an attacker impersonate a user?
  - Repudiation: Can a user deny sending a message?

Process (Message Service):
  - Spoofing: Can an attacker impersonate the service?
  - Tampering: Can an attacker modify message processing logic?
  - Repudiation: Can the service deny processing a message?
  - Info Disclosure: Can an attacker extract data from the service?
  - DoS: Can an attacker crash or overload the service?
  - Elevation: Can an attacker gain admin access through the service?

Data Store (MySQL - users):
  - Tampering: Can an attacker modify user data?
  - Repudiation: Can data modification be denied?
  - Info Disclosure: Can an attacker read user data?
  - DoS: Can an attacker make DB unavailable?

Data Flow (User → API Gateway):
  - Spoofing: Can an attacker forge requests?
  - Tampering: Can an attacker modify requests in transit?
  - Info Disclosure: Can an attacker eavesdrop on requests?
```

### Step 3: Rate Threats (Risk = Likelihood × Impact)

#### Likelihood Ratings

| Rating | Score | Description |
|--------|-------|-------------|
| Low | 1 | Requires rare conditions, skilled attacker, or specific vulnerability |
| Medium | 2 | Possible with moderate effort, common conditions |
| High | 3 | Easily exploitable, common conditions, automated tools available |

#### Impact Ratings

| Rating | Score | Description |
|--------|-------|-------------|
| Low | 1 | Minor inconvenience, no data loss, recoverable |
| Medium | 2 | Some data exposure, service degradation, recoverable with effort |
| High | 3 | Significant data breach, service outage, regulatory violation, financial loss |

#### Risk Matrix

| | Impact Low (1) | Impact Medium (2) | Impact High (3) |
|---|---|---|---|
| **Likelihood High (3)** | 3 (Medium) | 6 (High) | 9 (Critical) |
| **Likelihood Medium (2)** | 2 (Low) | 4 (Medium) | 6 (High) |
| **Likelihood Low (1)** | 1 (Low) | 2 (Low) | 3 (Medium) |

| Risk Score | Level | Action |
|-----------|-------|--------|
| 7-9 | Critical | Must fix before release, immediate mitigation |
| 4-6 | High | Fix in current release, plan mitigation |
| 2-3 | Medium | Fix in next release, accept with justification |
| 1 | Low | Accept, monitor, fix if convenient |

### Step 4: Mitigate Threats

#### Mitigation Strategies

| Strategy | Description | Example |
|----------|-------------|---------|
| **Avoid** | Eliminate the feature/functionality that creates the threat | Remove deprecated API endpoint |
| **Prevent** | Implement controls to stop the threat | Input validation, authentication, encryption |
| **Detect** | Implement controls to detect the threat | Audit logging, intrusion detection, anomaly detection |
| **Transfer** | Shift risk to third party | Insurance, CDN, managed security service |
| **Accept** | Accept the risk with justification | Low risk, cost of mitigation > impact |

#### Common Mitigations by STRIDE

| STRIDE | Mitigation |
|--------|-----------|
| Spoofing | Strong authentication, MFA, certificate validation, API keys |
| Tampering | Input validation, integrity checks (HMAC), digital signatures, immutable audit logs |
| Repudiation | Audit logging, digital signatures, timestamping, non-repudiation protocols |
| Info Disclosure | Encryption (at rest + in transit), least privilege, data masking, access control |
| DoS | Rate limiting, load balancing, auto-scaling, circuit breakers, resource quotas |
| Elevation of Privilege | RBAC/ABAC, least privilege, separation of duties, privilege escalation monitoring |

## CBOL Messaging Threat Model Example

### Identified Threats

| # | Element | STRIDE | Threat | Likelihood | Impact | Risk | Mitigation |
|---|---------|--------|--------|-----------|--------|------|-----------|
| 1 | User → API Gateway | S | Attacker forges JWT token | Medium | High | 6 (High) | JWT signature validation, short TTL, refresh token rotation |
| 2 | User → API Gateway | I | Eavesdrop on WebSocket messages | Low | High | 3 (Medium) | WSS (TLS), HSTS, certificate pinning |
| 3 | Message Service | T | Attacker modifies message content | Medium | High | 6 (High) | Input validation, server-side validation, immutable message content after send |
| 4 | Message Service | D | Flood WebSocket connections | High | Medium | 6 (High) | Rate limiting per user/IP, connection limits, auto-scaling |
| 5 | MySQL (users) | I | SQL injection exposes user data | Low | High | 3 (Medium) | Prepared statements, ORM, input validation, least privilege DB user |
| 6 | MongoDB (messages) | I | Unauthorized message access | Medium | High | 6 (High) | RBAC/ABAC, query validation, shard key = recipientId |
| 7 | User | R | User denies sending message | Medium | Medium | 4 (Medium) | Audit logging, message timestamps, digital signatures (optional) |
| 8 | Auth Service | E | Vertical privilege escalation | Low | High | 3 (Medium) | RBAC, least privilege, privilege escalation monitoring, separation of duties |
| 9 | API Gateway | D | API rate limit bypass | Medium | Medium | 4 (Medium) | Rate limiting at gateway + service level, distributed rate limiter (Redis) |
| 10 | Redis (cache) | I | Cache poisoning | Low | Medium | 2 (Low) | Cache validation, TTL, cache-aside pattern, input validation on cache keys |

### Attack Surface Analysis

```
External Attack Surface (Internet-facing):
  - REST API endpoints (/api/v1/*)
  - WebSocket endpoint (/ws)
  - Authentication endpoints (/auth/*)
  - Static assets (if any)

Internal Attack Surface (within trust boundary):
  - Service-to-service communication
  - Database connections
  - Cache (Redis)
  - Message queue (Kafka/RocketMQ)
  - Configuration management
  - Admin/management endpoints

Attack Surface Reduction:
  - Minimize external endpoints (API gateway as single entry point)
  - No direct database access from Internet
  - No admin endpoints exposed to Internet
  - Use private networking for internal services
  - Disable unnecessary features/endpoints
```

## Threat Modeling Checklist

### Design Phase

- [ ] Create Data Flow Diagram (DFD)
- [ ] Identify trust boundaries
- [ ] Identify external entities
- [ ] Identify data stores (with data classification)
- [ ] Identify data flows (with data classification)
- [ ] Apply STRIDE to each element
- [ ] Rate each threat (likelihood × impact)
- [ ] Identify mitigations for High/Critical threats
- [ ] Document security requirements
- [ ] Review with security team

### Implementation Phase

- [ ] Implement authentication/authorization
- [ ] Implement input validation
- [ ] Implement encryption (at rest + in transit)
- [ ] Implement audit logging
- [ ] Implement rate limiting
- [ ] Implement secure configuration
- [ ] Run SAST (Static Application Security Testing)
- [ ] Run dependency vulnerability scan

### Testing Phase

- [ ] Run DAST (Dynamic Application Security Testing)
- [ ] Penetration testing (for High/Critical threats)
- [ ] Security regression testing
- [ ] Verify mitigations are effective
- [ ] Test edge cases and error paths

### Deployment Phase

- [ ] Security configuration review
- [ ] Secret management verification
- [ ] Monitoring and alerting setup
- [ ] Incident response plan ready
- [ ] Security sign-off

## Threat Modeling Tools

| Tool | Type | Use For |
|------|------|---------|
| Microsoft Threat Modeling Tool | Desktop | DFD creation, STRIDE analysis, Windows only |
| OWASP Threat Dragon | Web/Desktop | DFD, STRIDE, LINDDUN, open source |
| IriusRisk | Commercial | Full threat modeling platform, risk management |
| ThreatModeler | Commercial | Automated threat modeling, continuous monitoring |
| Draw.io / Lucidchart | Diagram | DFD creation (manual STRIDE analysis) |
| Plain Markdown | Text | Simple threat models, version control friendly |

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Threat modeling once, never updating | New threats introduced by changes | Threat model on every significant change |
| Too abstract, no actionable items | Can't implement mitigations | Specific threats with concrete mitigations |
| Only security team does it | Developers don't understand/own threats | Collaborative, developers participate |
| Ignoring low-risk threats | Accumulated risk | Document and accept with justification |
| No DFD | Can't systematically identify threats | Always create DFD first |
| No trust boundaries | Miss cross-boundary threats | Explicitly mark trust boundaries |
| Only technical threats | Miss business/process threats | Include business logic threats, social engineering |
| No risk rating | Can't prioritize | Rate every threat (likelihood × impact) |
| No mitigation verification | Threats "mitigated" on paper only | Test and verify mitigations |
| Threat model as checkbox | No real security value | Integrate into SDLC, actionable output |
| No data classification | Can't assess impact of info disclosure | Classify data (public/internal/confidential/restricted) |
| Ignoring third-party components | Supply chain threats | Include dependencies, libraries, external APIs in threat model |

## References

- STRIDE: https://learn.microsoft.com/en-us/azure/security/develop/threat-modeling-tool-threats
- Microsoft Threat Modeling Tool: https://learn.microsoft.com/en-us/azure/security/develop/threat-modeling-tool
- OWASP Threat Dragon: https://owasp.org/www-project-threat-dragon/
- OWASP Threat Modeling Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Threat_Modeling_Cheat_Sheet.html
- NIST SP 800-154 (Guide to Data-Centric System Threat Modeling): https://csrc.nist.gov/publications/detail/sp/800-154/final
- PASTA (Process for Attack Simulation and Threat Analysis): https://versprite.com/pasta/
- LINDDUN (privacy threat modeling): https://www.linddun.org/
- Attack Trees: https://www.schneier.com/academic/archives/1999/12/attack_trees.html
- CVSS (Common Vulnerability Scoring System): https://www.first.org/cvss/
