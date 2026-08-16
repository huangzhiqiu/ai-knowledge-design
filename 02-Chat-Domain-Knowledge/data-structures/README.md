# Data Structures

> Core data structures for IM system implementation.

## Message Envelope

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImMessage {
    private String msgId;           // Global unique ID
    private Long seqId;             // Per-conversation sequence
    private String conversationId;
    private String senderId;
    private MessageType type;
    private Object content;         // Type-specific content
    private Long timestamp;
    private MessageStatus status;
    private String replyTo;
    private Map<String, Object> metadata;
}
```

## Conversation

```java
@Data
@Builder
public class Conversation {
    private String id;
    private ConversationType type;  // DIRECT, GROUP, CHANNEL
    private String name;
    private String ownerId;
    private String lastMessageId;
    private Long lastMessageAt;
    private Long maxSeq;
    private LocalDateTime createdAt;
    private Map<String, Object> metadata;
}
```

## Membership

```java
@Data
@Builder
public class Membership {
    private String userId;
    private String conversationId;
    private MemberRole role;        // OWNER, ADMIN, MEMBER, MUTED
    private Long lastReadSeq;
    private LocalDateTime joinedAt;
    private Boolean muted;
    private Boolean pinned;
}
```

## User

```java
@Data
@Builder
public class User {
    private String id;
    private String username;
    private String avatar;
    private UserStatus status;      // ONLINE, OFFLINE, AWAY, BUSY
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
}
```

## Device

```java
@Data
@Builder
public class Device {
    private String id;
    private String userId;
    private DevicePlatform platform; // IOS, ANDROID, WEB, DESKTOP
    private String deviceName;
    private String appVersion;
    private String pushToken;
    private LocalDateTime lastSeenAt;
}
```

## Session (Connection)

```java
@Data
@Builder
public class Session {
    private String id;
    private String userId;
    private String deviceId;
    private String serverNode;      // Gateway node holding connection
    private Channel channel;        // Netty channel (transient)
    private LocalDateTime connectedAt;
    private LocalDateTime lastHeartbeat;
    private SessionStatus status;   // ACTIVE, IDLE, DISCONNECTED
}
```

## Offline Message

```java
@Data
@Builder
public class OfflineMessage {
    private Long id;
    private String userId;
    private String msgId;
    private String conversationId;
    private Long seqId;
    private LocalDateTime createdAt;
}
```

## Read Receipt

```java
@Data
@Builder
public class ReadReceipt {
    private String userId;
    private String conversationId;
    private Long lastReadSeq;
    private LocalDateTime readAt;
}
```

## Typing Indicator

```java
@Data
@Builder
public class TypingIndicator {
    private String userId;
    private String conversationId;
    private Boolean isTyping;
    private LocalDateTime timestamp;
}
```

## Enums

```java
public enum MessageType {
    TEXT, IMAGE, FILE, VOICE, VIDEO, LOCATION, STICKER, SYSTEM, CUSTOM
}

public enum MessageStatus {
    SENDING, SENT, DELIVERED, READ, FAILED, REVOKED
}

public enum ConversationType {
    DIRECT, GROUP, CHANNEL
}

public enum MemberRole {
    OWNER, ADMIN, MEMBER, MUTED
}

public enum UserStatus {
    ONLINE, OFFLINE, AWAY, BUSY
}

public enum DevicePlatform {
    IOS, ANDROID, WEB, DESKTOP, SERVER
}

public enum SessionStatus {
    ACTIVE, IDLE, DISCONNECTED
}
```

## Redis Data Structures

### Session Registry
```
Key: session:user:{userId}
Type: Hash
Field: deviceId
Value: serverNode (gateway node ID)
TTL: 300s (refreshed by heartbeat)
```

### Presence
```
Key: presence:{userId}
Type: Hash
Fields: status, lastSeen, device
TTL: 300s
```

### Offline Queue
```
Key: offline:{userId}
Type: Sorted Set
Score: seqId
Value: msgId (or full message JSON)
TTL: 30 days
```

### Dedup Cache
```
Key: dedup:{msgId}
Type: String
Value: "1"
TTL: 24h
```

### Conversation Unread
```
Key: unread:{userId}:{conversationId}
Type: String (counter)
Value: unread count
```

## Database Tables (MySQL)

### user
```sql
CREATE TABLE user (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    avatar VARCHAR(255),
    status VARCHAR(16) DEFAULT 'OFFLINE',
    last_seen_at BIGINT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    INDEX idx_username (username)
);
```

### conversation
```sql
CREATE TABLE conversation (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(16) NOT NULL,
    name VARCHAR(128),
    owner_id VARCHAR(64),
    last_message_id VARCHAR(64),
    last_message_at BIGINT,
    max_seq BIGINT DEFAULT 0,
    created_at BIGINT NOT NULL,
    metadata JSON,
    INDEX idx_owner (owner_id),
    INDEX idx_last_message (last_message_at)
);
```

### membership
```sql
CREATE TABLE membership (
    user_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) DEFAULT 'MEMBER',
    last_read_seq BIGINT DEFAULT 0,
    joined_at BIGINT NOT NULL,
    muted TINYINT DEFAULT 0,
    pinned TINYINT DEFAULT 0,
    PRIMARY KEY (user_id, conversation_id),
    INDEX idx_conversation (conversation_id),
    INDEX idx_user (user_id)
);
```

### message
```sql
CREATE TABLE message (
    msg_id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    seq_id BIGINT NOT NULL,
    sender_id VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL,
    content JSON NOT NULL,
    timestamp BIGINT NOT NULL,
    status VARCHAR(16) DEFAULT 'SENT',
    reply_to VARCHAR(64),
    edited_at BIGINT,
    deleted_at BIGINT,
    UNIQUE KEY uk_conv_seq (conversation_id, seq_id),
    INDEX idx_sender (sender_id, timestamp)
);
```

### device
```sql
CREATE TABLE device (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    device_name VARCHAR(128),
    app_version VARCHAR(32),
    push_token VARCHAR(255),
    last_seen_at BIGINT,
    created_at BIGINT NOT NULL,
    INDEX idx_user (user_id)
);
```

### offline_message
```sql
CREATE TABLE offline_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    msg_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    seq_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_user_seq (user_id, seq_id),
    INDEX idx_created (created_at)
);
```

## Reference: Mattermost Data Model
Mattermost uses: `Users`, `Teams`, `Channels`, `Posts` (messages), `ChannelMembers`, `TeamMembers`. Posts store `ChannelId`, `UserId`, `Message`, `CreateAt`, `Props` (JSON for metadata).
