# Module Structure

> CBOL codebase module decomposition. Fill in based on actual project structure.

## How to Document

1. List all Maven/Gradle modules
2. For each module: responsibility, key packages, dependencies
3. Document module dependency graph
4. Identify circular dependencies (to avoid)

## Module Template

```markdown
## Module: {module-name}

### Responsibility

### Key Packages
- `com.cbol.{module}.controller` - 
- `com.cbol.{module}.service` - 
- `com.cbol.{module}.repository` - 
- `com.cbol.{module}.domain` - 
- `com.cbol.{module}.config` - 

### Dependencies
| Dependency | Purpose |
|-----------|---------|
|           |         |

### Exposed Interfaces
| Interface | Description |
|-----------|-------------|
|           |             |
```

## Module Index

| Module | Layer | Responsibility | Key Classes |
|--------|-------|---------------|-------------|
| cbol-gateway | Access | WebSocket/HTTP entry | |
| cbol-user | Business | User management | |
| cbol-conversation | Business | Conversation management | |
| cbol-message | Business | Message send/receive/store | |
| cbol-group | Business | Group management | |
| cbol-push | Infrastructure | Push notifications | |
| cbol-common | Shared | Utilities, constants | |
| cbol-domain | Shared | Domain entities | |

## Module Dependency Graph

```
cbol-gateway --> cbol-user
             --> cbol-conversation
             --> cbol-message
             --> cbol-group

cbol-message --> cbol-domain
              --> cbol-common
              --> cbol-push
```

## Layer Rules

| Layer | Can Depend On | Cannot Depend On |
|-------|--------------|-----------------|
| Controller | Service, DTO | Repository, Entity |
| Service | Repository, Domain | Controller |
| Repository | Database, Entity | Service |
| Domain | - | Everything (pure) |
| Common | - | Business modules |
