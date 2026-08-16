# User Model

## Entity Definition

| Field | Type | Description |
|-------|------|-------------|
| user_id | string (UUID/Snowflake) | Global unique user identifier |
| username | string | Display name |
| avatar | string | Avatar URL |
| status | enum | online / offline / away / busy |
| created_at | timestamp | Account creation time |
| updated_at | timestamp | Last profile update |

## User States

```
[Active] --disable--> [Disabled]
   |                     |
   +--delete--> [Deleted] --purge--> [Purged]
```

## User Preferences
- Notification settings (per conversation)
- Privacy settings (read receipt visibility, last seen)
- Theme / display preferences

## Related Concepts
- **Identity**: Authentication credentials (separate from profile)
- **Presence**: Online/offline status
- **Relationship**: Friend / block / contact lists

## Reference: Mattermost User Model
Mattermost separates `User` (authentication) from `UserProfile` (display data), supporting multiple auth methods per user.
