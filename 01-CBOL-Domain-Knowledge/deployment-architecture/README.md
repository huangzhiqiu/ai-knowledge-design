# Deployment Architecture

> CBOL deployment topology and infrastructure. Fill in based on actual environment.

## Architecture Diagram

```
                    ┌─────────────┐
                    │   CDN / WAF  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ Load Balancer│
                    │ (Nginx/SLB)  │
                    └──────┬──────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
    ┌─────▼─────┐   ┌─────▼─────┐   ┌─────▼─────┐
    │  Gateway  │   │  Gateway  │   │  Gateway  │
    │  (Netty)  │   │  (Netty)  │   │  (Netty)  │
    └─────┬─────┘   └─────┬─────┘   └─────┬─────┘
          │                │                │
          └────────────────┼────────────────┘
                           │
                    ┌──────▼──────┐
                    │ Message Bus  │
                    │ (Kafka/MQ)   │
                    └──────┬──────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
    ┌─────▼─────┐   ┌─────▼─────┐   ┌─────▼─────┐
    │   User    │   │ Conversation│  │  Message  │
    │  Service  │   │   Service  │   │  Service  │
    └─────┬─────┘   └─────┬─────┘   └─────┬─────┘
          │                │                │
          └────────────────┼────────────────┘
                           │
    ┌────────┬─────────────┼─────────────┬──────────┐
    │        │             │             │          │
┌───▼───┐ ┌──▼───┐   ┌─────▼─────┐ ┌─────▼────┐ ┌──▼───┐
│ MySQL │ │Redis│   │  MongoDB  │ │  Kafka   │ │ S3/  │
│ 主从  │ │集群 │   │  副本集   │ │  集群    │ │MinIO │
└───────┘ └──────┘   └───────────┘ └──────────┘ └──────┘
```

## Component Inventory

| Component | Technology | Count | Purpose |
|-----------|-----------|-------|---------|
| Gateway | Netty / Spring WebSocket | 3+ | WebSocket connections |
| API Server | Spring Boot | 3+ | REST API |
| Message Service | Spring Boot | 3+ | Message processing |
| User Service | Spring Boot | 2+ | User management |
| MySQL | 8.0 | 1主2从 | Metadata storage |
| Redis | 6.x | 3主3从 | Cache, session, presence |
| MongoDB | 5.x | 3节点副本集 | Message history |
| Kafka | 3.x | 3 broker | Message bus |
| MinIO/S3 | - | 4节点 | Media storage |
| Nginx | - | 2 | Load balancing |

## Capacity Planning

### Connection Capacity
| Metric | Per Node | Cluster (3 nodes) |
|--------|----------|-------------------|
| WebSocket connections | 50,000 | 150,000 |
| Memory per connection | ~20KB | - |
| Total memory | ~1GB | ~3GB |

### Message Throughput
| Metric | Estimate |
|--------|----------|
| Peak messages/sec | |
| Average message size | |
| Daily message volume | |
| Storage growth/month | |

## Deployment Strategy

### Blue-Green Deployment
- Two identical environments: blue (current), green (new)
- Switch traffic via load balancer
- Zero downtime, instant rollback

### Canary Deployment
- New version deployed to subset of nodes
- Gradually increase traffic
- Monitor metrics before full rollout

### Rolling Update
- Update nodes one by one
- Connection draining before shutdown
- Client reconnects to other nodes

## CI/CD Pipeline

```
Code Push -> Build -> Unit Test -> Sonar Scan -> Package Docker Image
    -> Push to Registry -> Deploy to Test -> Integration Test
    -> Deploy to Prod (manual approval)
```

### Tools
- CI: Jenkins / GitLab CI / GitHub Actions
- Container: Docker
- Orchestration: Kubernetes / Docker Compose
- Registry: Harbor / Docker Hub
- Config: Nacos / Apollo

## Monitoring & Alerting

| Component | Tool | Key Metrics |
|-----------|------|-------------|
| Application | Prometheus + Grafana | QPS, latency, error rate |
| JVM | Micrometer | Heap, GC, threads |
| Database | Prometheus exporter | Connections, slow queries |
| Redis | Prometheus exporter | Hit rate, memory, latency |
| Logs | ELK / Loki | Error logs, slow requests |
| Tracing | SkyWalking / Jaeger | Distributed trace |

## Alert Rules

| Alert | Condition | Severity |
|-------|-----------|----------|
| Service down | Health check fail 3x | Critical |
| High latency | p99 > 500ms for 5min | Warning |
| Error rate | > 1% for 5min | Warning |
| Disk usage | > 80% | Warning |
| Connection drop | > 10% connections lost | Critical |
