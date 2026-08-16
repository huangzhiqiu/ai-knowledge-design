# Gateway Pattern

## What is a Gateway?

The gateway (or connection server) is the stateful entry point that holds all client connections (WebSocket / TCP) and routes real-time traffic.

```
Clients <--> [Gateway Cluster] <--> [Backend Services]
              (stateful)            (stateless)
```

## Responsibilities

| Responsibility | Description |
|---------------|-------------|
| Connection management | Accept, maintain, close connections |
| Authentication | Validate token on connect |
| Heartbeat | Detect dead connections |
| Message push | Send real-time messages to clients |
| Connection routing | Know which user is on which gateway node |
| Protocol translation | WebSocket <-> internal message format |
| Rate limiting | Per-connection / per-user limits |

## Architecture

```
                    ┌─────────────┐
                    │  Load       │
                    │  Balancer   │
                    └──────┬──────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
    ┌─────▼─────┐   ┌─────▼─────┐   ┌─────▼─────┐
    │ Gateway 1 │   │ Gateway 2 │   │ Gateway N │
    │ (node-1)  │   │ (node-2)  │   │ (node-n)  │
    └─────┬─────┘   └─────┬─────┘   └─────┬─────┘
          │                │                │
          └────────────────┼────────────────┘
                           │
                    ┌──────▼──────┐
                    │  Session    │
                    │  Registry   │
                    │  (Redis)    │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ Message Bus │
                    │ (NATS/Kafka)│
                    └─────────────┘
```

## Session Registry

Maps `user_id` -> `gateway_node` for active connections.

```redis
# Hash: user -> gateway node
HSET session:user:{user_id} {device_id} {gateway_node_id}

# Set: gateway node -> connected users
SMEMBERS session:node:{gateway_node_id}
```

### Routing a message to user:
1. Look up `user_id` in session registry -> get `gateway_node`
2. Send message to that gateway via message bus
3. Gateway pushes to client connection

## Connection State

Each connection on gateway tracks:
```
Connection {
    user_id: string
    device_id: string
    connected_at: timestamp
    last_heartbeat: timestamp
    ip: string
    protocol: websocket / tcp
    status: active / idle / closing
}
```

## Scaling Gateways

### Challenge: Stateful
- Connections are tied to specific gateway nodes
- Can't just add nodes - existing connections don't move

### Strategies:
1. **Horizontal scale**: add nodes, new connections go to new nodes
2. **Consistent hashing**: route users to nodes by hash (minimizes disruption on scale)
3. **Connection draining**: on shutdown, migrate connections to other nodes (client reconnects)

### Capacity Planning
- Single gateway node: ~50K-100K concurrent WebSocket connections (tuned Linux)
- Need: high file descriptor limit, epoll/kqueue, optimized kernel params
- Memory: ~10-50KB per connection

## Gateway vs API Server

| Aspect | Gateway | API Server |
|--------|---------|------------|
| Connection | Long-lived (WebSocket) | Short-lived (HTTP) |
| State | Stateful (holds connection) | Stateless |
| Protocol | WebSocket / TCP | REST / GraphQL |
| Scaling | Session-aware | Horizontal, any node |
| Use case | Real-time push | CRUD operations |

## Reference: OpenChat Scaling
OpenChat scaled WebSocket with Go + Redis pub/sub: each server broadcasts to local clients, Redis pub/sub distributes messages across server instances.
