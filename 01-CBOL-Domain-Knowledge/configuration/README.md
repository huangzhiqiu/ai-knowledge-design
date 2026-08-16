# Configuration

> CBOL system configuration. Fill in based on actual application.yml / application.properties.

## Configuration Profiles

| Profile | Environment | Purpose |
|---------|-------------|---------|
| dev | Development | Local development |
| test | Testing | QA / Staging |
| prod | Production | Live |

## Core Configuration Items

### Server
```yaml
server:
  port: 8080
  servlet:
    context-path: /api
```

### WebSocket / Netty
```yaml
netty:
  port: 9000
  boss-threads: 1
  worker-threads: 16
  reader-idle-seconds: 60
  writer-idle-seconds: 0
  all-idle-seconds: 0
```

### Database
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cbol
    username: root
    password: 
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
```

### Redis
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: 
    database: 0
    timeout: 3000ms
    lettuce:
      pool:
        max-active: 16
        max-idle: 8
        min-idle: 2
```

### Message Queue (Kafka / RocketMQ)
```yaml
# Kafka example
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      retries: 3
    consumer:
      group-id: cbol-group
      auto-offset-reset: earliest
```

### Push Notification
```yaml
push:
  apns:
    key-path: 
    key-id: 
    team-id: 
    topic: 
    production: false
  fcm:
    service-account-key: 
  oem:
    xiaomi:
      app-id: 
      app-key: 
    huawei:
      app-id: 
      app-secret: 
```

### IM Business Config
```yaml
im:
  message:
    max-size: 65536
    recall-timeout-seconds: 120
    offline-ttl-days: 30
  session:
    heartbeat-interval-seconds: 30
    connection-timeout-seconds: 90
    max-connections-per-user: 5
  group:
    max-members: 500
    large-group-threshold: 100
```

## Configuration Management

- Config file location: `src/main/resources/application-{profile}.yml`
- Sensitive config: use environment variables or config center (Nacos/Apollo)
- Config center: 

## Key Tuning Parameters

| Parameter | Default | Tuning Note |
|-----------|---------|-------------|
| Netty worker threads | 16 | = CPU cores * 2 |
| Redis pool max-active | 16 | Adjust by QPS |
| Message max size | 64KB | Increase if rich media |
| Heartbeat interval | 30s | Mobile: 60s to save battery |
| Offline TTL | 30 days | Storage cost vs UX |
