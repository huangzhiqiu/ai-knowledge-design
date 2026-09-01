# Demo Package

This package contains runnable demos showcasing the state machine framework's features.

## Demos

| Demo Class | Description | Key Features |
|------------|-------------|--------------|
| `BasicStateMachineDemo` | Core framework usage | Builder DSL, states, transitions, guards, actions, ExtendedState |
| `CbolConversationDemo` | CBOL business layer | Full conversation lifecycle, v6 transfer behavior, survey flow, failover, multi-market |
| `AdvancedFeaturesDemo` | Advanced decorators | Persistence+optimistic lock, idempotency, event sourcing, resilience, failover, composition |
| `EventDrivenDemo` | Event-driven infrastructure | StandardEvent, EventNormalizer, EventDispatcher, interceptors |

## Running

```bash
cd 08-Code/state-machine

# Basic demo
mvn exec:java -Dexec.mainClass="com.selfdevelopment.ai.messaging.demo.BasicStateMachineDemo"

# CBOL conversation demo
mvn exec:java -Dexec.mainClass="com.selfdevelopment.ai.messaging.demo.CbolConversationDemo"

# Advanced features demo
mvn exec:java -Dexec.mainClass="com.selfdevelopment.ai.messaging.demo.AdvancedFeaturesDemo"

# Event-driven demo
mvn exec:java -Dexec.mainClass="com.selfdevelopment.ai.messaging.demo.EventDrivenDemo"
```

## Package Structure

```
demo/
├── BasicStateMachineDemo.java       # Order state machine (CREATED→PAID→SHIPPED→DELIVERED)
├── CbolConversationDemo.java         # CBOL conversation lifecycle (7 states, 18 events)
├── AdvancedFeaturesDemo.java         # 6 advanced feature demos
└── EventDrivenDemo.java              # Event-driven architecture (normalizer + dispatcher)
```
