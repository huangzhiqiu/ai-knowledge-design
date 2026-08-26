# State Machine Deep Analysis

> Source-code-level analysis of the best state machine projects, with architecture diagrams (Mermaid), comparative analysis, and actionable recommendations for CBOL.
>
> **Last updated**: 2026-08-26

## Document Index

| # | Document | Project | Focus |
|---|----------|---------|-------|
| 01 | [COLA StateMachine](./01-cola-statemachine.md) | alibaba/COLA | Stateless minimalist, O(1) lookup, zero-dep |
| 02 | [XState](./02-xstate.md) | statelyai/xstate | SCXML gold standard, statecharts + actor model |
| 03 | [squirrel-foundation](./03-squirrel-foundation.md) | hekailiang/squirrel | Enterprise diagnosable, declarative listeners, UML |
| 04 | [Hypercell FSM](./04-hypercell-fsm.md) | Hypercell-IT-Solutions/fsm-library | Distributed resumable, sub-step checkpoints, retry |
| 05 | [Comparative Analysis](./05-comparative-analysis.md) | All 4 | Architecture/API/performance/fit comparison |
| 06 | [CBOL Recommended Architecture](./06-cbol-recommended-architecture.md) | — | Synthesis: recommended design + roadmap |

## Quick Comparison

```mermaid
quadrantChart
    title State Machine Projects — Capability vs Complexity
    x-axis "Minimal / Lightweight" --> "Full-Featured / Complex"
    y-axis "Low Capability" --> "High Capability"
    quadrant-1 "Advanced Heavyweights"
    quadrant-2 "Powerful but Heavy"
    quadrant-3 "Simple & Light"
    quadrant-4 "Sweet Spot"
    "COLA": [0.15, 0.45]
    "XState": [0.85, 0.95]
    "squirrel": [0.55, 0.70]
    "Hypercell": [0.50, 0.75]
```

## CBOL Fit Scorecard

| Criterion | Weight | COLA | XState | squirrel | Hypercell |
|-----------|--------|------|--------|----------|-----------|
| High concurrency (stateless) | 30% | 10/10 | 5/10 | 5/10 | 6/10 |
| Java ecosystem | 20% | 10/10 | 0/10 | 10/10 | 10/10 |
| Distributed / resumable | 20% | 3/10 | 5/10 | 4/10 | 10/10 |
| Diagnosability | 10% | 4/10 | 8/10 | 10/10 | 8/10 |
| Advanced patterns | 10% | 2/10 | 10/10 | 8/10 | 2/10 |
| Lightweight / zero-dep | 10% | 10/10 | 9/10 | 6/10 | 5/10 |
| **Weighted score** | 100% | **7.3** | **5.9** | **6.5** | **7.0** |

## Recommended Synthesis

```mermaid
flowchart TB
    subgraph Core["Core Engine (P0)"]
        COLA["COLA Pattern<br/>Stateless · O(1) lookup<br/>Fluent builder · Guards · Actions"]
    end

    subgraph Runtime["Runtime Layer (P1)"]
        HYPERCELL["Hypercell Patterns<br/>ConversationInstance · Snapshot<br/>Distributed lock · Load-Execute-Save"]
    end

    subgraph Observability["Observability (P2)"]
        SQUIRREL["squirrel Patterns<br/>Declarative listeners · Audit log<br/>Performance monitor · Extension methods"]
    end

    subgraph Resilience["Resilience (P2)"]
        HYPERCELL2["Hypercell Patterns<br/>Sub-step checkpoints · Resume<br/>Exponential backoff retry · Startup recovery"]
    end

    subgraph Advanced["Advanced Design (P3)"]
        XSTATE["XState Patterns (design only)<br/>Parallel decomposition · Hierarchical grouping<br/>History for resume · Context separation"]
    end

    COLA --> HYPERCELL
    HYPERCELL --> SQUIRREL
    HYPERCELL --> HYPERCELL2
    SQUIRREL --> XSTATE
    HYPERCELL2 --> XSTATE

    style COLA fill:#c8e6c9
    style HYPERCELL fill:#bbdefb
    style HYPERCELL2 fill:#bbdefb
    style SQUIRREL fill:#fff9c4
    style XSTATE fill:#f8bbd0
```

---

*State Machine Deep Analysis — v1.0.0 — 2026-08-26*
