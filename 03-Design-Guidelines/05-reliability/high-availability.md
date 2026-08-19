# High Availability Design Guidelines

> Best practices for designing highly available systems in CBOL Messaging Hub. Covers redundancy, failover, load balancing, health checks, and SLA/SLO design.

## Availability Fundamentals

### Availability Levels

| Availability | Downtime/Year | Downtime/Month | Class |
|-------------|---------------|----------------|-------|
| 99% | 3.65 days | 7.31 hours | Basic |
| 99.9% | 8.76 hours | 43.8 minutes | High |
| 99.95% | 4.38 hours | 21.9 minutes | Higher |
| 99.99% | 52.6 minutes | 4.38 minutes | Very High |
| 99.999% | 5.26 minutes | 26.3 seconds | Extreme |
| 99.9999% | 31.5 seconds | 2.6 seconds | Near-perfect |

### SLA vs SLO vs SLI

```
SLI (Service Level Indicator):
  - Quantitative measure of service level
  - Example: "99.5% of requests return 200 in < 500ms"
  - Measured, not promised

SLO (Service Level Objective):
  - Target value for SLI
  - Example: "99.9% availability per month"
  - Internal target, guides engineering

SLA (Service Level Agreement):
  - Contractual agreement with customers
  - Example: "99.9% uptime, else credit"
  - Legal commitment, penalties for breach

Relationship: SLI (measured) → SLO (target) → SLA (contract)
Best practice: SLO < SLA (internal target stricter than contract)
```

### CBOL Availability Targets

| Service | SLO | RTO | RPO |
|---------|-----|-----|-----|
| API Gateway | 99.99% | < 30s | N/A |
| Auth Service | 99.95% | < 1min | N/A |
| Message Service | 99.99% | < 30s | 0 (no message loss) |
| WebSocket Gateway | 99.99% | < 30s | N/A |
| MySQL (users) | 99.95% | < 5min | < 1min |
| MongoDB (messages) | 99.99% | < 5min | 0 (no message loss) |
| Redis (cache) | 99.9% | < 1min | N/A (cache, can rebuild) |
| Message Queue | 99.95% | < 1min | 0 (no message loss) |

## Redundancy Patterns

### Active-Active (Multi-Master)

```
┌──────────┐     ┌──────────┐
│ Node A   │────►│ Node B   │
│ (active) │◄────│ (active) │
└────┬─────┘     └────┬─────┘
     │                  │
     ▼                  ▼
┌─────────────────────────────┐
│      Shared Database        │
│   (multi-master replication)│
└─────────────────────────────┘

Pros:
  - No failover delay
  - Load distributed across all nodes
  - Highest availability

Cons:
  - Complex conflict resolution
  - Data consistency challenges
  - Higher cost (all nodes active)

Use for: Stateless services, read-heavy workloads
```

### Active-Passive (Primary-Standby)

```
┌──────────┐     ┌──────────┐
│ Primary  │────►│ Standby  │
│ (active) │     │ (passive)│
└────┬─────┘     └────┬─────┘
     │                  │
     ▼                  ▼
┌──────────┐     ┌──────────┐
│ DB Primary│────►│ DB Replica│
└──────────┘     └──────────┘

Failover:
  1. Health check detects primary failure
  2. Promote standby to primary
  3. Update DNS/load balancer
  4. RTO = detection + promotion + DNS propagation

Pros:
  - Simple, no conflict resolution
  - Data consistency (single writer)
  - Lower cost (standby can be smaller)

Cons:
  - Failover delay (seconds to minutes)
  - Standby resources idle until failover
  - Split-brain risk if network partition

Use for: Stateful services, databases, write-heavy workloads
```

### N+1 Redundancy

```
Capacity needed: N nodes
Deployed: N + 1 nodes (one extra for redundancy)

Example:
  - Need 4 nodes to handle peak load
  - Deploy 5 nodes
  - If one node fails, 4 nodes still handle load
  - Auto-scaling replaces failed node

Pros:
  - Cost-effective (only 1 extra node)
  - Handles single node failure
  - Auto-scaling can recover

Cons:
  - Can't handle 2+ simultaneous failures
  - During failure, remaining nodes at 100% capacity

Use for: Stateless services with auto-scaling
```

### 2N Redundancy (Full Duplication)

