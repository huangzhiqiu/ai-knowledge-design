# Sync Mechanism Overview

> How messages and state stay consistent across devices.

## Sub-topics

| Document | Description |
|----------|-------------|
| [multi-device-sync](./multi-device-sync.md) | Multi-device concurrent sync strategies |
| [cursor-design](./cursor-design.md) | Delivery/read cursors and unread calculation |
| [push-pull-models](./push-pull-models.md) | Push, pull, and hybrid sync models |

## Core Problem
A user may be online on multiple devices simultaneously. Messages and read state must converge across all devices with eventual consistency.

## Key Concepts
- **Delivery cursor**: per device, tracks last received seq
- **Read cursor**: per user per conversation, shared across devices
- **Fanout**: message replicated to all online devices
- **Catch-up sync**: offline device pulls missed messages on reconnect
