# Authentication & Authorization Design Guidelines

> Best practices for designing authentication and authorization systems in CBOL Messaging Hub. Covers authentication flows, token management, RBAC/ABAC, session management, and access control architecture.

## Authentication Architecture

### Authentication Flow

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Client  │────►│  API Gateway │────►│  Auth Service│────►│  User Store  │
│          │     │  (validate    │     │  (issue/     │     │  (MySQL)     │
│          │◄────│   JWT token)  │◄────│   validate   │◄────│              │
└──────────┘     └──────┬───────┘     │   tokens)    │     └──────────────┘
                         │               └──────────────┘
                         ▼
                  ┌──────────────┐
                  │ Application  │
                  │ Services     │
                  │ (trust token │
                  │  from GW)    │
                  └──────────────┘
```

### Authentication Methods

| Method | Use For | Security Level |
|--------|---------|---------------|
| JWT Bearer Token | API authentication, mobile/web | Medium-High |
| OAuth 2.0 + OIDC | Third-party login, SSO | High |
| API Key | Service-to-service, server-side | Medium |
| mTLS | Service-to-service in zero-trust | Very High |
| Session Cookie | Web browser sessions | Medium |

### JWT Authentication Flow

```
1. Client sends credentials (username/password) → Auth Service
2. Auth Service validates credentials against User Store
3. Auth Service issues:
   - Access token (short-lived: 15 min)
   - Refresh token (long-lived: 7 days)