```
Capacity needed: N nodes
Deployed: 2N nodes (full duplication)

Example:
  - Need 4 nodes
  - Deploy 8 nodes (2 × 4)
  - If entire AZ fails, other AZ handles full load

Pros:
  - Can handle entire AZ/region failure
  - During failure, remaining nodes at 50% capacity
  - Highest availability

Cons:
  - Double cost
  - Over-provisioned during normal operation

Use for: Critical services, multi-AZ/region deployments
```

## Load Balancing

### Load Balancing Algorithms

| Algorithm | Description | Best For |
|-----------|-------------|----------|
| Round Robin | Distribute requests sequentially | Equal capacity nodes, stateless |
| Weighted Round Robin | Distribute based on node weight | Heterogeneous node capacity |
| Least Connections | Send to node with fewest connections | Variable request duration, stateful |
| Least Response Time | Send to node with lowest latency | Latency-sensitive, geographically distributed |
| IP Hash | Hash client IP to determine node | Session affinity, sticky sessions |
| Random | Randomly select node | Simple, uniform distribution |
| Consistent Hashing | Hash key to ring, assign to nearest node | Caching, distributed systems |

### Load Balancer Types

```
Layer 4 (Transport):
  - Operates at TCP/UDP level
  - Fast, simple, no content inspection
  - Example: Nginx stream, HAProxy, AWS NLB

Layer 7 (Application):
  - Operates at HTTP level
  - Content-based routing (path, header, cookie)
  - SSL termination, compression, caching
  - Example: Nginx, HAProxy, AWS ALB, Kong

Global Server Load Balancing (GSLB):
  - DNS-based load balancing across regions
  - Geo-routing, health-based failover
  - Example: AWS Route 53, Cloudflare, F5 GTM
```

```yaml
# ✅ Good - Nginx load balancer configuration
upstream message_service {
    least_conn;  # Least connections algorithm
    server 10.0.1.10:8080 max_fails=3 fail_timeout=30s;
    server 10.0.1.11:8080 max_fails=3 fail_timeout=30s;
    server 10.0.1.12:8080 max_fails=3 fail_timeout=30s;
    keepalive 32;
}

server {
    listen 80;
    location /api/v1/messages/ {
        proxy_pass http://message_service;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_connect_timeout 5s;
        proxy_read_timeout 30s;
        proxy_send_timeout 30s;
    }
}
```

## Health Checks

### Health Check Types

| Type | Interval | Timeout | Failure Threshold | Use For |
|------|----------|---------|-------------------|---------|
| Liveness | 10s | 5s | 3 | Process is alive, restart if dead |
| Readiness | 5s | 3s | 2 | Ready to receive traffic, remove from LB if not |
| Startup | 5s | 10s | 30 | Slow-starting apps, don't kill during startup |

### Health Check Endpoint Design

```java
// ✅ Good - comprehensive health check endpoint
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/liveness")
    public ResponseEntity<Map<String, Object>> liveness() {
        // Simple check: process is alive
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean allUp = true;

        // Check database connectivity
        try {
            dataSource.getConnection().isValid(2);
            checks.put("database", "UP");
        } catch (Exception e) {
            checks.put("database", "DOWN: " + e.getMessage());
            allUp = false;
        }

        // Check Redis connectivity
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            checks.put("redis", "UP");
        } catch (Exception e) {
            checks.put("redis", "DOWN: " + e.getMessage());
            allUp = false;
        }

        // Check message queue connectivity
        try {
            kafkaTemplate.executeInTransaction(t -> true);
            checks.put("kafka", "UP");
        } catch (Exception e) {
            checks.put("kafka", "DOWN: " + e.getMessage());
            allUp = false;
        }

        checks.put("status", allUp ? "UP" : "DOWN");
        checks.put("timestamp", Instant.now().toString());

        return allUp ? ResponseEntity.ok(checks) : ResponseEntity.status(503).body(checks);
    }
}
```

### Kubernetes Health Check Configuration

```yaml
# ✅ Good - Kubernetes health checks
apiVersion: apps/v1
kind: Deployment
metadata:
  name: message-service
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: message-service
        image: cbol/message-service:latest
        ports:
        - containerPort: 8080
        startupProbe:
          httpGet:
            path: /health/liveness
            port: 8080
          periodSeconds: 5
          failureThreshold: 30  # Up to 150s for startup
        livenessProbe:
          httpGet:
            path: /health/liveness
            port: 8080
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /health/readiness
            port: 8080
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 2
        resources:
          requests:
            cpu: "500m"
            memory: "512Mi"
          limits:
            cpu: "2000m"
            memory: "2Gi"
```

