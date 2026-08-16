# Multi-Device Sync

## Sync Strategies

### Strategy 1: Push-only (real-time fanout)
- Server pushes message to all online devices immediately
- Offline devices miss messages (need separate catch-up)
- Simple, low latency for online users

### Strategy 2: Pull-only (polling)
- Client periodically polls for new messages
- High latency, server load
- Simple to implement, works with HTTP only

### Strategy 3: Push + Pull (hybrid) -- RECOMMENDED
- Online: server pushes in real-time
- Reconnect: client pulls missed messages via cursor
- Best of both worlds

## Sync Flow (Hybrid Model)

```
1. Device connects -> sends last_delivered_seq
2. Server compares with conversation max_seq
3. If gap exists: server pushes historical messages (catch-up)
4. After catch-up: real-time push for new messages
5. Device ACKs each batch -> updates last_delivered_seq
```

## Message Fanout

When a message arrives for a user with N online devices:
```
Message -> Message Router -> Session Registry -> Device 1 (push)
                                      |------> Device 2 (push)
                                      |------> Device N (push)
                           Offline devices -> stored in inbox
```

## Read State Sync

Read state is per-user (not per-device):
- User reads on Device A -> `last_read_seq` updates
- Server broadcasts read event to Device B, C, D
- All devices show same unread count

## Consistency Model

| Aspect | Model |
|--------|-------|
| Message delivery | At-least-once (idempotent dedup by msg_id) |
| Message ordering | Per-conversation seq_id |
| Read state | Eventual consistency (last-write-wins by timestamp) |
| Presence | Eventual (heartbeat + timeout) |

## Reference: WhatsApp Multi-Device
- Each device has independent identity key
- Sender encrypts once per recipient device
- Server is temporary queue only (E2EE)
- Companion devices sync through primary device
