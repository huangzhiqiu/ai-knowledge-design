# Federation Architecture

## What is Federation?

Federation allows independent servers to communicate and share data, without a central authority. Users on different servers can chat with each other.

```
User A (matrix.org) <──federation──> User B (example.com)
     │                                    │
     v                                    v
┌──────────┐                        ┌──────────┐
│Homeserver│                        │Homeserver│
│ matrix.  │ <──signed events──>    │ example. │
│   org    │                        │   com    │
└──────────┘                        └──────────┘
```

## Matrix Federation Model (Reference)

### Core Concepts

| Concept | Description |
|---------|-------------|
| Homeserver | Server that stores user data and room state |
| Room | Shared conversation space, replicated across participating homeservers |
| Event | Atomic unit of change (message, membership, state) |
| PDU | Persistent Data Unit - signed event replicated between servers |
| EDU | Ephemeral Data Unit - non-persistent data (presence, typing) |

### Federation Flow

```
1. Alice (@alice:matrix.org) sends message in room with Bob (@bob:example.com)
2. matrix.org homeserver signs event with Ed25519 key
3. matrix.org sends PDU to example.com via Federation API (HTTPS)
4. example.com verifies signature, stores event
5. example.com pushes event to Bob's connected clients
```

### State Resolution

When servers receive events in different orders (network partitions), they must resolve room state consistently:

- **State Resolution v2**: Matrix's algorithm for deterministic conflict resolution
- Based on event DAG topology and power levels
- All servers converge to same state without central coordinator

### Federation APIs

| API | Purpose |
|-----|---------|
| Server-to-Server | Exchange PDUs (signed events) |
| Client-to-Server | Client REST + WebSocket sync |
| Application Service | Bridges to external networks (IRC, Slack, WhatsApp) |
| Identity Service | Third-party ID lookup (email, phone) |

## Pros of Federation

1. **Decentralization**: No single point of failure or control
2. **Interoperability**: Different providers can communicate
3. **Data sovereignty**: Users choose where their data lives
4. **Scalability**: Load distributed across servers
5. **Censorship resistance**: No central authority can shut down

## Cons of Federation

1. **Complexity**: State resolution, signature verification, replication
2. **Latency**: Cross-server communication adds hops
3. **Spam/abuse**: Harder to moderate across trust boundaries
4. **Privacy**: Metadata leaks (which servers know about which rooms)
5. **Consistency**: Eventual consistency across servers

## When to Consider Federation

| Scenario | Federation? |
|----------|-------------|
| Internal enterprise chat | No (single deployment) |
| Consumer chat app | No (single company) |
| Open standard protocol | Yes (Matrix, XMPP) |
| Multi-tenant SaaS | Maybe (isolated tenants, not federated) |
| Inter-company collaboration | Yes (federated or bridge) |

## Alternative: Bridge Model

Instead of full federation, use **bridges** to connect networks:

```
Matrix Server <--bridge--> Slack
              <--bridge--> WhatsApp
              <--bridge--> IRC
              <--bridge--> Telegram
```

- Matrix acts as hub, bridges translate protocols
- Simpler than full federation between every pair
- Matrix has bridges to most major chat networks
