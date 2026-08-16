# Presence Management

## What is Presence?

Presence = online/offline status of users, plus optional "away", "busy", "typing" states.

```
[Offline] --connect--> [Online] --idle--> [Away] --activity--> [Online]
   ^                       |
   +-------disconnect------+
```

## Presence States

| State | Meaning | Trigger |
|-------|---------|---------|
| online | User actively using app | Connection + recent activity |
| away | User online but inactive | No activity for N minutes |
| busy | User in do-not-disturb | User-set status |
| offline | User not connected | No active connection |
| typing | User is typing (ephemeral) | Typing indicator event |

## Architecture

```
Client <--> Gateway <--> Presence Service <--> Redis (presence store)
                              |
                              +--> Message Bus (presence events)
```

## Presence Storage (Redis)

### User Presence Key
```redis
# Hash: presence state
HSET presence:{user_id} status online last_seen 1718438400000 device mobile

# TTL: auto-expire if no heartbeat
EXPIRE presence:{user_id} 300  # 5 minutes
```

### Active Connections
```redis
# Set: which devices are online for user
SADD presence:active:{user_id} {device_id}:{gateway_node}

# On disconnect: remove
SREM presence:active:{user_id} {device_id}:{gateway_node}

# If set empty -> user offline
```

## Heartbeat Mechanism

```
Client -> Gateway: ping (every 30s)
Gateway -> Presence Service: update last_seen
Presence Service -> Redis: refresh TTL

If no heartbeat for > 90s (3 missed):
  -> mark device offline
  -> if no devices left: mark user offline
  -> broadcast presence change to contacts
```

### Heartbeat Parameters

| Parameter | Recommended |
|-----------|-------------|
| Heartbeat interval | 30 seconds |
| Timeout threshold | 90 seconds (3x interval) |
| Presence TTL | 300 seconds (5 min) |
| Mobile heartbeat (background) | 5-15 minutes |

## Presence Broadcast

When user's presence changes:
1. Determine which users should be notified (contacts, group members)
2. Publish presence event to message bus
3. Gateways push to interested online users

### Optimization: Subscribe Model
- Users subscribe to presence of their contacts
- Only subscribers receive updates
- Avoid broadcasting to everyone

```redis
# Set: who subscribes to this user's presence
SMEMBERS presence:subscribers:{user_id}
```

## Typing Indicator

Ephemeral presence state:
```
Client types -> send typing.start event -> broadcast to conversation members
Client stops -> send typing.stop event (or timeout after 5s)
```

- Don't persist typing state
- Timeout after 5-10 seconds of no typing activity
- Throttle: don't send typing events more than once per 3s

## Last Seen

"Last seen" timestamp for offline users:
- Updated on disconnect and periodic heartbeat
- Privacy setting: user can hide last seen
- Shown as "last seen recently" / "last seen X minutes ago" / "last seen today at HH:MM"

## Mobile Presence Challenges

### Background mode
- iOS/Android kill background connections
- Use push notifications as presence proxy
- App in background = effectively offline for real-time

### Doze mode (Android)
- Network access restricted
- Heartbeat may not fire
- Rely on FCM high-priority messages to wake

### Solution
- Mark mobile user online only when app is foreground
- Background = offline (but push notifications still work)
- "Last seen" reflects when app was last foregrounded

## Privacy Considerations

| Setting | Effect |
|---------|--------|
| Show online status | Contacts can see if user is online |
| Show last seen | Contacts can see last active time |
| Read receipts | Others see when user reads messages |
| Blocked users | No presence info shared |

## Reference: Rocket.Chat Presence
Rocket.Chat uses MongoDB + Meteor DDP for presence. User status tracked in `users` collection with `status` field (online/away/offline/busy). Presence updates broadcast via DDP subscriptions.

## Reference: Matrix Presence
Matrix has optional presence over federation. EDUs (Ephemeral Data Units) carry presence updates between homeservers. Can be disabled for performance/privacy.
