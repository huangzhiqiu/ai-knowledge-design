# Push-Pull Models

## Model Comparison

| Model | Latency | Server Load | Complexity | Offline Support |
|-------|---------|-------------|------------|-----------------|
| Push-only | Low | High | Medium | Poor |
| Pull-only | High | Low | Low | Good (on poll) |
| Push-Pull (hybrid) | Low | Medium | High | Excellent |

## Push Model

### How it works
- Server maintains active connections (WebSocket / TCP)
- On new message: server immediately pushes to connected clients
- Session Registry maps user_id -> active connections

### Components
```
Message In -> Router -> Session Registry -> [Device A conn] -> push
                                      -> [Device B conn] -> push
```

### Pros
- Real-time (sub-100ms)
- Efficient for active users

### Cons
- Connection management complexity
- Offline users need separate mechanism
- Connection stateful (harder to scale horizontally)

## Pull Model

### How it works
- Client polls server at intervals (e.g., every 30s)
- Or client uses long-polling (request hangs until data available)

### Variants
1. **Short polling**: periodic GET requests
2. **Long polling**: request hangs until new data or timeout
3. **Incremental sync**: pull only changes since last cursor

### Pros
- Stateless server (easy horizontal scaling)
- Works through firewalls/proxies
- Simple implementation

### Cons
- Latency (poll interval)
- Wasted requests (empty polls)
- Battery impact on mobile

## Hybrid Push-Pull Model (Recommended)

```
Online state:
  New message -> Server pushes via WebSocket -> Client receives instantly

Reconnect / offline:
  Client connects -> sends cursor -> Server pulls missed messages -> Client catches up

Fallback:
  If WebSocket fails -> Client falls back to long-polling
```

### Turms IM Three Models
Turms (open-source IM engine) explicitly supports:
1. **Push model**: server notifies clients of changes
2. **Pull model**: clients query on demand
3. **Push-pull model**: push notification + pull full data

Used for business data change awareness (messages, friend requests, group updates).

## When to Use Which

| Scenario | Recommended Model |
|----------|-------------------|
| Real-time chat | Push-Pull hybrid |
| Low-activity notification center | Pull (long polling) |
| IoT sensor data | Push |
| Email-like async messaging | Pull |
| Mixed workload | Push-Pull hybrid |

---

## Open Source Project Sync Mechanisms

### Matrix: /sync Incremental Long Polling

Matrix's client sync mechanism is **token-based incremental long polling**, elegantly designed and compatible with all network environments.

**Core flow:**

```
1. Initial sync: GET /_matrix/client/v3/sync (no since param)
   -> returns full state + next_batch token

2. Incremental sync: GET /_matrix/client/v3/sync?since={token}&timeout=30000
   -> server holds connection until new event or timeout (30s)
   -> returns incremental update + new next_batch token

3. Repeat step 2 for "near real-time" sync
```

**/sync response structure:**
```json
{
  "next_batch": "s_abc_123",
  "rooms": {
    "join": {
      "!room:example.com": {
        "timeline": { "events": [...], "limited": false },
        "state": { "events": [...] },
        "ephemeral": { "events": [...] }
      }
    }
  },
  "presence": { "events": [...] }
}
```

**Key design points:**
1. **Token mechanism**: `since` token marks the incremental starting point, server does not need to maintain client session state
2. **Long polling**: `timeout` param makes server hold the connection, return immediately on new event, or return empty on timeout
3. **Categorized response**: timeline (messages), state (room state), ephemeral (typing/receipts) separated
4. **Filter**: client can specify filter param to only receive rooms/event types of interest
5. **Stateless server**: server does not maintain WebSocket connections, easy horizontal scaling

**Why doesn't Matrix use WebSocket?**
- Long polling has better compatibility (penetrates all firewalls/proxies)
- Stateless server, easier horizontal scaling
- In federated architecture, cross-server push is complex, pull model is simpler
- Trade-off: slightly higher latency (TCP/TLS handshake on each reconnect)

### Rocket.Chat: DDP Subscription-Push Model

