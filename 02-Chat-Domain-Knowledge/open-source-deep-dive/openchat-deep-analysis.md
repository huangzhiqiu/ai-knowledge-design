# OpenChat Deep Architecture Analysis

> Source: [open-chat-labs/open-chat](https://github.com/open-chat-labs/open-chat) ⭐ ~0.2k | AGPL-3.0 | Rust + Svelte/TypeScript
> Website: https://oc.app
> Positioning: Decentralized instant messaging fully running on Internet Computer blockchain

---

## 1. Project Overview

OpenChat is a **fully on-chain** instant messaging application, running on the Internet Computer (ICP) blockchain. Unlike traditional centralized chat platforms, OpenChat has no centralized server — all data, logic, and even frontend are provided by canister smart contracts on the blockchain.

### Core Features

| Feature | Description |
|---------|-------------|
| **Fully on-chain** | All messages, groups, user profiles stored on-chain, cannot be deleted or shut down by admins |
| **No operator** | Governed by SNS DAO community, no CEO/board |
| **Passwordless** | Internet Identity (biometrics/security keys), consistent across devices |
| **Built-in crypto payments** | Native support for BTC/ETH/ICP custody and in-chat transfers |
| **End-to-end encryption** | Private conversations encrypted |
| **Verifiable builds** | Deterministic builds, can verify code running on-chain |
| **Bots platform** | SDK provided for building bots |

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Rust (compiled to WebAssembly, running in canisters) |
| Frontend | Svelte + TypeScript (also served by canister) |
| Platform | Internet Computer (ICP) |
| Build tools | DFX 0.31+, Cargo, Vite |
| Governance | SNS DAO |
| Mobile | Kotlin (Android) |

### Repository Structure

```
open-chat/
├── architecture/       # Architecture docs
├── backend/            # Rust canister code (core)
├── frontend/           # Svelte frontend
├── scripts/            # Deployment/build/upgrade scripts
├── sns/                # SNS DAO governance config
├── dfx.json            # DFX project config
├── canister_ids.json   # Canister IDs
├── Cargo.toml          # Rust workspace
├── rust-toolchain.toml # Rust version (1.95.0)
└── upgrade_order.md    # Canister upgrade order
```

---

## 2. Canister Architecture (Core Design)

### 2.1 What is a Canister?

A canister is a compute unit on Internet Computer, encapsulating **code (WebAssembly) + state (stable memory)**, similar to smart contracts but capable of handling HTTP requests, storing large amounts of data, and executing in parallel.

### 2.2 OpenChat Canister Topology

```
┌─────────────────────────────────────────────────────────────┐
│                    Internet Computer (ICP)                   │
│                                                              │
│  ┌──────────────────┐    ┌──────────────────┐               │
│  │  user_index       │    │  group_index      │               │
│  │  (user registry)  │    │  (group registry) │               │
│  │  - create user    │    │  - create group   │               │
│  │    canister       │    │    canister       │               │
│  │  - upgrade user   │    │  - upgrade group  │               │
│  │    canister       │    │    canister       │               │
│  └────────┬─────────┘    └────────┬─────────┘               │
│           │                       │                          │
│     ┌─────┴─────┐           ┌─────┴─────┐                   │
│     ▼           ▼           ▼           ▼                   │
│  ┌──────┐   ┌──────┐    ┌──────┐   ┌──────┐                │
│  │User A│   │User B│    │Group1│   │Group2│   ...           │
│  │canister│ │canister│  │canister│ │canister│               │
│  └──────┘   └──────┘    └──────┘   └──────┘                │
│                                                              │
│  ┌──────────────────┐    ┌──────────────────┐               │
│  │  storage_index    │    │  notifications    │               │
│  │  (media storage   │    │  (push            │               │
│  │   index)          │    │   notifications)  │               │
│  └──────────────────┘    └──────────────────┘               │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 Canister Type Responsibilities

| Canister Type | Responsibility | Count |
|--------------|---------------|-------|
| **user_index** | User registry, create/upgrade user canisters | 1 |
| **user** | Individual user's personal data, conversation list, messages | 1 per user |
| **group_index** | Group registry, create/upgrade group canisters | 1 |
| **group** | Individual group's messages, members, settings | 1 per group |
| **storage_index** | Media file storage index | 1 |
| **storage** | Actual file storage (includes evidence vault compliance feature) | Multiple |
| **notifications** | Push notification service | 1 |
| **OpenChatInstaller** | Installation bootstrap | 1 |

### 2.4 Why One Canister Per User/Group?

| Advantage | Description |
|-----------|-------------|
| **Infinite scaling** | User/group growth just creates new canisters, ICP auto-allocates nodes |
| **Data isolation** | User data entirely in own canister, strong privacy |
| **Parallel execution** | Different canisters run in parallel, no lock contention |
| **Independent upgrade** | Can upgrade user canisters one by one, no global impact |
| **Censorship resistance** | No centralized database that can be deleted wholesale |

---

## 3. Canister Upgrade Mechanism

### 3.1 Upgrade Flow

OpenChat has thousands of user canisters and group canisters, upgrades require careful design:

```
1. Community approves new version via SNS DAO vote
2. New wasm pushed to user_index canister
3. user_index stores wasm in Rust struct (serialized to stable memory on upgrade)
4. user_index upgrades user canisters one by one via heartbeat mechanism
5. group_index similarly upgrades group canisters
6. Each canister upgrade:
   - Data serialized from heap to stable memory
   - Replace wasm code
   - Deserialize data from stable memory back to heap
```

### 3.2 Stable Memory Role

- ICP canister upgrade clears heap memory
- **stable memory** persists across upgrades
- OpenChat serializes critical data to stable memory, ensuring no data loss on upgrade
- `upgrade_order.md` specifies canister upgrade order, avoiding dependency issues

### 3.3 Verifiable Builds

- Use Docker for deterministic builds
- Compare locally built wasm hash with hash exposed on-chain
- Community can verify running code is the audited code

---

## 4. Message Passing Model

### 4.1 Direct Messages (1:1)

```
User A sends message to User B:
1. A's client calls A's user canister's send_message method
2. A's user canister stores message in own history
3. A's user canister notifies B's user canister via inter-canister call
4. B's user canister receives message, stores in own history
5. B's client gets new message via polling/update interface
```

### 4.2 Group Messages

```
User A sends message in group:
1. A's client calls group canister's send_message method
2. group canister verifies permissions, stores message history
3. group canister notifies all members' user canisters (new message)
4. Member clients pull new messages from group canister
```

### 4.3 Comparison with Traditional Architecture

| Dimension | Traditional IM (e.g., Turms) | OpenChat (ICP) |
|-----------|----------------------------|----------------|
| Message storage | Centralized database (MongoDB/MySQL) | Each user/group's canister state |
| Message push | WebSocket long connection | Client polling + canister update |
| Scalability | Sharding, connection pooling | Auto-create new canisters |
| Consistency | Strong (centralized) | Eventual (cross-canister calls) |
| Latency | Very low (intranet) | Higher (blockchain consensus) |

---

## 5. Governance Model (SNS DAO)

### 5.1 What is SNS?

SNS (Service Nervous System) is a DAO framework on ICP, OpenChat is fully controlled by SNS:

| Role | Traditional Company | OpenChat SNS |
|------|--------------------|-------------|
| Decision making | CEO/board | Token holder voting |
| Upgrades | Engineering team releases | Auto-upgrade after community vote approval |
| Funding | Company budget | SNS treasury (community voted use) |
| Ownership | Shareholders | CHAT token holders |

### 5.2 Governance Flow

```
1. Anyone submits proposal (upgrade/parameter change/funding use)
2. CHAT token holders stake and vote
3. After vote passes, SNS auto-executes
4. Canister upgrades execute in predetermined order
```

---

## 6. Security & Privacy

### 6.1 On-Chain Security Features

| Feature | Description |
|---------|-------------|
| **Tamper-proof** | Data on blockchain, cannot be unilaterally modified |
| **DDoS resistant** | ICP network distributed, no single attack target |
| **No server intrusion** | No traditional servers to hack |
| **End-to-end encryption** | Private conversation content encrypted |
| **Internet Identity** | Passwordless, biometrics/security keys, anti-cross-site tracking |

### 6.2 Evidence Vault (Compliance Feature)

OpenChat implements evidence vault in storage canister:
- Captures metadata (report index, chat/message/sender, detection time, classifier category)
- Retention clock (scheduled task expires deletion unless legal hold set)
- Legal holds (prevent deletion)
- Destruction records (request reference log)
- Hash deduplication (all aliases of same file hash processed together)

---

## 7. Design Principles & Trade-offs

| Design Decision | Choice | Trade-off |
|----------------|--------|-----------|
| **Fully on-chain** | All data and logic in canisters | No centralization risk, but higher latency, ongoing cost (cycles) |
| **One canister per user/group** | Fine-grained data isolation | Infinite scaling, but canister management and upgrade complex |
| **SNS DAO governance** | Community self-governance | No single point of control, but slow decisions, upgrades require voting |
| **Rust + Wasm** | Memory safe + verifiable | High development barrier, complex toolchain |
| **Client polling** | Rather than WebSocket long connection | ICP doesn't support long connections, higher latency |
| **AGPL-3.0** | Strong Copyleft | Derivative works must be open source, protects community |

---

## 8. Reference Value for CBOL Project

### 8.1 Architecture Idea Level

| OpenChat Design | CBOL Can Learn |
|----------------|---------------|
| **Per-user independent data space** | User data isolation and privacy protection ideas |
| **Index + entity canister pattern** | Registry + entity instance scaling pattern (similar to registry pattern in microservices) |
| **Rolling upgrade one by one** | Rolling upgrade strategy for large number of instances (heartbeat-driven) |
| **stable memory persistence** | Design pattern for no data loss on upgrade |

### 8.2 Governance Level

| OpenChat Design | CBOL Can Learn |
|----------------|---------------|
| SNS DAO proposal-vote-execute | If community governance needed, can reference its flow |
| Verifiable builds | Supply chain security, ensure running code is audited code |

### 8.3 Security Level

| OpenChat Design | CBOL Can Learn |
|----------------|---------------|
| Internet Identity passwordless | Biometrics/security key authentication method |
| Evidence Vault | Message retention and legal holds for compliance scenarios |
| Hash deduplication storage | File storage optimization |

> **Note**: OpenChat's fully on-chain architecture is not suitable for most enterprise IM scenarios (latency, cost, regulatory compliance). But its ideas like **data isolation, rolling upgrade, verifiable builds** have reference value. If CBOL project is centralized deployment, no need to replicate its blockchain architecture.

---

## 9. References

- GitHub: https://github.com/open-chat-labs/open-chat
- Website: https://oc.app
- ICP ecosystem spotlight: https://internetcomputer.org/ecosystem-spotlight/open-chat/
- Event Store (sub-project): https://github.com/open-chat-labs/event-store
- Bots SDK: https://github.com/open-chat-labs/open-chat-bots
- Internet Computer: https://internetcomputer.org
- SNS docs: https://internetcomputer.org/docs/current/developer-docs/integrations/sns/

---

*Analysis date: 2026-08-18*
