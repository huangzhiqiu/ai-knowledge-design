# Message Routing

## Problem

How to deliver a message from sender to the correct recipient(s) efficiently, when recipients may be on different gateway nodes or offline.

## Routing Flow

```
Sender sends message
    |
    v
Gateway (sender's connection)
    |
    v
Message Service (persist, assign seq_id)
    |
    v
Message Router (determine recipients)
    |
    +--> Online recipients: Session Registry -> Gateway nodes -> Push
    |
    +--> Offline recipients: Offline Queue -> Deliver on reconnect
    |
    +--> Push Notification Service (if offline)
```

## Recipient Resolution

### 1-on-1 Chat
- Recipient = the other user in conversation
- Look up user's active devices in session registry

### Group Chat
- Recipients = all members of group (except sender)
- Batch lookup in session registry
- Large groups: may need pagination or fanout service

### Channel (large)
- Recipients = all members (could be 100K+)
- Use read-fanout: don't push to everyone, store once, clients pull
- Or use push notification only, message pulled on open

## Routing Strategies

### Strategy 1: Direct Push (Small Scale)

```
Message Router -> Session Registry -> for each recipient:
    lookup gateway_node -> send directly to gateway -> push
```

- Simple, low latency
- Router needs to know all gateway nodes
- Works for < 10 gateway nodes

### Strategy 2: Message Bus Fanout (Medium Scale)

```
Message Router -> publish to topic "user:{user_id}"
Each gateway subscribes to its assigned users
Gateway receives message -> pushes to local connection
```

- Decouples router from gateways
- Scales with message bus throughput
- Redis pub/sub or NATS

### Strategy 3: Consistent Hashing (Large Scale)

```
user_id -> hash -> gateway node
Each gateway owns a range of user hash space
Message sent to owning gateway via message bus
```

- Predictable routing
- Minimizes cross-gateway messages
- On node add/remove: only hash range users migrate

## Multi-device Routing

One user may have multiple devices online:
```
user_id -> [device_A on gateway_1, device_B on gateway_3, device_C on gateway_1]

Message -> fanout to device_A (gw1), device_B (gw3), device_C (gw1)
Gateway 1 pushes to 2 local connections
Gateway 3 pushes to 1 local connection
```

## Routing for Group Messages

### Write Fanout (small groups)
```
Group message -> for each member:
    if online: push to their gateway
    if offline: add to their inbox
```
- O(N) operations per message
- Fast reads (each user has personal inbox)
- Good for groups < 100 members

### Read Fanout (large groups)
```
Group message -> store once in conversation store
User opens group -> pull messages from conversation store
```
- O(1) write
- Slower reads (need to query + merge)
- Good for groups > 100 members / channels

## Routing Metadata

Each routed message carries:
```json
{
  "msg_id": "xxx",
  "conversation_id": "xxx",
  "recipients": ["user_1", "user_2"],
  "sender_id": "user_0",
  "seq_id": 12345,
  "timestamp": 1718438400000,
  "routing": {
    "priority": "normal",
    "ttl": 86400,
    "require_ack": true
  }
}
```

## Reference: WhatsApp Routing
WhatsApp uses Erlang/OTP with session-based routing. Each user connection is an Erlang process. Message routing is process-to-process: find recipient's process, send message directly. If process not found, store for offline.
