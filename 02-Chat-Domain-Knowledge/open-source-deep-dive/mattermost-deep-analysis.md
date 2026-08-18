# Mattermost Deep Architecture Analysis

> Source: [mattermost/mattermost](https://github.com/mattermost/mattermost) ⭐ ~28k | MIT/AGPL | Go + React
> Official docs: https://docs.mattermost.com
> Positioning: Enterprise-grade secure collaboration platform, for defense, intelligence, security, and critical infrastructure

---

## 1. Project Overview

Mattermost is an open-core enterprise collaboration platform, with **security, self-hosting, and extensibility** as core characteristics. Its architecture design emphasizes modularity, high availability, and deep customizability, adopted by the US Department of Defense and Fortune 500 companies.

### Monorepo Structure

```
mattermost/
├── server/           # Go backend service
├── webapp/           # React frontend
├── api/              # OpenAPI spec (v4 + playbooks)
├── e2e-tests/        # End-to-end tests
└── tools/            # Development tools
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend language | Go |
| Frontend | React + Redux + TypeScript |
| Database | PostgreSQL (primary) / MySQL (compatible) |
| File storage | Local / NAS / S3 |
| Real-time communication | WebSocket (WSS) |
| Cluster communication | Gossip protocol |
| Build | Go toolchain + Make / Webpack 5 |
| Deployment | Docker / Kubernetes / Terraform |
| Monitoring | Prometheus (port 8067) |

### Version Strategy

| Version | License | Description |
|---------|---------|-------------|
| Team Edition | MIT | Open-source core features |
| Enterprise Edition | Commercial | Clustering, SSO, compliance, advanced security, etc., via build tag conditional compilation |

---

## 2. Architecture Design

### 2.1 Three-Layer Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Access Layer                          │
│  Web / Desktop / Mobile / Email                          │
│  (HTTPS + WSS, load balanced via NGINX/HAProxy)          │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│                 Application Layer                        │
│  Mattermost Server (single Go binary)                    │
│  ┌──────────┬──────────┬──────────┬──────────────────┐  │
│  │ REST API │ WebSocket│  Auth    │ Notification     │  │
│  │  (api4)  │ (wsapi)  │          │ (Push + Email)   │  │
│  └──────────┴──────────┴──────────┴──────────────────┘  │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Business Logic (app)                  │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │           Plugin System (RPC independent process)  │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│              Backend Infrastructure                      │
│  PostgreSQL/MySQL  │  File storage  │  Push proxy  │  LDAP/SSO │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Server Layered Architecture (Go)

```
server/
├── cmd/
│   ├── mattermost/        # Main service entry
│   └── mmctl/             # CLI management tool
├── channels/              # Core channel features
│   ├── api4/              # REST API v4 handlers
│   ├── app/               # Business logic layer (transport-agnostic)
│   ├── store/             # Data access layer
│   │   ├── sqlstore/      # SQL implementation
│   │   └── searchlayer/   # Search functionality
│   ├── web/               # Web handlers
│   └── wsapi/             # WebSocket API
├── platform/              # Platform services
│   ├── services/          # Shared services
│   └── shared/            # Shared utilities
├── public/                # Public API and models
│   ├── model/             # Data models (User, Team, Channel, Post...)
│   ├── plugin/            # Plugin system
│   └── pluginapi/         # Plugin API helpers
├── enterprise/            # Enterprise features (build tag conditional compilation)
└── build/                 # Build scripts and config
```

### 2.3 Layer Responsibilities

| Layer | Directory | Responsibility |
|-------|-----------|---------------|
| **API layer** | `channels/api4/` | RESTful API v4, HTTP request handling, input validation, call app layer, return JSON |
| **Application layer** | `channels/app/` | Business logic and orchestration, transport-agnostic (HTTP/WebSocket) |
| **Storage layer** | `channels/store/` | Data access abstraction, supports PostgreSQL, includes migration management |
| **WebSocket layer** | `channels/wsapi/` | Real-time bidirectional communication, real-time updates, typing indicators, presence |
| **Model layer** | `public/model/` | Shared data structures, config models, API request/response, validation and serialization |

### 2.4 Design Characteristics

- **Single binary deployment**: Go static compilation, one binary contains all server functionality
- **Transport-agnostic business logic**: app layer does not depend on HTTP or WebSocket, can be called by any transport layer
- **Enterprise conditional compilation**: feature toggles via Go build tags (`enterprise`, `sourceavailable`)
- **Cluster-aware**: Enterprise edition supports multi-node clusters, Gossip protocol syncs state

---

## 3. Network Communication Design

### 3.1 Dual Protocol Communication

| Protocol | Purpose | Characteristics |
|----------|---------|----------------|
| **HTTPS** | Page rendering, API requests, file upload | Intermittent connection, request-response pattern |
| **WSS** | Real-time updates, notifications, typing indicators | Persistent connection, server push |

> **Key**: If WSS is unavailable and only HTTPS is used, the system appears normal but real-time updates fail; can only refresh page to get new messages.

### 3.2 WebSocket Design

**Endpoint**: `/api/v4/websocket`

**Server event types**:

| Event | Purpose |
|-------|---------|
| `posted` | New message published |
| `typing` | User typing indicator |
| `user_updated` | User profile change |
| `channel_updated` | Channel metadata change |
| `status_change` | User presence status change |
| Custom events | Plugins publish via `PublishWebSocketEvent` |

**Event scoping optimization** (permanently enabled since v11):
- `typing` and `reaction` events are**only sent to clients that have the corresponding channel/thread open**
- Reduces unnecessary network traffic and client processing overhead

**Client reconnection**:
- Auto-reconnect with exponential backoff
- Redux middleware handles WebSocket events, auto-updates store
- Graceful recovery after network interruption

### 3.3 Service Ports

| Service | Default Port | Protocol | Direction |
|---------|-------------|----------|-----------|
| HTTP/WebSocket | 8065 (or 80/443 TLS) | TCP | Inbound |
| Cluster Gossip | 8074 | TCP/UDP | Inbound (internal) |
| Metrics | 8067 | TCP | Inbound |
| PostgreSQL | 5432 | TCP | Outbound |
| MySQL | 3306 | TCP | Outbound |
| LDAP | 389 | TCP/UDP | Outbound |
| S3 storage | 443 (TLS) | TCP | Outbound |
| SMTP | 10025 | TCP/UDP | Outbound |
| Push notifications | 443 (TLS) | TCP | Outbound |

### 3.4 Cluster Communication

- Enterprise multi-node clusters sync node state via **Gossip protocol** (port 8074)
- WebSocket connections require **Sticky Session**, ensuring client always connects to same node
- Load balancer (NGINX/HAProxy) distributes HTTP and WebSocket connections

---

## 4. Plugin System Architecture

Mattermost's plugin system is the core of its extensibility, divided into **server plugins** and **webapp plugins**.

### 4.1 Server Plugins

```
server/public/plugin/
├── api.go           # Plugin API interface
├── hooks.go         # Lifecycle hooks
├── client_rpc.go    # Plugin RPC client
└── environment.go   # Plugin environment management
```

**Characteristics**:
- Plugins run as **independent processes**, communicate with main service via **RPC**
- Written in Go
- Plugins loaded from `plugins/` directory as binaries

**Plugin lifecycle**:
```
1. Load plugin binary from plugins/ directory
2. OnActivate hook -> initialize
3. Register hooks and HTTP handlers
4. Communicate with server via RPC
5. OnDeactivate hook -> cleanup
```

**Plugin capabilities**:
- Hooks intercept server events (message publish, user join, etc.)
- Register custom API endpoints
- Access Mattermost functionality via plugin API
- Publish custom WebSocket events

### 4.2 Webapp Plugins

- JavaScript bundle, integrated with React frontend
- Registration points: custom message types, channel header buttons, root components, slash commands, menu items, Reducer extensions

```javascript
// Plugin entry
export default class Plugin {
  initialize(registry, store) {
    registry.registerPostTypeComponent('custom_type', CustomComponent);
  }
}
```

### 4.3 Plugin Communication

```
Server plugin ←RPC→ Mattermost Server ←WebSocket→ Webapp plugin
```

Server plugins can push custom events to clients via `PublishWebSocketEvent`, webapp plugins listen and handle.

### 4.4 Official Plugin Examples

| Plugin | Function |
|--------|----------|
| Calls | Self-hosted voice calls + screen sharing |
| Playbooks | Workflow automation (SOP) |
| Boards | Kanban project management |
| Agents | AI agent integration (multi-agent/multi-LLM) |
| Jira/GitLab | Third-party system integration |

---

## 5. Data Model & Storage

### 5.1 Core Data Tables

| Table | Description |
|-------|-------------|
| Users | User accounts and credentials |
| Teams | Teams (workspaces) |
| Channels | Channels (public/private/DM) |
| Posts | Messages (core data table) |
| ChannelMembers | Channel membership relationships |
| TeamMembers | Team membership relationships |
| Reactions | Message emoji reactions |
| Status | User presence |
| SidebarCategories | Sidebar categories |
| SidebarChannels | Sidebar channel ordering |
| Configurations | System config (includes SchemaVersion) |
| Compliance | Compliance exports |
| SharedChannels | Shared channels (cross-instance) |

### 5.2 Storage Design Points

- **PostgreSQL primary**, MySQL compatible (MySQL uses text type, PostgreSQL uses varchar, note during migration)
- **Schema version** stored in Configurations table's JSON config (`SchemaVersion` field)
- Supports **online Schema migration** (e.g., v7.1 adding column+index to Reactions table, 12M Posts + 2.5M Reactions ~1min 34sec)
- File storage supports: local filesystem / NAS / S3 (including MinIO compatible)

### 5.3 Message (Posts) Storage

- Messages are core data, support channel messages, thread replies, DMs
- Message content supports Markdown, attachments, custom types
- Message deletion supports soft delete (deletion timestamp field)
- Enterprise edition supports compliance export and data retention policies

---

## 6. Frontend Architecture (React)

### 6.1 Directory Structure

```
webapp/
├── channels/              # Main web app
│   └── src/
│       ├── actions/       # Redux actions
│       ├── components/    # React components
│       ├── reducers/      # Redux reducers
│       ├── selectors/     # Redux selectors
│       ├── plugins/       # Plugin integration
│       ├── sass/          # Global styles
│       ├── utils/         # Utility functions
│       └── i18n/          # Internationalization
└── platform/              # Shared platform package
    ├── types/             # TypeScript type definitions
    ├── client/            # API client library (Client4)
    ├── components/        # Reusable UI components
    ├── mattermost-redux/  # Redux state management
    └── shared/            # Shared utilities
```

### 6.2 State Management

- **Redux** centralized state management
- **Normalized storage**: entities (users, channels, teams, posts) stored in lookup tables by ID, references stored separately
- `Client4` class provides typed REST API interface
- WebSocket events automatically handled by Redux middleware and update store

### 6.3 Component Patterns

- Function components + Hooks (modern approach)
- Styles co-located with components (component.tsx + component.scss)
- Tests co-located with components (component.test.tsx)
- Performance optimization: `React.memo`, `useCallback`, `useMemo`

---

## 7. Request Data Flow Example

Taking "send message" as example:

```
1. User clicks send -> React component dispatches createPost(post)
2. Redux action creator -> Client4.createPost(post) -> POST /api/v4/posts
3. Server api4/post.go createPost() -> validate -> c.App.CreatePost()
4. app layer -> permission check -> plugin hooks -> Store().Post().Save()
5. store layer -> SQL INSERT into Posts table
6. app layer -> PublishWebSocketEvent("posted", ...) -> broadcast to online clients in channel
7. Client Redux middleware receives WebSocket event -> updates store -> React re-renders
```

---

## 8. Security & High Availability

### 8.1 Security

| Mechanism | Description |
|-----------|-------------|
| **Reverse proxy** | NGINX/hardware proxy, enforce HTTPS, load balancing |
| **SSL/TLS** | Transport encryption |
| **Permission model** | Fine-grained user permissions and roles |
| **SSO** | SAML, LDAP/AD, OAuth2, MFA |
| **VPN recommended** | Recommended deployment behind private network/VPN |
| **Audit logs** | Enterprise compliance auditing |
| **Data retention** | Enterprise message retention policies |

### 8.2 High Availability (Enterprise)

- Multi-node cluster deployment, automatic failover
- Database master-slave replication (PostgreSQL sync/async replication)
- Load balancer distributes traffic
- WebSocket Sticky Session
- Push notification retry mechanism
- Multi-SMTP server redundancy

---

## 9. Design Principles & Trade-offs

| Design Decision | Choice | Trade-off |
|----------------|--------|-----------|
| **Monolithic binary** | Go single binary contains all functionality | Simple deployment, but lower microservice split flexibility |
| **Transport-agnostic business layer** | app layer independent of HTTP/WS | Clean layering, extensible to new transport protocols |
| **Plugin independent process** | RPC communication rather than in-process | Good stability (plugin crash doesn't affect main process), but has RPC overhead |
| **PostgreSQL primary** | Prioritize PostgreSQL support | Full features, but MySQL compatibility needs maintenance |
| **Open core + Enterprise** | MIT core + commercial enterprise features | Active community + commercial sustainability |
| **WebSocket event scoping** | Only send to relevant clients | Reduces traffic, but increases server state management complexity |

---

## 10. Reference Value for CBOL Project

### 10.1 Architecture Level

| Mattermost Design | CBOL Can Learn |
|------------------|---------------|
| Transport-agnostic business logic layer (app separated from api4/wsapi) | Business logic not bound to HTTP/WebSocket, easy to extend |
| Plugin system (independent process + RPC) | Extensible architecture, functional modularity |
| Single binary deployment | Simplify deployment and operations |
| Cluster Gossip protocol | Multi-node state synchronization solution |

### 10.2 Network Communication Level

| Mattermost Design | CBOL Can Learn |
|------------------|---------------|
| HTTPS + WSS dual protocol | REST API + WebSocket separation |
| WebSocket event scoping optimization | Only push to relevant clients, reduce traffic |
| Client exponential backoff reconnection | Connection recovery mechanism |
| Sticky Session | WebSocket load balancing strategy |

### 10.3 Data Model Level

| Mattermost Design | CBOL Can Learn |
|------------------|---------------|
| Normalized Redux state (entities stored by ID) | Frontend state management design |
| Schema version management + online migration | Database evolution strategy |
| Soft delete (deletion timestamp field) | Data retention and recovery |

### 10.4 Extensibility Level

| Mattermost Design | CBOL Can Learn |
|------------------|---------------|
| Plugin hook mechanism (message publish, user join, etc.) | Business extension point design |
| Custom WebSocket events | Plugin-frontend real-time communication |
| OpenAPI spec (api/ directory) | API documentation and client SDK generation |

---

## 11. References

- GitHub: https://github.com/mattermost/mattermost
- Official docs: https://docs.mattermost.com
- Application architecture: https://docs.mattermost.com/deployment-guide/application-architecture.html
- System architecture (community): https://mintlify.wiki/mattermost/mattermost/dev/architecture
- API docs: https://api.mattermost.com
- Plugin development: https://developers.mattermost.com/integrate/plugins/

---

*Analysis date: 2026-08-18*
