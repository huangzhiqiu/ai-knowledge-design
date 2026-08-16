# Security Coding Guidelines

> Secure coding standards based on OWASP Top 10, CWE, and industry best practices.

## OWASP Top 10 (2021)

| # | Risk | Description |
|---|------|-------------|
| A01 | Broken Access Control | Restrictions not properly enforced |
| A02 | Cryptographic Failures | Inadequate encryption/protection of data |
| A03 | Injection | SQL, NoSQL, OS, LDAP injection |
| A04 | Insecure Design | Missing security in design phase |
| A05 | Security Misconfiguration | Default configs, unnecessary features |
| A06 | Vulnerable Components | Outdated libraries with known CVEs |
| A07 | Identification & Auth Failures | Weak auth, session management |
| A08 | Software & Data Integrity Failures | Untrusted data in CI/CD, insecure deserialization |
| A09 | Security Logging Failures | Insufficient logging and monitoring |
| A10 | Server-Side Request Forgery | SSRF attacks |

## Input Validation

### [Mandatory] Validate all external input
- Never trust client input
- Validate at every trust boundary
- Use whitelist (allow list), not blacklist

### [Mandatory] Use parameterized queries (SQL Injection)
```java
// Good - PreparedStatement
String sql = "SELECT * FROM users WHERE id = ?";
PreparedStatement stmt = conn.prepareStatement(sql);
stmt.setString(1, userId);
ResultSet rs = stmt.executeQuery();

// Bad - string concatenation (SQL injection)
String sql = "SELECT * FROM users WHERE id = '" + userId + "'";
Statement stmt = conn.createStatement();
```

### [Mandatory] NoSQL Injection
- Use parameterized queries for MongoDB/Redis
- Never pass user input directly to query operators
```java
// Good
Document query = new Document("username", username);

// Bad - operator injection
Document query = new Document(username, password); // if username = "$gt"
```

### Input Validation Checklist
- [ ] Type checking (string, number, date)
- [ ] Length limits (prevent overflow, DoS)
- [ ] Format validation (email, phone, UUID)
- [ ] Range validation (min/max values)
- [ ] Enum validation (only allowed values)
- [ ] File type validation (magic bytes, not just extension)
- [ ] File size limits

## Authentication & Authorization

### Authentication
- Use standard libraries (Spring Security, OAuth2), don't roll your own
- Password hashing: bcrypt/argon2 (never MD5/SHA1)
- Rate limit login attempts (prevent brute force)
- MFA for sensitive operations
- Session: use secure, httpOnly, sameSite cookies

### Authorization
- Enforce on server side (never trust client-side checks)
- Principle of Least Privilege
- Check ownership: user can only access their own data
- IDOR prevention: verify user owns the resource
```java
// Good - verify ownership
Message message = messageService.getById(messageId);
if (!message.getSenderId().equals(currentUserId)) {
    throw new PermissionDeniedException();
}

// Bad - no ownership check
Message message = messageService.getById(messageId);
```

### JWT Security
- Use short expiration (15-30 min) + refresh token
- Validate signature, issuer, audience, expiration
- Never put sensitive data in payload (base64, not encrypted)
- Store securely (httpOnly cookie, not localStorage for XSS)

## Cryptography

### [Mandatory] Use strong algorithms
| Purpose | Algorithm | Avoid |
|---------|-----------|-------|
| Hashing | SHA-256, SHA-3 | MD5, SHA-1 |
| Password | bcrypt, argon2, PBKDF2 | MD5, SHA-1, plain SHA-256 |
| Symmetric | AES-256-GCM | DES, 3DES, ECB mode |
| Asymmetric | RSA-2048+, ECDSA | RSA-1024, DSA |
| TLS | TLS 1.2+ (prefer 1.3) | SSL, TLS 1.0/1.1 |
| Random | SecureRandom | Random, Math.random() |

### [Mandatory] Secure random for security contexts
```java
// Good
SecureRandom random = new SecureRandom();
byte[] key = new byte[32];
random.nextBytes(key);

// Bad - predictable
Random random = new Random();
int token = random.nextInt();
```

### [Mandatory] Don't hardcode secrets
- Use environment variables, config center, or secret manager
- Never commit secrets to Git
- Use `.gitignore` for config files with secrets

## XSS Prevention

### Context-aware escaping
- HTML context: escape `<`, `>`, `&`, `"`, `'`
- JavaScript context: escape non-alphanumeric
- URL context: encode URI components
- CSS context: escape non-alphanumeric

### [Mandatory] Use templating engine auto-escaping
- Thymeleaf, React, Vue auto-escape by default
- Never use `v-html`, `dangerouslySetInnerHTML` with user input
- Set Content-Security-Policy header

## CSRF Prevention
- Use SameSite=Strict/Lax cookies
- CSRF token for state-changing requests
- Verify Origin/Referer headers
- REST APIs with token auth (not cookie) are immune

## Insecure Deserialization
- Never deserialize untrusted data
- Use safe formats (JSON with type checking)
- Avoid Java native serialization (`ObjectInputStream`)
- If must deserialize: validate class whitelist, use look-ahead deserialization

## SSRF Prevention
- Validate URL scheme (only http/https)
- Block private IP ranges (10.x, 192.168.x, 127.x, 169.254.x)
- Use allowlist of allowed domains
- Don't follow redirects blindly

## Dependency Security
- Regular dependency scanning (OWASP Dependency-Check, Snyk, Dependabot)
- Update dependencies with known CVEs promptly
- Use minimal dependencies (avoid unused libraries)
- Pin versions, avoid version ranges in production

## Logging Security
- [Mandatory] Never log passwords, tokens, credit cards
- [Mandatory] Mask sensitive data (PII): `user@***.com`
- Log security events: login, access denied, validation failures
- Include request ID / correlation ID for tracing
- Don't log full request bodies (may contain secrets)

## Secure Headers
```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'
Strict-Transport-Security: max-age=31536000; includeSubDomains
Referrer-Policy: strict-origin-when-cross-origin
```

## Security Checklist

- [ ] All input validated and sanitized
- [ ] Parameterized queries (no SQL injection)
- [ ] Authentication on all protected endpoints
- [ ] Authorization checks (ownership, roles)
- [ ] Passwords hashed (bcrypt/argon2)
- [ ] Secrets not in code/config committed
- [ ] HTTPS enforced (TLS 1.2+)
- [ ] Security headers set
- [ ] Dependencies scanned for vulnerabilities
- [ ] Sensitive data masked in logs
- [ ] Rate limiting on auth endpoints
- [ ] File upload validated (type, size, storage)
- [ ] Error messages don't leak internals (stack traces)

## References
- OWASP Top 10: https://owasp.org/www-project-top-ten/
- OWASP Cheat Sheet Series
- CWE Top 25: https://cwe.mitre.org/top25/
- Secure Coding Guidelines for Java SE (Oracle)
