# Reliability Overview

> Patterns for ensuring message delivery, idempotency, and fault tolerance.

## Sub-topics

| Document | Description |
|----------|-------------|
| [message-delivery](./message-delivery.md) | At-least-once / exactly-once delivery guarantees |
| [idempotency](./idempotency.md) | Safe retries and deduplication |
| [retry-backoff](./retry-backoff.md) | Retry strategies and exponential backoff |

## Reliability Goals

| Metric | Target |
|--------|--------|
| Message delivery success rate | > 99.99% |
| Message loss | 0 (persistent before ACK) |
| Duplicate rate | < 0.01% (idempotent dedup) |
| End-to-end latency (online) | < 200ms p99 |
| Availability | > 99.9% |