4. Client sends access token in Authorization: Bearer header
5. API Gateway validates token signature + expiration
6. Services trust token (no DB call needed)
7. On expiration, client uses refresh token to get new access token
```

```java
// ✅ Good - JWT token generation
@Service
@RequiredArgsConstructor
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    public TokenPair generateTokenPair(User user) {
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);
        return new TokenPair(accessToken, refreshToken);
    }

    private String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("cbol")
            .subject(String.valueOf(user.getId()))
            .issuedAt(now)
            .expiresAt(now.plus(ACCESS_TOKEN_TTL))
            .claim("type", "access")
            .claim("userId", user.getId())
            .claim("roles", user.getRoles())
            .claim("deviceType", user.getDeviceType())
            .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String generateRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        // Store hashed refresh token (not plaintext)
        refreshTokenRepository.save(RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(hashToken(token))
            .expiresAt(Instant.now().plus(REFRESH_TOKEN_TTL))
            .build());
        return token;
    }

    public TokenPair refreshToken(String refreshToken) {
        // 1. Validate refresh token
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
            .orElseThrow(() -> new InvalidRefreshTokenException());

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new RefreshTokenExpiredException();
        }

        // 2. Rotate refresh token (invalidate old, issue new)
        refreshTokenRepository.delete(stored);
        User user = userRepository.findById(stored.getUserId()).orElseThrow();
        return generateTokenPair(user);
    }
}
```

### Token Validation

```java
// ✅ Good - JWT validation filter at API Gateway
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

                // Validate token type
                if (!"access".equals(jwt.getClaimAsString("type"))) {
                    throw new InvalidTokenException("Not an access token");
                }

                // Validate expiration (jwtDecoder does this automatically)
                // Set authentication context
                Long userId = jwt.getClaim("userId", Long.class);
                List<String> roles = jwt.getClaim("roles", List.class);
                Authentication auth = new UsernamePasswordAuthenticationToken(
                    userId, null,
                    roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).collect(Collectors.toList())
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

    // RBAC: admin or owner can delete
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @messageSecurity.isOwner(authentication, #id)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        messageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

// ABAC: custom security expression
@Component("messageSecurity")
public class MessageSecurity {
    private final MessageRepository messageRepository;

    public boolean isOwner(Authentication auth, Long messageId) {
        Long userId = (Long) auth.getPrincipal();
        return messageRepository.findById(messageId)
            .map(m -> m.getSenderId().equals(userId))
            .orElse(false);
    }
}
```

### Permission Matrix

| Resource | USER | AGENT | ADMIN | SYSTEM |
|----------|------|-------|-------|--------|
| Read own conversations | ✅ | ✅ | ✅ | ✅ |
| Read all conversations | ❌ | ✅ (assigned) | ✅ | ✅ |
| Send message | ✅ | ✅ | ✅ | ✅ |
| Delete own message | ✅ (5min) | ✅ (5min) | ✅ | ✅ |
| Delete any message | ❌ | ❌ | ✅ | ✅ |
| Transfer conversation | ❌ | ✅ | ✅ | ✅ |
| Manage users | ❌ | ❌ | ✅ | ✅ |
| View analytics | ❌ | ✅ | ✅ | ✅ |
| System config | ❌ | ❌ | ❌ | ✅ |

## Session Management

### WebSocket Session

```java
// ✅ Good - WebSocket session with authentication
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final WebSocketSessionManager sessionManager;
    private final JwtDecoder jwtDecoder;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Connection established, but not authenticated yet
        session.getAttributes().put("authenticated", false);
        session.getAttributes().put("connectedAt", Instant.now());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // First message must be authentication
        if (!(Boolean) session.getAttributes().get("authenticated")) {
            handleAuthentication(session, message);
            return;
        }
        // Dispatch authenticated message
    }

    private void handleAuthentication(WebSocketSession session, TextMessage message) {
        try {
            AuthMessage auth = parseAuthMessage(message);
            Jwt jwt = jwtDecoder.decode(auth.getToken());
            Long userId = jwt.getClaim("userId", Long.class);

            session.getAttributes().put("userId", userId);
            session.getAttributes().put("authenticated", true);
            session.getAttributes().put("deviceType", jwt.getClaim("deviceType", String.class));

            // Register session (multi-device support)
            sessionManager.register(userId, session);

            // Send welcome
            sendMessage(session, new WelcomeMessage(userId, session.getId()));
        } catch (Exception e) {
            sendMessage(session, new ErrorMessage("authentication_failed", "Invalid token"));
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
    }
}
```

### Multi-Device Session Management

```java
// ✅ Good - multi-device session management
@Component
public class WebSocketSessionManager {
    // userId → deviceType → session
    private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        String deviceType = (String) session.getAttributes().getOrDefault("deviceType", "web");
        sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(deviceType, session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        Map<String, WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.values().removeIf(s -> s.getId().equals(session.getId()));
            if (userSessions.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    public List<WebSocketSession> getSessions(Long userId) {
        Map<String, WebSocketSession> userSessions = sessions.get(userId);
        return userSessions != null ? new ArrayList<>(userSessions.values()) : List.of();
    }

    public void sendToUser(Long userId, Object message) {
        getSessions(userId).forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(toJson(message)));
                }
            } catch (IOException e) {
                // Log and continue
            }
        });
    }

    public void sendToAllDevicesExcept(Long userId, String excludeDevice, Object message) {
        getSessions(userId).stream()
            .filter(s -> !excludeDevice.equals(s.getAttributes().get("deviceType")))
            .forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(toJson(message)));
                    }
                } catch (IOException e) { }
            });
    }
}
```

## API Key Authentication (Service-to-Service)

```java
// ✅ Good - API key authentication for service-to-service
@Configuration
public class ApiKeyAuthConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/internal/**").hasAuthority("SERVICE")  // Internal APIs require API key
                .anyRequest().authenticated()
            )
            .addFilterBefore(new ApiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-Key";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && isValidApiKey(apiKey)) {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                "service", null, List.of(new SimpleGrantedAuthority("SERVICE")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }

    private boolean isValidApiKey(String apiKey) {
        // Validate against stored hashed API keys
        // Use constant-time comparison to prevent timing attacks
        return apiKeyRepository.findByKeyHash(hashKey(apiKey)).isPresent();
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Long-lived access tokens | Stolen token persists for long time | Short access token (15min) + refresh token rotation |
| Storing tokens in localStorage | XSS can steal token | Use httpOnly cookies, or short-lived tokens in memory |
| No token expiration | Token never expires | Always set exp claim |
| Storing refresh tokens in plaintext | DB leak = all tokens compromised | Store hashed refresh tokens |
| No refresh token rotation | Stolen refresh token persists forever | Rotate on use, invalidate old |
| RBAC only | Can't express fine-grained access (owner, time) | RBAC + ABAC hybrid |
| Hardcoding roles in code | Can't change roles without redeploy | Role-permission mapping in DB/config |
| No least privilege | Service accounts have admin access | Minimal permissions per service |
| Session in memory only | Lost on restart, can't scale | Acceptable for WebSocket (reconnect), but use sticky sessions |
| No multi-device support | User logged out on new login | Multi-device session management (userId → deviceType → session) |
| No API key for internal APIs | Internal APIs accessible from anywhere | API key authentication for service-to-service |
| Constant-time comparison not used | Timing attack on API key/token validation | Use MessageDigest.isEqual() or similar |
| No audit logging for auth events | Can't investigate security incidents | Log login/logout/token refresh/permission denied |
| No rate limiting on login | Brute force attacks | Rate limit + account lockout + CAPTCHA |
| JWT with sensitive data | Token can be decoded (not encrypted) | Only store non-sensitive claims (userId, roles) |

## References

- OAuth 2.0: https://oauth.net/2/
- OpenID Connect: https://openid.net/connect/
- JWT (RFC 7519): https://datatracker.ietf.org/doc/html/rfc7519
- JWT Best Practices (RFC 8725): https://datatracker.ietf.org/doc/html/rfc8725
- Spring Security: https://docs.spring.io/spring-security/reference/
- RBAC: https://csrc.nist.gov/projects/role-based-access-control
- ABAC: https://csrc.nist.gov/projects/attribute-based-access-control
- Zero Trust: https://csrc.nist.gov/publications/detail/sp/800-207/final
- OWASP Authentication Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- OWASP Session Management: https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
