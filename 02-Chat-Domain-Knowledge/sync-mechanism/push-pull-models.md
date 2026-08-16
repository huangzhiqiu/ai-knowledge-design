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
