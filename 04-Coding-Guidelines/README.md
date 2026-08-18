# Coding Guidelines

> Industry-standard coding standards, security, quality, and best practices.

## Documents

| Document | Description |
|----------|-------------|
| [java-coding-standards.md](./java-coding-standards.md) | Google + Alibaba Java coding standards |
| [security-guidelines.md](./security-guidelines.md) | OWASP Top 10, secure coding, cryptography |
| [code-quality.md](./code-quality.md) | Complexity, coverage, SonarQube quality gates, code smells |
| [concurrency-guidelines.md](./concurrency-guidelines.md) | Thread pools, thread safety, async programming |
| [exception-and-logging.md](./exception-and-logging.md) | Exception handling, error codes, structured logging |
| [websocket-guidelines.md](./websocket-guidelines.md) | WebSocket protocol, security, connection management, performance, Netty |
| [sonar-rules.md](./sonar-rules.md) | SonarQube rules and quality configuration |

## Coding Standards Summary

### Naming & Formatting
- Classes: UpperCamelCase; Methods/Variables: lowerCamelCase; Constants: UPPER_SNAKE_CASE
- 4-space indent, max 120 chars/line, braces always used
- No wildcard imports, remove unused

### OOP & Collections
- BigDecimal for money, never float/double
- Specify collection initial capacity
- Use entrySet() for Map iteration
- Use isEmpty() not size()==0

### Concurrency
- Thread pools with bounded queues, meaningful thread names
- ThreadLocal MUST be cleaned up (try-finally)
- Concurrent collections, immutable objects, atomic variables
- Avoid shared mutable state

### Exception Handling
- Never catch Error/Throwable
- Never swallow exceptions (log and handle)
- Catch specific exceptions, not Exception
- Custom business exception hierarchy with error codes
- Global exception handler (Spring @RestControllerAdvice)

## Security Summary

### OWASP Top 10 (2021)
A01 Broken Access Control, A02 Cryptographic Failures, A03 Injection, A04 Insecure Design, A05 Security Misconfiguration, A06 Vulnerable Components, A07 Auth Failures, A08 Integrity Failures, A09 Logging Failures, A10 SSRF

### Key Rules
- Validate all input (whitelist, not blacklist)
- Parameterized queries (no SQL injection)
- Strong crypto: bcrypt/argon2 for passwords, AES-256-GCM, TLS 1.2+
- SecureRandom for security contexts
- No hardcoded secrets
- Mask sensitive data in logs
- Security headers (CSP, HSTS, X-Frame-Options)

## Quality Summary

### Thresholds
- Cyclomatic complexity ≤ 10/function
- Method ≤ 50 lines, Class ≤ 500 lines
- Duplication ≤ 3%
- Line coverage ≥ 80%, new code ≥ 90%
- 0 blocker/critical issues

### SonarQube Quality Gate
- Coverage on new code ≥ 80%
- Duplication on new code ≤ 3%
- Reliability/Security/Maintainability rating = A
- Security hotspots 100% reviewed

## Logging Summary
- SLF4J facade, parameterized logging
- Levels: ERROR (failures), WARN (potential issues), INFO (business events), DEBUG (diagnostics)
- Include traceId/requestId via MDC
- Never log passwords, tokens, secrets
- Mask PII (email, phone, ID)
- JSON structured logging in production

## WebSocket Summary
- **Protocol**: RFC 6455, version 13, WSS in production, sub-protocol negotiation
- **Security**: Origin validation, handshake auth, connection limits, message size/frequency limits
- **Heartbeat**: Ping/Pong frames, 60s server idle, 30s client interval, 3 misses → reconnect
- **Reconnect**: Exponential backoff (1s→2s→4s→...→30s max), resume with last seq_id
- **Messages**: Unique msg_id for idempotency, type field, server timestamp, ≤4KB text
- **Performance**: Netty pooled allocator, async business logic, per-session serialization, permessage-deflate
- **Errors**: Structured error response (code/message/ref_msg_id), business errors don't close connection
- **Testing**: 100K concurrent connections/node, 100K msg/s, P99 latency ≤100ms

## References
- Google Java Style Guide
- Alibaba Java Development Manual
- OWASP Top 10: https://owasp.org/www-project-top-ten/
- SonarQube Rules: https://rules.sonarsource.com/java
- Java Concurrency in Practice by Brian Goetz
- Effective Java by Joshua Bloch
