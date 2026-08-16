# Database Schema

> CBOL database design. Fill in based on actual schema.

## How to Document

1. List all tables/collections
2. For each: fields, indexes, constraints
3. Document relationships (foreign keys)
4. Note sharding strategy if applicable

## Table Template

```markdown
## Table: {table_name}

### Description

### DDL
```sql
CREATE TABLE {table_name} (
    id BIGINT PRIMARY KEY,
    ...
);
```

### Fields
| Field | Type | Nullable | Default | Index | Description |
|-------|------|----------|---------|-------|-------------|
|       |      |          |         |       |             |

### Indexes
| Index Name | Fields | Type | Purpose |
|-----------|--------|------|---------|
|           |        |      |         |

### Relationships
| Relation | Target Table | Foreign Key | Cardinality |
|----------|-------------|-------------|-------------|
|          |             |             |             |
```

## Table Index

| Table | Category | Row Count Est. | Sharding |
|-------|----------|---------------|----------|
| user | Metadata | | |
| conversation | Metadata | | |
| message | Time-series | | conversation_id |
| group | Metadata | | |
| group_member | Relationship | | group_id |
| device | Metadata | | |
| offline_message | Queue | | user_id |

## Sharding Strategy

### Shard Key Selection
| Table | Shard Key | Reason |
|-------|-----------|--------|
| message | conversation_id | All messages in one conversation on same shard |
| offline_message | user_id | User's offline messages together |

### Sharding Middleware
- Tool: ShardingSphere / MyCat / custom
- Shard count: 
- Shard algorithm: 

## Caching Strategy

| Data | Cache Key | TTL | Invalidation |
|------|-----------|-----|-------------|
| User profile | user:{id} | 30min | Update evict |
| Conversation | conv:{id} | 10min | Update evict |
| Session registry | session:{user_id} | 5min | Heartbeat refresh |
| Online status | presence:{user_id} | 90s | Heartbeat refresh |
