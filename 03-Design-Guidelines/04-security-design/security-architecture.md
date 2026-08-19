# Security Architecture Guidelines

> Best practices for security architecture design in CBOL Messaging Hub. Covers defense in depth, zero trust, least privilege, security boundaries, and secure by design principles.

## Core Security Principles

### Defense in Depth

```
┌─────────────────────────────────────────────────────────────┐
│                    Layer 7: Application                       │
│  - Input validation, output encoding, CSRF protection        │
├─────────────────────────────────────────────────────────────┤
│                    Layer 6: API Gateway                       │
│  - Authentication, rate limiting, WAF, request validation    │
├─────────────────────────────────────────────────────────────┤
│                    Layer 5: Service Mesh                      │
│  - mTLS, service-to-service auth, traffic encryption          │
├─────────────────────────────────────────────────────────────┤
│                    Layer 4: Network                           │
│  - Firewalls, network segmentation, VPC isolation             │
├─────────────────────────────────────────────────────────────┤
│                    Layer 3: Host                              │
│  - OS hardening, patch management, endpoint protection        │
├─────────────────────────────────────────────────────────────┤
│                    Layer 2: Data                              │
│  - Encryption at rest, access control, audit logging          │
├─────────────────────────────────────────────────────────────┤
│                    Layer 1: Physical                          │
│  - Data center security, access controls                       │
└─────────────────────────────────────────────────────────────┘
```

### Zero Trust Architecture

```
Core principles:
  1. Never trust, always verify
  2. Assume breach
  3. Least privilege access
  4. Micro-segmentation
  5. Continuous verification

Implementation:
  - Every request authenticated (even internal)
  - Every connection encrypted (mTLS)
  - Every access authorized (RBAC/ABAC)
  - Every action logged (audit trail)
  - Every device verified (device posture)
```

### Least Privilege

```
✅ Good:
  - Service account has only permissions it needs
  - Database user has SELECT/INSERT on specific tables, not DROP
  - API token has scoped permissions (read:messages, not admin)
  - Developer has read access in prod, write in dev

❌ Bad:
  - Service uses root/admin credentials
  - Database user has ALL PRIVILEGES on *.*
  - API token has full admin scope
  - Everyone has prod access
```

## Security Boundaries

### Trust Zones

```
┌─────────────────────────────────────────────────────────────┐
│                    Untrusted Zone (Internet)                  │
│  - End users, browsers, mobile apps                           │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTPS / WAF
┌──────────────────────────▼──────────────────────────────────┐
│                    DMZ (Perimeter)                            │
│  - API Gateway, Load Balancer, WAF                           │
│  - Public-facing services only                                │
└──────────────────────────┬──────────────────────────────────┘
                           │ mTLS / internal network
┌──────────────────────────▼──────────────────────────────────┐
│                    Application Zone (Trusted)                 │
│  - Application services, message brokers                      │
│  - Internal APIs                                              │
└──────────────────────────┬──────────────────────────────────┘
                           │ Network ACLs
┌──────────────────────────▼──────────────────────────────────┐
│                    Data Zone (Restricted)                     │
│  - Databases, caches, object storage                          │
│  - Only accessible from application zone                      │
└─────────────────────────────────────────────────────────────┘
```

### Network Segmentation

```yaml
# ✅ Good - network security groups
# API Gateway (DMZ)
security_groups:
  api_gateway:
    inbound:
      - from: 0.0.0.0/0
        port: 443  # HTTPS only
    outbound:
      - to: application_zone
        port: 8080  # Internal API

# Application services
  application:
    inbound:
      - from: api_gateway_sg
        port: 8080
      - from: application_sg  # Service-to-service
        port: 8080
    outbound:
      - to: data_zone_sg
        port: 3306  # MySQL
      - to: data_zone_sg
        port: 6379  # Redis

# Database (data zone)
  database:
    inbound:
      - from: application_sg
        port: 3306
      - from: application_sg
        port: 27017  # MongoDB
    outbound: []  # No outbound from database!
```

## Secure by Design

### Secure Development Lifecycle (SDLC)

```
Requirements → Design → Implementation → Testing → Deployment → Operations
     │            │           │              │           │            │
     ▼            ▼           ▼              ▼           ▼            ▼
  Security   Threat      Secure       Security    Security   Security
  require-   modeling    coding       testing     config     monitoring
  ments      (STRIDE)    guidelines   (SAST/DAST) (hardening) (SIEM)
```

### Security Requirements

```
✅ Include in every feature:
  - Authentication requirements (who can access?)
  - Authorization requirements (what can they do?)
  - Data classification (public/internal/confidential/restricted)
  - Encryption requirements (in transit, at rest)
  - Audit logging requirements (what actions to log?)
  - Rate limiting requirements (how many requests?)
  - Input validation requirements (what input is expected?)
```

## Authentication Architecture

### Authentication Flow

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐
│  Client  │────►│  API Gateway │────►│  Auth Service│
│          │     │  (validate    │     │  (issue/     │
│          │◄────│   JWT token)  │◄────│   validate   │
└──────────┘     └──────┬───────┘     │   tokens)    │
                         │               └──────────────┘
                         ▼
                  ┌──────────────┐
                  │ Application  │
                  │ Services     │
                  │ (trust token │
                  │  from GW)    │
                  └──────────────┘
```

### Token-Based Authentication

```
✅ JWT authentication:
  1. Client sends credentials (username/password) to auth service
  2. Auth service validates, issues access token (short-lived, 15min) + refresh token (7d)
  3. Client sends access token in Authorization: Bearer header
  4. API Gateway validates token signature + expiration
  5. Services trust token (no DB call needed)
  6. On expiration, client uses refresh token to get new access token

