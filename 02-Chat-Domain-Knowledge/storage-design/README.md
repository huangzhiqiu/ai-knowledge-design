# Storage Design Overview

> How messages, conversations, and state are persisted.

## Sub-topics

| Document | Description |
|----------|-------------|
| [message-storage](./message-storage.md) | Message persistence, sharding, indexing |
| [offline-messages](./offline-messages.md) | Offline message queue and delivery |
| [timeline-model](./timeline-model.md) | Write-fanout vs read-fanout timeline models |

## Storage Tiers

| Tier | Technology | Use Case |
|------|-----------|----------|
| Hot cache | Redis | Session registry, online state, recent messages |
| Warm storage | MySQL / PostgreSQL | User, conversation, membership metadata |
| Cold storage | Cassandra / HBase | Message history (high write throughput) |
| Object storage | S3 / MinIO | Media files (images, videos, files) |
