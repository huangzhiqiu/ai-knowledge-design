# Device & Session Model

## Device Entity

| Field | Type | Description |
|-------|------|-------------|
| device_id | string | Unique device identifier |
| user_id | string | Bound user |
| platform | enum | ios / android / web / desktop / server |
| device_name | string | Human-readable device name |
| app_version | string | Client app version |
| push_token | string | Push notification token |
| last_seen_at | timestamp | Last activity time |
| created_at | timestamp | First registration time |

## Session Entity

| Field | Type | Description |
|-------|------|-------------|
| session_id | string | Unique session ID |
| user_id | string | Authenticated user |
| device_id | string | Connected device |
| server_node | string | Gateway server holding connection |
| connected_at | timestamp | Connection established |
| last_heartbeat | timestamp | Last heartbeat received |
| status | enum | active / idle / disconnected |

## Multi-Device Policy

### Strategy 1: Single Active Device
- Only one device can be online at a time
- New login kicks out previous device
- Simpler, less sync complexity

### Strategy 2: Multi-Device Concurrent (WhatsApp/Telegram style)
- Multiple devices online simultaneously
- Messages fan out to all online devices
- Offline devices sync on reconnect
- Requires per-device delivery cursor

### Strategy 3: Primary + Companion (Signal style)
- One primary device (phone) + N companion devices
- Companion devices relay through primary
- E2EE keys managed per device

## Connection Lifecycle

```
[Connecting] --auth success--> [Authenticated] --heartbeat--> [Active]
      |                              |                          |
      +--auth fail--> [Rejected]     +--timeout--> [Idle]       |
                                                          +--disconnect--> [Closed]
```

## Reference: WhatsApp Multi-Device
WhatsApp uses device-centric E2EE: each device has its own identity key, sender encrypts message once per recipient device. Server acts as temporary queue only, never stores plaintext.
