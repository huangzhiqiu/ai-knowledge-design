# Message States

## State Machine

```
                         +-------------------+
                         |                   |
                         v                   |
+----------+    +---+  +------+  +---------+  |  +------+
| sending  |--->|ack|->| sent |->|delivered|--+->| read |
+----------+    +---+  +------+  +---------+     +------+
     |              |       |          |
     |              |       |          |
     v              v       v          v
+----------+    +--------+------+  +---------+
| failed   |<---|network|server|  | revoked |
+----------+    +--------+------+  +---------+
     |
     +--resend--> [sending]
```

## State Definitions

| State | Trigger | Visible to Sender | Visible to Receiver |
|-------|---------|-------------------|---------------------|
| sending | Client sends, waiting for server ACK | Yes (local) | No |
| sent | Server ACK received | Yes | No (not yet delivered) |
| delivered | Pushed to at least one receiver device | Yes | Yes |
| read | Receiver opens conversation | Yes (if enabled) | Yes |
| failed | Server reject / network timeout | Yes (error) | No |
| revoked | Sender recalls within time window | Yes (replaced) | Yes (replaced) |

## ACK Mechanism

### Three-way acknowledgment
1. **Server ACK**: Server receives and persists message -> `sent`
2. **Delivery ACK**: Client receives message -> `delivered`
3. **Read ACK**: User views message -> `read`

### ACK aggregation
- Delivery ACK: per device, aggregated to conversation level
- Read ACK: per user, shared across all devices (last_read_seq)

## Timeout & Retry

| State | Timeout | Action |
|-------|---------|--------|
| sending -> sent | 5-10s | Mark failed, allow retry |
| sent -> delivered | N/A | Wait for online push or offline storage |
| delivered -> read | N/A | Optional, depends on privacy settings |

## Reference: Tencent RTC Chat
>99.99% message success rate via client persistence + server ACK with idempotent retries + offline push across APNs/FCM/OEM channels.