❌ Session-based authentication (for APIs):
  - Server-side session storage (scalability issue)
  - Sticky sessions required
  - Not RESTful
  - Hard to use with mobile/third-party clients
```

## Authorization Architecture

### RBAC + ABAC Hybrid

```
RBAC (Role-Based Access Control):
  - Roles: USER, AGENT, ADMIN, SYSTEM
  - Permissions: read:messages, send:messages, manage:conversations
  - Role → Permissions mapping

ABAC (Attribute-Based Access Control):
  - User attributes: userId, department, role
  - Resource attributes: ownerId, conversationId, status
  - Environment attributes: time, IP, device
  - Policy: "user can delete message if user is owner AND message is < 5min old"

Hybrid approach:
  - RBAC for coarse-grained access (can user access this feature?)
  - ABAC for fine-grained access (can user access this specific resource?)
```

```java
// ✅ Good - RBAC + ABAC hybrid
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    // RBAC: only authenticated users can send messages
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MessageResponse> send(@RequestBody SendMessageRequest request,
                                                  Authentication auth) {
        Long userId = (Long) auth.getPrincipal();

        // ABAC: user can only send to conversations they are a member of
        if (!conversationService.isMember(request.getConversationId(), userId)) {
            throw new AccessDeniedException("Not a member of this conversation");
        }

        return ResponseEntity.ok(messageService.send(request, userId));
    }

    // RBAC: only admin or owner can delete
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @messageSecurity.isOwner(authentication, #id)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        messageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

## Data Security

### Data Classification

| Classification | Description | Encryption | Access Control | Examples |
|---------------|-------------|-----------|---------------|----------|
| Public | Available to everyone | None | None | API docs, public profiles |
| Internal | Internal use only | TLS in transit | Employee access | Internal docs, metrics |
| Confidential | Sensitive business data | TLS + at rest | Need-to-know | Business logic, revenue |
| Restricted | Highly sensitive | TLS + at rest + field-level | Strict approval | Passwords, PII, payment |

### Encryption

```
✅ Encryption requirements:
  - In transit: TLS 1.2+ for all external, mTLS for internal
  - At rest: AES-256 for database, SSE for object storage
  - Field-level: AES-256-GCM for PII (phone, email, address)
  - Passwords: bcrypt (cost >= 12) or Argon2id
  - API keys: SHA-256 hash (store hash, not plaintext)
  - JWT: RS256 (asymmetric) or HS256 (symmetric, strong secret)

❌ Don't:
  - Use MD5/SHA1 for passwords (broken)
  - Use ECB mode for encryption (insecure)
  - Hardcode encryption keys in code
  - Store sensitive data in logs
  - Use custom encryption algorithms
```

## Security Monitoring

### Audit Logging

```
✅ Log these security events:
  - Authentication: login success/failure, logout, token refresh
  - Authorization: permission denied, role change
  - Data access: view/modify/delete restricted data
  - Configuration: security config changes, key rotation
  - Admin actions: user management, system config

✅ Log fields:
  - timestamp, eventType, userId, sessionId, sourceIp
  - resourceType, resourceId, action, result
  - userAgent, requestId, traceId

❌ Don't log:
  - Passwords, tokens, API keys
  - Full credit card numbers, SSN
  - Sensitive PII (use masking)
  - Request/response bodies with sensitive data
```

### Security Alerts

```
✅ Alert on:
  - Multiple failed logins (brute force)
  - Login from unusual location/IP
  - Privilege escalation
  - Access to restricted data outside business hours
  - Unusual API call patterns (scraping, data exfiltration)
  - Security config changes
  - New admin user creation
  - Database export/dump operations
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Security through obscurity | Relying on secrecy for security | Use proven security mechanisms, open design |
| Single security layer | One breach = total breach | Defense in depth, multiple layers |
| Trust internal network | Internal attacks possible | Zero trust, verify every request |
| Overly permissive access | Too much access, data breach risk | Least privilege, regular access reviews |
| No security requirements | Security bolted on at end | Security by design, include in requirements |
| No threat modeling | Unknown attack surface | STRIDE threat modeling for every feature |
| Hardcoded credentials | Source leak = credential compromise | Secret manager, environment variables |
| No encryption at rest | Stolen DB = data breach | AES-256 encryption at rest |
| No audit logging | Can't investigate incidents | Comprehensive audit logging |
| No security monitoring | Breaches go undetected | SIEM, security alerts, anomaly detection |
| Security team only responsible | Developers don't care about security | Shift-left, everyone responsible for security |
| No security training | Developers introduce vulnerabilities | Regular secure coding training |
| Disabling security to pass CI | False sense of security | Fix issues, don't suppress rules |
| No incident response plan | Chaos during security incident | Documented IR plan, regular drills |
| Storing JWT in localStorage | XSS can steal token | Use httpOnly cookies, short-lived tokens |
| No token expiration | Stolen token persists forever | Short access token (15min), refresh token rotation |

## References

- OWASP Top 10: https://owasp.org/www-project-top-ten/
- OWASP ASVS: https://owasp.org/www-project-application-security-verification-standard/
- NIST Zero Trust Architecture: https://csrc.nist.gov/publications/detail/sp/800-207/final
- Defense in Depth: https://www.cisa.gov/defense-depth
- STRIDE Threat Modeling: https://learn.microsoft.com/en-us/azure/security/develop/threat-modeling-tool-threats
- Secure by Design: https://www.cisa.gov/securebydesign
- NIST Cybersecurity Framework: https://www.nist.gov/cyberframework
- CIS Benchmarks: https://www.cisecurity.org/cis-benchmarks