Rocket.Chat uses Meteor's **DDP (Distributed Data Protocol)** over WebSocket:

**DDP core operations:**
```
Client -> Server: subscribe("stream-room-messages", roomId)
Server -> Client: added / changed / removed (incremental data)
Client -> Server: method("sendMessage", message)
Server -> Client: result (method call result)
```

**DDP vs REST:**

| Dimension | REST + Polling | DDP over WebSocket |
|-----------|---------------|-------------------|
| Connection | Short-lived, new per request | Persistent |
| Data fetch | Client actively pulls | Server actively pushes |
| Incremental | Full or manual incremental | Auto incremental (added/changed/removed) |
| Latency | Poll interval | Real-time |
| Server state | Stateless | Maintains subscription state |

**MongoDB OpLog-driven real-time:**
```
Data write to MongoDB -> OpLog record -> StreamHub captures -> DDPStreamer -> WebSocket push
```
- DDP real-time is fundamentally driven by MongoDB OpLog tailing
- Any data change automatically triggers client update, no manual push in business code

### Mattermost: WebSocket Events + REST Pull

Mattermost uses a hybrid **WebSocket push + REST pull** model:

**WebSocket event types:**
- `posted` - new message
- `typing` - typing indicator
- `user_updated` - user profile change
- `channel_updated` - channel metadata change
- `status_change` - presence status change

**Event scoping optimization (permanently enabled since v11):**
- `typing` and `reaction` events are only sent to clients that have the corresponding channel/thread open
- Not all channel members receive them, reducing unnecessary traffic

**Sync flow:**
```
1. Client fetches history via REST API (paginated pull)
2. WebSocket receives real-time events
3. On posted event -> incrementally update local state
4. On reconnect -> REST pull messages during disconnect (by last timestamp)
```

### Chat21: MQTT Topic Subscription

Chat21 uses the **MQTT protocol** for real-time communication:

**Subscription model:**
```
Client subscribes to own inbox topic:
  /apps/{appId}/users/{userId}/+/messages/clientadded

On message receipt, parse sender and message type from topic path
```

- MQTT is a lightweight pub/sub protocol, suitable for mobile devices
- QoS 1 (at least once) guarantees no message loss
- Last Will message handles abnormal disconnects
- Retained messages support new subscribers getting latest state

### Sync Mechanism Comparison

| Project | Mechanism | Transport | Server State | Latency | Scalability |
|---------|-----------|-----------|-------------|---------|-------------|
| Turms | Push notification + Pull content | TCP/WebSocket | Stateful (session) | Very Low | High (stateless gateway) |
| Mattermost | WebSocket events + REST | WebSocket + HTTP | Stateful (WS conn) | Low | Medium (sticky session needed) |
| Rocket.Chat | DDP subscription | WebSocket | Stateful (subscription) | Low | Medium (DDPStreamer scalable) |
| Matrix | /sync long polling | HTTP long polling | **Stateless** | Medium | **High** (stateless) |
| Chat21 | MQTT topic subscription | MQTT | Stateful (subscription) | Low | Medium (RabbitMQ-centric) |
| OpenChat | Poll + update | HTTP polling | Stateless | High | High (canister parallel) |

### CBOL Project Sync Strategy Recommendation

Based on CBOL's conversation handoff / conversation management / conversation forwarding scenarios:

1. **Recommended: WebSocket push + REST pull** (Mattermost model)
   - Real-time messages pushed via WebSocket
   - History messages, conversation list pulled via REST pagination
   - On reconnect, incremental pull by last_seq

2. **Consider event scoping optimization**
   - Typing indicators only pushed to users viewing that conversation
   - Reduce unnecessary pushes in large groups

3. **If extreme scalability is needed**
   - Reference Matrix's stateless long polling model
   - Server does not maintain WebSocket connections, simpler horizontal scaling
   - Trade-off: slightly higher latency

4. **If mobile-first**
   - Reference Chat21's MQTT model
   - MQTT is more power-efficient and better for weak networks
   - But requires maintaining an MQTT broker (e.g., RabbitMQ+MQTT plugin or EMQX)
