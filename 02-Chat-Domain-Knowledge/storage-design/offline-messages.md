# Offline Messages

## Problem
When a user is offline, messages must be stored and delivered when they reconnect.

## Architecture

```
Sender -> Message Server -> Online? -> Yes: push via WebSocket
                           |
                           +-> No: store in Offline Queue
                                     |
                                     v
                          User reconnects -> pull offline messages -> deliver -> ACK -> remove
```

## Offline Queue Design

### Option 1: Database table
```sql
CREATE TABLE offline_messages (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    msg_id      VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    seq_id      BIGINT NOT NULL,
    created_at  BIGINT NOT NULL,
    INDEX idx_user (user_id, created_at)
);
```

### Option 2: Redis sorted set (for active users)
- Key: `offline:{user_id}`
- Score: seq_id
- Member: msg_id or full message
- TTL: 7-30 days
- Fast read, but memory cost

### Option 3: Hybrid
- Recent offline messages in Redis (fast reconnect)
- Full history in DB (for long-offline users)
- On reconnect: check Redis first, fall back to DB

## Delivery Flow

```
1. User reconnects with device_id
2. Server queries offline messages for user (by last_delivered_seq)
3. Batch push messages to client (e.g., 100 per batch)
4. Client ACKs each batch
5. Server removes ACKed messages from offline queue
6. Repeat until queue empty
```

## Offline Push Notifications

When message arrives for offline user:
1. Store in offline queue
2. Send push notification via:
   - iOS: APNs (Apple Push Notification service)
   - Android: FCM (Firebase) + OEM channels (Huawei, Xiaomi, OPPO, Vivo)
3. Push payload: badge count, sender, preview text
4. User taps notification -> app opens -> pulls full message

## Push Notification Aggregation

- Per-user notification coalescing (avoid spam)
- Batch multiple messages into one notification
- Reset badge when user opens app

## TTL & Cleanup

| Message Age | Action |
|-------------|--------|
| < 7 days | Keep in Redis + DB |
| 7-30 days | Keep in DB only |
| > 30 days | Archive to cold storage or delete |
| > 90 days | Delete (configurable per policy) |

## Edge Cases

### User never comes back
- TTL cleanup prevents unbounded growth
- Orphaned messages cleaned by scheduled job

### Multiple devices
- Offline queue per user, not per device
- When any device connects and ACKs, messages removed for all devices
- Each device tracks its own last_delivered_seq

### Message recall while offline
- Remove from offline queue if recalled before delivery
- Or deliver recall event alongside message

## Reference: Tencent RTC Chat
Offline push across APNs, FCM, and OEM channels. Client persistence + server ACK with idempotent retries ensures >99.99% delivery.
