# Connection Management

> WebSocket connection lifecycle, reconnection, and degradation strategies.

## Connection State Machine

```
                    ┌──────────┐
                    │  CLOSED  │
                    └────┬─────┘
                         │ connect()
                         v
                    ┌──────────┐
              ┌────>│CONNECTING│
              │     └────┬─────┘
              │          │ handshake success
              │          v
              │     ┌──────────┐
              │     │  OPEN    │<──┐
              │     └────┬─────┘   │
              │          │         │ heartbeat ok
              │          │ idle timeout
              │          v         │
              │     ┌──────────┐   │
              │     │  IDLE    │───┘
              │     └────┬─────┘
              │          │ activity
              │          v
              │     ┌──────────┐
              │     │ CLOSING  │
              │     └────┬─────┘
              │          │ close frame
              │          v
              │     ┌──────────┐
              └─────│  CLOSED  │
                    └──────────┘
```

## Connection Lifecycle

### 1. Connect
```
1. Client obtains auth token
2. Client initiates WebSocket handshake
3. Server validates token during handshake
4. Server registers session in SessionRegistry
5. Server sends "connected" ack with server time
6. Client sends sync request (last_delivered_seq)
7. Server pushes missed messages (catch-up)
8. Connection enters OPEN state, real-time mode
```

### 2. Heartbeat
```
Client -> Server: Ping (every 30s)
Server -> Client: Pong (immediate)

OR (application-level):
Client -> Server: {"type":"ping","ts":1718438400}
Server -> Client: {"type":"pong","ts":1718438400,"server_ts":1718438401}
```

- If no ping received for 90s (3 missed) -> server closes connection
- If no pong received for 30s -> client initiates reconnect

### 3. Disconnect
**Graceful:**
- Client sends close frame (1000)
- Server unregisters session
- Server responds with close frame
- Connection closed

**Ungraceful (network drop):**
- TCP connection drops without close frame
- Server detects via heartbeat timeout (90s)
- Or TCP keepalive detects dead connection
- Session unregistered after timeout

### 4. Reconnect
```
1. Client detects connection lost (no pong / socket error)
2. Client waits with backoff: 1s, 2s, 4s, 8s, 16s, 30s (cap)
3. Client reconnects with same token
4. Server validates token, creates new session
5. Client sends sync with last_delivered_seq
6. Server pushes missed messages
7. Connection resumes
```

## Reconnection Strategy

### Exponential Backoff with Jitter
```java
int attempt = 0;
int maxDelay = 30000; // 30s cap
Random random = new Random();

while (!connected) {
    try {
        connect();
        attempt = 0; // reset on success
    } catch (Exception e) {
        attempt++;
        int baseDelay = Math.min(1000 * (1 << attempt), maxDelay);
        int jitter = random.nextInt(baseDelay / 2);
        int delay = baseDelay + jitter;
        Thread.sleep(delay);
    }
}
```

### Reconnection Triggers

| Trigger | Action |
|---------|--------|
| No pong for 30s | Close and reconnect |
| Socket error/exception | Immediate reconnect |
| Close code 4001 (auth failed) | Stop reconnecting, prompt login |
| Close code 4003 (kicked) | Stop reconnecting, show message |
| Close code 4004 (maintenance) | Wait 60s, then reconnect |
| App foregrounded | Check connection, reconnect if needed |
| Network change (WiFi->4G) | Force reconnect |

## Connection Degradation

When WebSocket is unavailable, fall back to:

```
WebSocket (primary)
    │ fail
    v
Long Polling (fallback 1)
    │ fail
    v
Short Polling (fallback 2)
```

### Long Polling
- Client sends GET request, server holds until data or timeout (30s)
- Server responds with data or empty
- Client immediately sends next request
- Works through most firewalls/proxies

### Short Polling
- Client sends GET every N seconds
- Simple but high latency
- Last resort fallback

## Multi-Device Connection Limits

| Plan | Max Devices | Behavior on Exceed |
|------|-------------|-------------------|
| Free | 3 | Kick oldest device |
| Pro | 5 | Kick oldest device |
| Enterprise | 10 | Reject new connection |

**Kick flow:**
1. New device connects, exceeds limit
2. Server finds oldest active session
3. Server sends close frame (4003) to old device
4. Old device shows "kicked by another login"
5. New device connection proceeds

## Connection-Aware Features

### Message Routing
- Only push to devices in OPEN state
- IDLE devices: still receive push (app may be background)
- CLOSED devices: offline queue

### Presence Updates
- On connect: user status -> ONLINE
- On disconnect (graceful): user status -> OFFLINE immediately
- On disconnect (ungraceful): wait heartbeat timeout, then OFFLINE
- On idle: user status -> AWAY (configurable)

### Typing Indicators
- Only sent when connection is OPEN
- Auto-cancel on disconnect
- Timeout after 5s of no typing activity

## Connection Metrics

| Metric | Purpose | Alert Threshold |
|--------|---------|-----------------|
| Active connections | Capacity planning | > 80% capacity |
| Connection rate | Traffic monitoring | Sudden spike |
| Disconnection rate | Stability | > 5%/min |
| Reconnect success rate | UX quality | < 95% |
| Average connection duration | Engagement | < 5min |
| Heartbeat timeout rate | Network quality | > 3% |

## Reference: Socket.IO Reconnection
Socket.IO implements: exponential backoff (1s initial, max 5s), randomization factor, reconnection attempts cap, and forced new connection on `forceNew` option.
