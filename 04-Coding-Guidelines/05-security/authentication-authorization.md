# Authentication & Authorization Guidelines

> Best practices for authentication, authorization, and access control in CBOL messaging system.

## Authentication

### JWT (JSON Web Token)

```java
// ✅ Good - JWT configuration
@Configuration
public class JwtConfig {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:3600000}")  // 1 hour
    private long expiration;

    @Value("${jwt.refresh-expiration:604800000}")  // 7 days
    private long refreshExpiration;

    @Bean
    public JwtDecoder jwtDecoder() {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}

// ✅ Good - token generation with proper claims
public String generateAccessToken(User user) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("cbol")
        .subject(String.valueOf(user.getId()))
        .issuedAt(now)
        .expiresAt(now.plusMillis(expiration))
        .claim("type", "access")
        .claim("userId", user.getId())
        .claim("roles", user.getRoles())
        .claim("deviceType", user.getDeviceType())
        .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
}

// ❌ Bad - no expiration, no issuer, sensitive data in token
public String generateBadToken(User user) {
    return Jwts.builder()
        .setSubject(user.getUsername())
        .claim("password", user.getPassword())  // sensitive data in token!
        .claim("email", user.getEmail())
        // no expiration = token never expires!
        .signWith(SignatureAlgorithm.HS256, secret)
        .compact();
}
```

### Token Validation

```java
// ✅ Good - JWT validation filter
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtDecoder jwtDecoder;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            try {
                Jwt jwt = jwtDecoder.decode(token);

                // Validate claims
                if (!"access".equals(jwt.getClaimAsString("type"))) {
                    throw new InvalidTokenException("Not an access token");
                }

                // Create authentication
                Long userId = jwt.getClaim("userId", Long.class);
                List<String> roles = jwt.getClaim("roles", List.class);
                Authentication auth = new UsernamePasswordAuthenticationToken(
                    userId, null, roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList())
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

### Refresh Token Pattern

```java
// ✅ Good - refresh token with rotation
@PostMapping("/auth/refresh")
public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
    // 1. Validate refresh token
    User user = validateRefreshToken(request.getRefreshToken());

    // 2. Invalidate old refresh token (rotation)
    refreshTokenService.invalidate(request.getRefreshToken());

    // 3. Generate new token pair
    String accessToken = generateAccessToken(user);
    String newRefreshToken = generateRefreshToken(user);

    // 4. Save new refresh token (hashed)
    refreshTokenService.save(user.getId(), hash(newRefreshToken));

    return ResponseEntity.ok(new TokenResponse(accessToken, newRefreshToken));
}

// ✅ Good - store refresh tokens hashed (not plaintext)
public void save(Long userId, String hashedToken) {
    refreshTokenRepository.save(RefreshToken.builder()
        .userId(userId)
        .tokenHash(hashedToken)  // store hash, not plaintext
        .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
        .build());
}

// ❌ Bad - store refresh tokens in plaintext, no rotation
public String refresh(String oldToken) {
    // old token still valid after refresh = token theft can persist
    return generateNewToken(oldToken);
}
```

## Authorization

### Role-Based Access Control (RBAC)

```java
// ✅ Good - method-level authorization
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<ConversationDTO> listConversations() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return conversationService.listByUser(userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @conversationSecurity.isOwner(authentication, #id)")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id) {
        conversationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

// ✅ Good - custom security expression
@Component("conversationSecurity")
public class ConversationSecurity {
    private final ConversationRepository conversationRepository;

    public boolean isOwner(Authentication auth, Long conversationId) {
        Long userId = (Long) auth.getPrincipal();
        return conversationRepository.findById(conversationId)
            .map(c -> c.getOwnerId().equals(userId))
            .orElse(false);
    }
}
```

### Attribute-Based Access Control (ABAC)

```java
// ✅ Good - ABAC for fine-grained control
@Service
public class MessageAuthorizationService {

    public boolean canReadMessage(Authentication auth, Long messageId) {
        Long userId = (Long) auth.getPrincipal();
        Message message = messageRepository.findById(messageId).orElseThrow();

        // User can read if:
        // 1. They are the sender
        if (message.getSenderId().equals(userId)) return true;

        // 2. They are the recipient
        if (message.getRecipientId().equals(userId)) return true;

        // 3. They are a member of the conversation
        if (conversationMemberRepository.existsByConversationIdAndUserId(
                message.getConversationId(), userId)) return true;

        return false;
    }

    public boolean canDeleteMessage(Authentication auth, Long messageId) {
        Long userId = (Long) auth.getPrincipal();
        Message message = messageRepository.findById(messageId).orElseThrow();

        // Only sender can delete within 5 minutes
        if (!message.getSenderId().equals(userId)) return false;
        return message.getCreatedAt().plusMinutes(5).isAfter(Instant.now());
    }
}
```

## WebSocket Security

```java
// ✅ Good - WebSocket authentication
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
            .simpDestMatchers("/app/**").authenticated()
            .simpSubscribeDestMatchers("/user/**").authenticated()
            .simpSubscribeDestMatchers("/topic/conversation/*").authenticated()
            .anyMessage().denyAll();
        return messages.build();
    }
}