## Failover Strategies

### Database Failover

```
MySQL Master-Slave Failover:
  1. Master fails (detected by health check / sentinel)
  2. Promote most up-to-date slave to master
  3. Update application connection string / VIP
  4. Reconfigure other slaves to replicate from new master
  5. RTO = detection (10-30s) + promotion (5-10s) + config update (5-30s)

MongoDB Replica Set Failover:
  1. Primary fails
  2. Secondaries hold election (majority vote)
  3. New primary elected
  4. Drivers automatically discover new primary
  5. RTO = election (~10s)

Redis Sentinel Failover:
  1. Master fails
  2. Sentinels detect (majority agree)
  3. Promote slave to master
  4. Update other slaves and clients
  5. RTO = detection + promotion (~30s)
```

### Service Failover

```
Stateless Service Failover:
  1. Instance fails (health check)
  2. Load balancer removes instance
  3. Traffic redistributed to remaining instances
  4. Auto-scaling starts replacement instance
  5. RTO = health check interval + LB update (~10-30s)

Stateful Service Failover:
  1. Instance fails
  2. Session/state transferred to standby
  3. Client reconnects to new instance
  4. RTO = detection + state transfer + reconnect (~30s-5min)
```

### Multi-AZ / Multi-Region Failover

```
Multi-AZ (Active-Active within region):
  - Deploy across 3 AZs
  - Load balancer distributes across AZs
  - If AZ fails, traffic shifts to other 2 AZs
  - RTO = LB health check (~30s)
  - RPO = 0 (synchronous replication within region)

Multi-Region (Active-Passive):
  - Primary region active, secondary region standby
  - Async replication between regions
  - If primary region fails, failover to secondary
  - RTO = DNS failover + service startup (~5-15min)
  - RPO = replication lag (~1-5min)

Multi-Region (Active-Active):
  - Both regions active, serving local users
  - Global load balancing (GSLB) routes users
  - If region fails, GSLB routes to other region
  - RTO = GSLB health check + DNS TTL (~1-5min)
  - RPO = replication lag (~1-5min), conflict resolution needed
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Single point of failure (SPOF) | One component failure = entire system down | Identify and eliminate all SPOFs |
| No health checks | Can't detect failures, manual intervention | Liveness + readiness + startup probes |
| Health check only checks process | Process alive but can't serve (DB down) | Readiness check includes dependencies |
| No load balancer | Single instance, can't scale/failover | Always use load balancer for stateless services |
| Sticky sessions | Session tied to one instance, can't failover | Stateless sessions (JWT) or distributed session store |
| No auto-scaling | Manual scaling, slow response to load | HPA based on CPU/memory/custom metrics |
| Over-provisioning always | Waste resources, high cost | Auto-scaling + baseline capacity + burst capacity |
| No graceful shutdown | In-flight requests dropped on shutdown | PreStop hook, drain connections, SIGTERM handling |
| No circuit breaker | Cascading failure when dependency fails | Circuit breaker (Resilience4j) with fallback |
| No retry with backoff | Thundering herd on dependency recovery | Exponential backoff + jitter |
| Synchronous cross-region replication | High latency, performance impact | Async replication for cross-region, sync for within-region |
| No chaos engineering | Unknown failure modes, surprises in production | Regular chaos testing (kill pod, kill AZ, network partition) |
| Ignoring graceful degradation | Partial failure = total failure | Degrade gracefully (read-only mode, cached data, reduced features) |
| No runbook for failover | Panic during outage, slow recovery | Documented runbooks, regular failover drills |
| SLA = SLO | No buffer, any SLO breach = SLA breach | SLO stricter than SLA (e.g., SLO 99.95%, SLA 99.9%) |

## References

- Google SRE Book: https://sre.google/sre-book/table-of-contents/
- Google SRE Workbook: https://sre.google/workbook/table-of-contents/
- AWS Well-Architected Framework (Reliability Pillar): https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/welcome.html
- Azure Architecture Center (Reliability): https://learn.microsoft.com/en-us/azure/architecture/framework/resiliency/
- Kubernetes Health Probes: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
- Nginx Load Balancing: https://docs.nginx.com/nginx/admin-guide/load-balancer/http-load-balancer/
- HAProxy: https://www.haproxy.org/
- The Twelve-Factor App: https://12factor.net/
- Release It! (Michael Nygard): https://pragprog.com/titles/mnee2/release-it-second-edition/
- Chaos Engineering (Principles): https://principlesofchaos.org/
