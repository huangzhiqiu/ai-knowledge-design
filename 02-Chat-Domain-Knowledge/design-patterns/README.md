# Design Patterns

> Reusable design patterns for chat/IM systems, collected from excellent open-source projects.

## Pattern Index

| Pattern | Category | Description | Source Inspiration |
|---------|----------|-------------|-------------------|
| [Gateway Pattern](./gateway-pattern.md) | Connection | Stateful entry point holding client connections | OpenChat, Mattermost |
| [Message Routing](./message-routing.md) | Delivery | Route messages to correct recipients/devices | WhatsApp, Turms |
| [Fanout Pattern](./fanout-pattern.md) | Scaling | Write-fanout vs read-fanout for group messages | WeChat, Turms |
| [Presence Management](./presence-management.md) | State | Online/offline status, heartbeat, typing indicators | Rocket.Chat, Matrix |

## Pattern Categories

### Connection Management
- Gateway Pattern: WebSocket/TCP connection management
- Session Registry: user -> gateway node mapping

### Message Delivery
- Message Routing: recipient resolution and delivery
- Fanout: group message replication strategy
- Push-Pull: real-time push + catch-up pull

### State Management
- Presence: online/offline/away status
- Typing Indicators: ephemeral state
- Read Receipts: per-user read state

### Reliability
- See [../reliability/](../reliability/) for delivery guarantees, idempotency, retry