// ✅ Good - JWT in WebSocket handshake
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtDecoder jwtDecoder;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // Extract token from query param or cookie
        String token = extractToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            attributes.put("userId", jwt.getClaim("userId", Long.class));
            attributes.put("roles", jwt.getClaim("roles", List.class));
            return true;
        } catch (JwtException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    private String extractToken(ServerHttpRequest request) {
        // From query param: ws://host/ws?token=xxx
        String token = request.getURI().getQuery();
        if (token != null && token.startsWith("token=")) {
            return token.substring(6);
        }
        // From cookie
        return request.getCookies().getFirst("access_token")?.getValue();
    }
}

// ✅ Good - per-message authorization (validate user can access conversation)
@Component
public class MessageAuthorizationHandler {
    private final MessageAuthorizationService authService;

    public void handleMessage(WebSocketSession session, Message message) {
        Long userId = (Long) session.getAttributes().get("userId");
        Authentication auth = new UsernamePasswordAuthenticationToken(userId, null,
            session.getAttributes().get("roles"));

        if (!authService.canSendMessage(auth, message.getConversationId())) {
            throw new AccessDeniedException("Cannot send message to this conversation");
        }
        // process message
    }
}
```

## API Security

### Input Validation

```java
// ✅ Good - Bean Validation on request DTOs
public class SendMessageRequest {
    @NotNull(message = "conversationId is required")
    private Long conversationId;

    @NotBlank(message = "content is required")
    @Size(max = 5000, message = "content must be at most 5000 characters")
    @XssProtected  // custom annotation for XSS prevention
    private String content;

    @Pattern(regexp = "TEXT|IMAGE|FILE|SYSTEM", message = "invalid contentType")
    private String contentType = "TEXT";
}

// ✅ Good - custom XSS validator
@Constraint(validatedBy = XssProtectedValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface XssProtected {
    String message() default "Input contains potentially malicious content";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class XssProtectedValidator implements ConstraintValidator<XssProtected, String> {
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "<script[^>]*>.*?</script>|<[^>]+on\\w+\\s*=|javascript:|eval\\(",
        Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return !XSS_PATTERN.matcher(value).find();
    }
}
```

### Rate Limiting

```java
// ✅ Good - rate limiting on sensitive endpoints
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @PostMapping("/login")
    @RateLimit(key = "login", limit = 5, window = 60)  // 5 attempts per minute per IP
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        // ...
    }

    @PostMapping("/register")
    @RateLimit(key = "register", limit = 3, window = 3600)  // 3 registrations per hour per IP
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        // ...
    }
}
```

### CORS Configuration

```java
// ✅ Good - strict CORS configuration
@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "https://app.cbol.com",
            "https://admin.cbol.com"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
        config.setExposedHeaders(List.of("X-Request-ID", "X-RateLimit-Remaining"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);  // 1 hour preflight cache

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}

// ❌ Bad - permissive CORS
config.setAllowedOrigins(List.of("*"));  // all origins!
config.setAllowCredentials(true);          // with credentials = security risk
```

## Security Headers

```java
// ✅ Good - security headers
@Configuration
public class SecurityHeadersConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: https:; " +
                    "connect-src 'self' wss://*.cbol.com; " +
                    "frame-ancestors 'none'"
                ))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .contentTypeOptions(HeadersConfigurer.ContentTypeOptionsConfig::disable)
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            );
        return http.build();
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| JWT without expiration | Token never expires, stolen token persists | Always set `exp` claim (1h access, 7d refresh) |
| Storing passwords in plaintext | Data breach = all passwords exposed | BCrypt/Argon2 hashing (cost factor >= 12) |
| `SELECT * FROM users WHERE username='...' AND password='...'` | SQL injection | Parameterized queries / ORM |
| CORS `*` with credentials | Any site can make authenticated requests | Whitelist specific origins |
| No rate limiting on login | Brute force attacks | Rate limit + account lockout + CAPTCHA |
| Sensitive data in JWT | Token can be decoded (not encrypted) | Only store non-sensitive claims (userId, roles) |
| Refresh tokens without rotation | Stolen refresh token persists forever | Rotate on use, store hashed, invalidate old |
| No input validation | XSS, injection, overflow | Bean Validation + custom validators |
| `hasRole('ADMIN')` only (no ownership check) | Any admin can access any user's data | Combine RBAC + ABAC (ownership check) |
| WebSocket without per-message auth | Authenticated user can access any conversation | Validate authorization per message/action |
| Hardcoded secrets in code | Source code leak = secret compromise | Environment variables / secret manager |
| No security headers | Clickjacking, MIME sniffing, XSS | CSP, HSTS, X-Frame-Options, X-Content-Type-Options |
| `System.out.println` for sensitive data | Sensitive data in logs | Use structured logging, mask PII |

## References

- OWASP Top 10: https://owasp.org/www-project-top-ten/
- OWASP ASVS: https://owasp.org/www-project-application-security-verification-standard/
- Spring Security: https://docs.spring.io/spring-security/reference/
- JWT Best Practices: https://datatracker.ietf.org/doc/html/rfc8725
- NIST Password Guidelines: https://pages.nist.gov/800-63-3/sp800-63b.html
- CSP Evaluator: https://csp-evaluator.withgoogle.com/
