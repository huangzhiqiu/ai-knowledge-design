# Message Delivery Guarantees

## Delivery Semantics

| Semantic | Meaning | Complexity | Use Case |
|----------|---------|------------|----------|
| At-most-once | Message may be lost, never duplicated | Low | Metrics, logs |
| At-least-once | Message never lost, may be duplicated | Medium | Chat messages |
| Exactly-once | Message never lost, never duplicated | High | Financial transactions |

## IM Systems: At-Least-Once + Idempotent Deduplication

> Industry standard: guarantee at-least-once delivery, then deduplicate on the receiving end.

## Delivery Pipeline

```
1. Sender sends message
2. Server persists message to durable storage
3. Server ACKs sender (message is safe)
4. Server pushes to online recipients
5. Recipient ACKs delivery
6. Server marks as delivered
7. If no ACK: retry push / store for offline
```

**Key principle: Persist before ACK.** Never ACK a message before it's durably stored.

## Online Delivery

```
Message -> Router -> Session Registry -> Device Connection -> Push -> Device ACK
```

- If device ACKs: mark delivered
- If connection drops: message stays in offline queue
- If push fails: retry with backoff

## Offline Delivery

```
Message -> Offline Queue -> User reconnects -> Batch pull -> Device ACKs -> Remove from queue
```

- Offline queue is durable (DB or persistent Redis)
- Messages removed only after explicit ACK
- TTL for very old messages (configurable)

## Exactly-Once: Is It Worth It?

### Challenges
- Need distributed transaction across: message store, session registry, push service
- Two-phase commit = high latency, lower availability
- Network partitions make true exactly-once impossible (FLP theorem)

### Practical Approach
1. **At-least-once** delivery (simple, available)
2. **Idempotent** message handling (dedup by msg_id)
3. **Client-side** deduplication (ignore duplicate msg_id)

This achieves "effectively exactly-once" from user perspective.

## ACK Mechanism Details

### Per-message ACK
- Client ACKs each message individually
- Simple but chatty (high ACK volume)

### Batch ACK
- Client ACKs highest seq received (implies all lower seq received)
- Fewer ACK packets
- Requires gap-free seq (or gap handling)

### Hybrid
- Batch ACK for normal flow
- Per-message ACK for critical messages (e.g., read receipts)

## Failure Scenarios

| Failure | Mitigation |
|---------|-----------|
| Network drop during send | Client retries with same msg_id (idempotent) |
| Server crash after persist, before ACK | Client retries, server dedups by msg_id |
| Push to client fails | Retry, then offline queue |
| Client crashes mid-sync | Reconnect with last_delivered_seq, resume |
| Disk full | Reject new writes, alert, fail fast |

## Reference: Tencent RTC Chat
>99.99% message success rate via: client persistence + server ACK with idempotent retries + offline push across APNs/FCM/OEM channels.
