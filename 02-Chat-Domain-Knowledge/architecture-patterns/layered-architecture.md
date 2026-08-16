# Layered Architecture

## Three-Layer Model (Mattermost reference)

```
┌─────────────────────────────────────────────┐
│              Access Layer                    │
│  (Load Balancer / CDN / API Gateway)         │
├─────────────────────────────────────────────┤
│            Application Layer                 │
│  ┌──────────┬──────────┬──────────────────┐  │
│  │ REST API │WebSocket │  Business Logic  │  │
│  └──────────┴──────────┴──────────────────┘  │
├─────────────────────────────────────────────┤
│              Data Layer                      │
│  ┌────────┬──────────┬────────┬───────────┐  │
│  │  MySQL │  Redis   │MongoDB │ Object S3 │  │
│  └────────┴──────────┴────────┴───────────┘  │
└─────────────────────────────────────────────┘
```

## Access Layer

**Responsibilities:**
- TLS termination
- Load balancing (round-robin / least connections)
- Rate limiting
- WAF / DDoS protection
- WebSocket upgrade handling

**Key concern:** WebSocket is stateful - need sticky sessions or connection-aware routing.

## Application Layer

### RESTful JSON Web Service
- All non-real-time API requests
- CRUD for users, groups, messages
- Stateless, horizontally scalable

### WebSocket Service
- Real-time message push
- Presence updates
- Typing indicators
- Stateful (holds connection)
- Requires session affinity or shared session store

### Business Logic Modules
- User service
- Conversation service
- Message service
- Group service
- Push service
- Search service

## Data Layer

| Store | Technology | Use Case |
|-------|-----------|----------|
| Relational | MySQL / PostgreSQL | Users, groups, memberships, metadata |
| Cache | Redis | Session registry, online state, rate limits, recent messages |
| Document | MongoDB | Message history (Rocket.Chat choice) |
| Wide-column | Cassandra | Message history at massive scale |
| Object | S3 / MinIO | Media files |
| Search | Elasticsearch | Full-text message search |

## Scaling Each Layer

| Layer | Scaling Strategy |
|-------|-----------------|
| Access | Add more LB instances, anycast |
| App (REST) | Horizontal, stateless |
| App (WebSocket) | Horizontal with session registry + Redis pub/sub |
| Data | Read replicas, sharding, clustering |

## Reference: Mattermost Architecture
Mattermost Server components:
- **Platform Services** (`server/channels/app/platform/`): config, logging, caching, metrics, clustering
- **Store Layer** (`server/channels/store/`): data persistence with decorator layers for resilience
- **API Layer** (`server/channels/api4/`): REST API handlers
- **App Layer** (`server/channels/app/`): business logic
