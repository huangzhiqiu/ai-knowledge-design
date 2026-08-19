# WebSocket API Design Guidelines

> Best practices for designing WebSocket APIs in CBOL Messaging Hub. Covers connection management, message framing, protocol design, heartbeat, reconnection, and security.

## WebSocket vs REST

| Use Case | Protocol | Why |
|----------|----------|-----|
| Real-time message delivery | WebSocket | Server push, low latency |
| Presence/typing indicators | WebSocket | Frequent small updates |
| Conversation state changes | WebSocket | Real-time notifications |
| CRUD operations | REST | Standard HTTP semantics |
| Large file upload | REST/HTTP | WebSocket not ideal for large payloads |
| Batch operations | REST | HTTP is better for bulk |

## Connection Lifecycle

```
Client                          Server
  │                               │
  │── HTTP Upgrade Request ─────►│  (with auth token)
  │                               │
  │◄── 101 Switching Protocols ──│  (connection established)
  │                               │
  │── Authenticate Message ──────►│
  │◄── Auth Success/Fail ────────│
  │                               │
  │◄── Welcome (session info) ───│
  │                               │
  │◄── Heartbeat (ping) ─────────│  (every 30s)
  │── Pong ──────────────────────►│
  │                               │
  │── Send Message ──────────────►│
  │◄── Message ACK ──────────────│
  │◄── Message Delivered ────────│  (to recipient)
  │                               │
  │── Close Frame (1000) ───────►│  (normal close)
  │◄── Close Frame (1000) ───────│
```

## Message Protocol Design

### Envelope Format

```json
// ✅ Good - consistent message envelope
{
  "type": "message.sent",
  "id": "msg-abc123",
  "timestamp": "2026-08-19T10:30:00Z",
  "payload": {
    "messageId": 12345,
    "conversationId": 678,
    "senderId": 999,
    "content": "Hello!",
    "contentType": "TEXT",
    "createdAt": "2026-08-19T10:30:00Z"
  }
}
```

### Message Type Naming

```
✅ Good - domain.action format
  message.sent
  message.delivered
  message.read
  conversation.created
  conversation.closed
  conversation.transferred
  agent.connected
  typing.started
  typing.stopped
  presence.online
  presence.offline
  error.authentication_failed
  error.rate_limited

❌ Bad - inconsistent naming
  sendMessage
  new_msg
  MessageCreated
  conv_close
  typing
```

### Message Categories

| Category | Type Prefix | Direction | Description |
|----------|-------------|-----------|-------------|
| Client → Server | `*.request` or action | Client → Server | Client sends command/data |
| Server → Client | `*.event` or past tense | Server → Client | Server notifies client of change |
| ACK | `*.ack` | Server → Client | Acknowledges client message |
| Error | `error.*` | Both | Error notification |

## Connection Management

### Connection Establishment

```java
// ✅ Good - WebSocket handler with auth
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final WebSocketSessionManager sessionManager;
    private final MessageDispatcher messageDispatcher;
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
        messageDispatcher.dispatch(session, message);
    }

    private void handleAuthentication(WebSocketSession session, TextMessage message) {
        try {
            AuthMessage auth = parseAuthMessage(message);
            Jwt jwt = jwtDecoder.decode(auth.getToken());
            Long userId = jwt.getClaim("userId", Long.class);

            session.getAttributes().put("userId", userId);
            session.getAttributes().put("authenticated", true);

            // Register session
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

### Session Management

```java
// ✅ Good - session manager with multi-device support
@Component
public class WebSocketSessionManager {
    // userId -> deviceType -> session
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
}
```

## Heartbeat & Keep-Alive

```java
// ✅ Good - heartbeat mechanism
@Component
@RequiredArgsConstructor
public class WebSocketHeartbeat {
    private final WebSocketSessionManager sessionManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration SESSION_TIMEOUT = Duration.ofSeconds(90);  // 3x heartbeat

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeats, 30, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupStaleSessions, 60, 60, TimeUnit.SECONDS);
    }

    private void sendHeartbeats() {
        sessionManager.getAllSessions().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new PingMessage());
                }
            } catch (IOException e) {
                // Session will be cleaned up
            }
        });
    }

    private void cleanupStaleSessions() {
        Instant cutoff = Instant.now().minus(SESSION_TIMEOUT);
        sessionManager.getAllSessions().forEach(session -> {
            Instant lastActivity = (Instant) session.getAttributes().get("lastActivity");
            if (lastActivity != null && lastActivity.isBefore(cutoff)) {
                try {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException ignored) {}
            }
        });
    }
}
```

## Reconnection Strategy

```javascript
// ✅ Good - client reconnection with exponential backoff
class ChatWebSocketClient {
    constructor(url) {
        this.url = url;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.baseDelay = 1000;  // 1 second
        this.maxDelay = 30000;  // 30 seconds
    }

