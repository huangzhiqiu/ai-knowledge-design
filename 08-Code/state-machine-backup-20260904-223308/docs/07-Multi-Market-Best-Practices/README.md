# Multi-Market Best Practices

> Detailed design documents for 8 multi-market state machine best practices.
> Last Updated: 2026-09-01

## Overview

This folder contains detailed design documents for implementing multi-market support in the CBOL state machine. Each document covers one best practice with problem statement, design details, implementation roadmap, and risk assessment.

## Document Index

| # | Document | Core Problem | Priority |
|---|----------|-------------|----------|
| 01 | [Three-Layer Config Inheritance](./01-Three-Layer-Config-Inheritance.md) | Config duplication and inconsistency across markets | P0 |
| 02 | [Market Diff Visualization](./02-Market-Diff-Visualization.md) | Invisible differences between markets, unknown change impact | P0 |
| 03 | [Market Routing & Isolation](./03-Market-Routing-Isolation.md) | One market failure cascades to all markets | P1 |
| 04 | [Config as Code & GitOps](./04-Config-as-Code-GitOps.md) | Config changes untraceable, accidental modifications | P1 |
| 05 | [Canary Release Strategy](./05-Canary-Release-Strategy.md) | New config deployment risk, no safe rollout path | P2 |
| 06 | [Circuit Breaker & Degradation](./06-Circuit-Breaker-Degradation.md) | System fault tolerance, graceful degradation under pressure | P2 |
| 07 | [Config Schema Validation](./07-Config-Schema-Validation.md) | Config errors discovered only at runtime | P0 |
| 08 | [Market Test Matrix](./08-Market-Test-Matrix.md) | Combinatorial explosion of market × state × event test cases | P3 |

## Recommended Implementation Order

```
Phase 1 (Immediate, ~1 week):
  ├── 01 Three-Layer Config Inheritance
  ├── 07 Config Schema Validation
  └── 02 Market Diff Visualization

Phase 2 (Short-term, ~2 weeks):
  ├── 03 Market Routing & Isolation
  └── 04 Config as Code & GitOps

Phase 3 (Mid-term, ~2 weeks):
  ├── 05 Canary Release Strategy
  └── 06 Circuit Breaker & Degradation

Phase 4 (Long-term, ~1 week):
  └── 08 Market Test Matrix
```

## Relationship to Other Documents

- [06-Multi-Market-Design.md](../06-Multi-Market-Design.md) — High-level architecture decision (hybrid approach)
- This folder — Detailed implementation designs for each best practice
- [02-CBOL-Business-Layer-Design.md](../02-CBOL-Business-Layer-Design.md) — Current CBOL business layer with basic market config