    connect() {
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
            this.reconnectAttempts = 0;
            this.authenticate();
        };

        this.ws.onclose = (event) => {
            if (event.code === 1000) return;  // Normal close, don't reconnect
            this.scheduleReconnect();
        };

        this.ws.onerror = () => {
            // onclose will be called after onerror
        };
    }

    scheduleReconnect() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('Max reconnection attempts reached');
            return;
        }

        // Exponential backoff with jitter
        const delay = Math.min(
            this.baseDelay * Math.pow(2, this.reconnectAttempts),
            this.maxDelay
        );
        const jitter = delay * 0.3 * Math.random();
        const totalDelay = delay + jitter;

        this.reconnectAttempts++;
        setTimeout(() => this.connect(), totalDelay);
    }

    // After reconnection, sync missed messages
    authenticate() {
        // Send auth + last message ID for catch-up
        this.ws.send(JSON.stringify({
            type: 'auth',
            token: this.token,
            lastMessageId: this.lastMessageId  // For catch-up
        }));
    }
}
```

## Message Reliability

### ACK Pattern

```
Client sends message → Server stores → Server sends ACK → Client marks as sent
                                              ↓
                              Server delivers to recipient → Recipient sends delivered event
```

```java
// ✅ Good - message ACK with retry
public class MessageSender {
    private final Map<String, PendingMessage> pendingMessages = new ConcurrentHashMap<>();
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRIES = 3;

    public void send(WebSocketSession session, OutgoingMessage message) {
        String messageId = message.getId();
        pendingMessages.put(messageId, new PendingMessage(message, 0, Instant.now()));

        try {
            session.sendMessage(new TextMessage(toJson(message)));
        } catch (IOException e) {
            scheduleRetry(messageId);
        }
    }

    public void onAck(String messageId) {
        pendingMessages.remove(messageId);
    }

    @Scheduled(fixedDelay = 1000)
    public void checkTimeouts() {
        Instant now = Instant.now();
        pendingMessages.forEach((id, pending) -> {
            if (now.isAfter(pending.getSentAt().plus(ACK_TIMEOUT))) {
                if (pending.getRetryCount() < MAX_RETRIES) {
                    retry(id);
                } else {
                    // Give up, notify user
                    pendingMessages.remove(id);
                }
            }
        });
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| No authentication on connect | Anyone can connect | Auth on first message or during handshake |
| No heartbeat | Dead connections accumulate | Ping/pong every 30s, timeout at 90s |
| No reconnection | Users lose connection permanently | Exponential backoff reconnection on client |
| No message ACK | Messages can be lost without detection | ACK pattern with retry |
| Sending large payloads | Performance issues, memory | Limit message size, use HTTP for large files |
| No rate limiting | One client can flood server | Rate limit per user/connection |
| Sharing session across users | Security issue | One session per user+device, validate on every message |
| No close reason | Hard to debug disconnections | Use proper close codes (1000, 1008, 1011, etc.) |
| Blocking operations in handler | Blocks event loop | Offload blocking ops to thread pool |
| No message ordering | Messages arrive out of order | Use sequence numbers, client reorders |
| Storing session in memory only | Lost on restart | Acceptable for WebSocket (reconnect), but notify clients |
| No catch-up on reconnect | Missed messages lost | Send lastMessageId on reconnect, server delivers missed messages |

## References

- WebSocket Protocol (RFC 6455): https://datatracker.ietf.org/doc/html/rfc6455
- Spring WebSocket: https://docs.spring.io/spring-framework/reference/web/websocket.html
- Netty WebSocket: https://netty.io/wiki/
- STOMP Protocol: https://stomp.github.io/
- Socket.IO: https://socket.io/docs/v4/
- WebSocket Security: https://book.hacktricks.xyz/network-services-pentesting/pentesting-web/websocket
